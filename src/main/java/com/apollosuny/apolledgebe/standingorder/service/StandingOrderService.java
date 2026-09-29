package com.apollosuny.apolledgebe.standingorder.service;

import com.apollosuny.apolledgebe.account.entity.Account;
import com.apollosuny.apolledgebe.account.repository.AccountRepository;
import com.apollosuny.apolledgebe.common.exception.BusinessException;
import com.apollosuny.apolledgebe.standingorder.dto.StandingOrderRequest;
import com.apollosuny.apolledgebe.standingorder.dto.StandingOrderResponse;
import com.apollosuny.apolledgebe.standingorder.entity.StandingOrder;
import com.apollosuny.apolledgebe.standingorder.mapper.StandingOrderMapper;
import com.apollosuny.apolledgebe.standingorder.repository.StandingOrderRepository;
import com.apollosuny.apolledgebe.transaction.dto.TransactionResponse;
import com.apollosuny.apolledgebe.transaction.service.TransactionService;
import com.apollosuny.apolledgebe.user.entity.User;
import com.apollosuny.apolledgebe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StandingOrderService {

    private final StandingOrderRepository standingOrderRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionService transactionService;
    private final StandingOrderMapper standingOrderMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<StandingOrderResponse> getStandingOrders(UUID userId) {
        return standingOrderRepository.findAllByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(standingOrderMapper::toResponse)
                .toList();
    }

    @Transactional
    public StandingOrderResponse create(UUID userId, StandingOrderRequest request) {
        validateDistinctAccounts(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND",
                        "User not found",
                        HttpStatus.NOT_FOUND
                ));

        Account debitAccount = getUsableAccount(userId, request.debitAccountId());
        Account creditAccount = getUsableAccount(userId, request.creditAccountId());

        StandingOrder order = StandingOrder.builder()
                .user(user)
                .name(request.name().trim())
                .amountVnd(request.amountVnd())
                .debitAccount(debitAccount)
                .creditAccount(creditAccount)
                .dayOfMonth(request.dayOfMonth().shortValue())
                .nextRunOn(firstRunOnOrAfter(today(), request.dayOfMonth()))
                .autoPost(request.autoPost() == null || request.autoPost())
                .build();

        return standingOrderMapper.toResponse(standingOrderRepository.save(order));
    }

    @Transactional
    public StandingOrderResponse update(UUID userId, UUID standingOrderId, StandingOrderRequest request) {
        validateDistinctAccounts(request);

        StandingOrder order = getOwnedOrder(userId, standingOrderId);

        Account debitAccount = getUsableAccount(userId, request.debitAccountId());
        Account creditAccount = getUsableAccount(userId, request.creditAccountId());

        boolean dayChanged = order.getDayOfMonth().intValue() != request.dayOfMonth();

        order.update(
                request.name().trim(),
                request.amountVnd(),
                debitAccount,
                creditAccount,
                request.dayOfMonth().shortValue(),
                request.autoPost() == null || request.autoPost()
        );

        if (dayChanged) {
            order.scheduleNextRunOn(firstRunOnOrAfter(today(), request.dayOfMonth()));
        }

        return standingOrderMapper.toResponse(order);
    }

    @Transactional
    public StandingOrderResponse pause(UUID userId, UUID standingOrderId) {
        StandingOrder order = getOwnedOrder(userId, standingOrderId);

        order.pause(Instant.now(clock));

        return standingOrderMapper.toResponse(order);
    }

    /**
     * Occurrences that fell due while paused are skipped rather than back-filled, so the
     * schedule restarts from the next matching day.
     */
    @Transactional
    public StandingOrderResponse resume(UUID userId, UUID standingOrderId) {
        StandingOrder order = getOwnedOrder(userId, standingOrderId);

        order.resume();
        order.scheduleNextRunOn(firstRunOnOrAfter(today(), order.getDayOfMonth()));

        return standingOrderMapper.toResponse(order);
    }

    /**
     * Confirms the earliest due occurrence. This is how auto_post = false orders are posted;
     * it works for auto orders too, in case the user wants to post before the scheduler runs.
     */
    @Transactional
    public TransactionResponse postNextOccurrence(UUID userId, UUID standingOrderId) {
        StandingOrder order = standingOrderRepository
                .findByIdAndUserIdForUpdate(standingOrderId, userId)
                .orElseThrow(StandingOrderService::notFound);

        if (order.isPaused()) {
            throw new BusinessException(
                    "STANDING_ORDER_PAUSED",
                    "Standing order is paused",
                    HttpStatus.CONFLICT
            );
        }

        if (order.getNextRunOn().isAfter(today())) {
            throw new BusinessException(
                    "STANDING_ORDER_NOT_DUE",
                    "Standing order is not due until " + order.getNextRunOn(),
                    HttpStatus.CONFLICT
            );
        }

        return postOccurrence(order);
    }

    @Transactional(readOnly = true)
    public List<UUID> findDueAutoPostIds() {
        return standingOrderRepository.findDueAutoPostIds(today());
    }

    /**
     * Called by the scheduler, one transaction per order so a failing order cannot block the rest.
     * Re-checks the state under the row lock because it may have changed since the id was listed.
     * Catches up every missed occurrence (e.g. after downtime), each with its own due date.
     */
    @Transactional
    public int postDueOccurrences(UUID standingOrderId) {
        StandingOrder order = standingOrderRepository.findByIdForUpdate(standingOrderId).orElse(null);

        if (order == null || order.isPaused() || !order.isAutoPost()) {
            return 0;
        }

        int posted = 0;
        LocalDate today = today();

        while (!order.getNextRunOn().isAfter(today)) {
            postOccurrence(order);
            posted++;
        }

        return posted;
    }

    private TransactionResponse postOccurrence(StandingOrder order) {
        LocalDate dueDate = order.getNextRunOn();

        TransactionResponse transaction = transactionService.createStandingOrderTransaction(
                order.getUser().getId(),
                order.getId(),
                dueDate.atStartOfDay(clock.getZone()).toInstant(),
                order.getName(),
                order.getDebitAccount().getId(),
                order.getCreditAccount().getId(),
                order.getAmountVnd(),
                "standing-order:" + order.getId() + ":" + dueDate
        );

        order.scheduleNextRunOn(occurrenceIn(YearMonth.from(dueDate).plusMonths(1), order.getDayOfMonth()));

        return transaction;
    }

    private StandingOrder getOwnedOrder(UUID userId, UUID standingOrderId) {
        return standingOrderRepository.findByIdAndUser_Id(standingOrderId, userId)
                .orElseThrow(StandingOrderService::notFound);
    }

    private void validateDistinctAccounts(StandingOrderRequest request) {
        if (request.debitAccountId().equals(request.creditAccountId())) {
            throw new BusinessException(
                    "STANDING_ORDER_SAME_ACCOUNT",
                    "Debit and credit accounts must differ",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private Account getUsableAccount(UUID userId, UUID accountId) {
        Account account = accountRepository.findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new BusinessException(
                        "ACCOUNT_NOT_FOUND",
                        "Account not found",
                        HttpStatus.NOT_FOUND
                ));

        if (account.getArchivedAt() != null) {
            throw new BusinessException(
                    "ACCOUNT_ARCHIVED",
                    "Cannot use an archived account",
                    HttpStatus.CONFLICT
            );
        }

        return account;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDate firstRunOnOrAfter(LocalDate from, int dayOfMonth) {
        LocalDate candidate = occurrenceIn(YearMonth.from(from), dayOfMonth);

        return candidate.isBefore(from)
                ? occurrenceIn(YearMonth.from(from).plusMonths(1), dayOfMonth)
                : candidate;
    }

    /**
     * Day 31 in a 30-day month (or 29/30/31 in February) runs on the last day of that month.
     */
    private LocalDate occurrenceIn(YearMonth month, int dayOfMonth) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    private static BusinessException notFound() {
        return new BusinessException(
                "STANDING_ORDER_NOT_FOUND",
                "Standing order not found",
                HttpStatus.NOT_FOUND
        );
    }
}
