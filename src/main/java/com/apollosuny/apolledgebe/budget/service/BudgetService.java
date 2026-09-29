package com.apollosuny.apolledgebe.budget.service;

import com.apollosuny.apolledgebe.account.entity.Account;
import com.apollosuny.apolledgebe.account.entity.AccountType;
import com.apollosuny.apolledgebe.account.repository.AccountRepository;
import com.apollosuny.apolledgebe.budget.dto.BudgetResponse;
import com.apollosuny.apolledgebe.budget.dto.BudgetSummaryResponse;
import com.apollosuny.apolledgebe.budget.entity.Budget;
import com.apollosuny.apolledgebe.budget.entity.BudgetId;
import com.apollosuny.apolledgebe.budget.repository.BudgetRepository;
import com.apollosuny.apolledgebe.common.exception.BusinessException;
import com.apollosuny.apolledgebe.transaction.entity.EntryDirection;
import com.apollosuny.apolledgebe.transaction.repository.AccountSpent;
import com.apollosuny.apolledgebe.transaction.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<BudgetSummaryResponse> getSummary(UUID userId, YearMonth month) {
        YearMonth targetMonth = month != null ? month : YearMonth.now(clock);
        ZoneId zone = clock.getZone();
        Instant from = targetMonth.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = targetMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        Map<UUID, Long> limitByAccount = budgetRepository
                .findAllByUserAndMonth(userId, targetMonth.atDay(1))
                .stream()
                .collect(Collectors.toMap(
                        budget -> budget.getId().getAccountId(),
                        Budget::getLimitVnd
                ));

        Map<UUID, Long> spentByAccount = ledgerEntryRepository
                .sumSpentByExpenseAccount(userId, from, to, EntryDirection.DEBIT)
                .stream()
                .collect(Collectors.toMap(AccountSpent::accountId, AccountSpent::spentVnd));

        // A budget with no spending yet must still show up, so both sources are merged
        Set<UUID> accountIds = new HashSet<>(limitByAccount.keySet());
        accountIds.addAll(spentByAccount.keySet());

        Map<UUID, Account> accounts = accountRepository.findAllById(accountIds)
                .stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        return accountIds.stream()
                .map(accountId -> toSummary(
                        accounts.get(accountId),
                        spentByAccount.getOrDefault(accountId, 0L),
                        limitByAccount.get(accountId)
                ))
                .sorted(Comparator.comparingLong(BudgetSummaryResponse::spentVnd).reversed()
                        .thenComparing(BudgetSummaryResponse::name))
                .toList();
    }

    @Transactional
    public BudgetResponse setBudget(
            UUID userId,
            UUID accountId,
            YearMonth month,
            long limitVnd
    ) {
        Account account = getOwnedAccount(userId, accountId);

        if (account.getType() != AccountType.EXPENSE) {
            throw new BusinessException(
                    "BUDGET_REQUIRES_EXPENSE_ACCOUNT",
                    "Budgets can only be set on expense accounts",
                    HttpStatus.BAD_REQUEST
            );
        }

        budgetRepository.upsert(accountId, month.atDay(1), limitVnd);

        return new BudgetResponse(accountId, month, limitVnd);
    }

    @Transactional
    public void deleteBudget(UUID userId, UUID accountId, YearMonth month) {
        getOwnedAccount(userId, accountId);

        // deleteById is a no-op when the budget does not exist, which keeps DELETE idempotent
        budgetRepository.deleteById(new BudgetId(accountId, month.atDay(1)));
    }

    private Account getOwnedAccount(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new BusinessException(
                        "ACCOUNT_NOT_FOUND",
                        "Account not found",
                        HttpStatus.NOT_FOUND
                ));
    }

    private BudgetSummaryResponse toSummary(Account account, long spentVnd, Long limitVnd) {
        return new BudgetSummaryResponse(
                account.getId(),
                account.getName(),
                account.getIcon(),
                spentVnd,
                limitVnd,
                limitVnd != null ? limitVnd - spentVnd : null
        );
    }
}
