package com.apollosuny.apolledgebe.standingorder.service;

import com.apollosuny.apolledgebe.account.entity.Account;
import com.apollosuny.apolledgebe.account.entity.AccountType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandingOrderServiceTest {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private StandingOrderRepository standingOrderRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionService transactionService;

    @Spy
    private StandingOrderMapper standingOrderMapper = new StandingOrderMapper();

    private StandingOrderService service;

    private final UUID userId = UUID.randomUUID();
    private User user;
    private Account bank;
    private Account rent;

    @BeforeEach
    void setUp() {
        // 2026-09-15 12:00 in Vietnam
        Clock clock = Clock.fixed(Instant.parse("2026-09-15T05:00:00Z"), VIETNAM);
        service = new StandingOrderService(
                standingOrderRepository, accountRepository, userRepository,
                transactionService, standingOrderMapper, clock);
        user = User.builder().id(userId).username("trung").build();
        bank = Account.builder().id(UUID.randomUUID()).user(user).type(AccountType.ASSET).name("Bank").build();
        rent = Account.builder().id(UUID.randomUUID()).user(user).type(AccountType.EXPENSE).name("Rent").build();
    }

    // ---------- create ----------

    @Test
    void create_shouldScheduleThisMonth_whenDayIsStillAhead() {
        stubCreate();

        StandingOrderResponse response = service.create(userId, request(20, null));

        assertThat(response.nextRunOn()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(response.autoPost()).isTrue();
    }

    @Test
    void create_shouldRunToday_whenDayIsToday() {
        stubCreate();

        assertThat(service.create(userId, request(15, null)).nextRunOn()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    void create_shouldScheduleNextMonth_whenDayAlreadyPassed() {
        stubCreate();

        assertThat(service.create(userId, request(5, null)).nextRunOn()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void create_shouldClampDay31ToLastDayOfShortMonth() {
        stubCreate();

        // 31 is still ahead in September (30 days), so it clamps to Sep 30
        assertThat(service.create(userId, request(31, false)).nextRunOn()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void create_shouldHonourAutoPostFalse() {
        stubCreate();

        assertThat(service.create(userId, request(20, false)).autoPost()).isFalse();
    }

    @Test
    void create_shouldRejectSameDebitAndCreditAccount() {
        StandingOrderRequest request = new StandingOrderRequest("Rent", 5_000_000L, bank.getId(), bank.getId(), 5, null);

        assertBusinessError(() -> service.create(userId, request), "STANDING_ORDER_SAME_ACCOUNT", HttpStatus.BAD_REQUEST);
        verify(standingOrderRepository, never()).save(any());
    }

    @Test
    void create_shouldThrowNotFound_whenAccountBelongsToAnotherUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRepository.findByIdAndUser_Id(bank.getId(), userId)).thenReturn(Optional.of(bank));
        when(accountRepository.findByIdAndUser_Id(rent.getId(), userId)).thenReturn(Optional.empty());

        assertBusinessError(() -> service.create(userId, request(5, null)), "ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND);
        verify(standingOrderRepository, never()).save(any());
    }

    @Test
    void create_shouldRejectArchivedAccount() {
        Account archived = Account.builder()
                .id(rent.getId()).user(user).type(AccountType.EXPENSE).name("Rent").archivedAt(Instant.now()).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRepository.findByIdAndUser_Id(rent.getId(), userId)).thenReturn(Optional.of(archived));
        when(accountRepository.findByIdAndUser_Id(bank.getId(), userId)).thenReturn(Optional.of(bank));

        assertBusinessError(() -> service.create(userId, request(5, null)), "ACCOUNT_ARCHIVED", HttpStatus.CONFLICT);
    }

    // ---------- update / pause / resume ----------

    @Test
    void update_shouldRescheduleOnlyWhenDayOfMonthChanges() {
        StandingOrder order = order(LocalDate.of(2026, 9, 20), 20, true);
        stubOwned(order);
        stubAccounts();

        service.update(userId, order.getId(), request(20, true));
        assertThat(order.getNextRunOn()).isEqualTo(LocalDate.of(2026, 9, 20));

        service.update(userId, order.getId(), request(25, true));
        assertThat(order.getNextRunOn()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(order.getDayOfMonth()).isEqualTo((short) 25);
    }

    @Test
    void pause_shouldSetPausedAtOnlyOnce() {
        StandingOrder order = order(LocalDate.of(2026, 9, 20), 20, true);
        stubOwned(order);

        service.pause(userId, order.getId());
        Instant firstPause = order.getPausedAt();
        service.pause(userId, order.getId());

        assertThat(firstPause).isNotNull();
        assertThat(order.getPausedAt()).isEqualTo(firstPause);
    }

    @Test
    void resume_shouldSkipMissedOccurrencesAndScheduleNextMatchingDay() {
        StandingOrder order = order(LocalDate.of(2026, 6, 5), 5, true);
        order.pause(Instant.parse("2026-06-01T00:00:00Z"));
        stubOwned(order);

        service.resume(userId, order.getId());

        assertThat(order.isPaused()).isFalse();
        assertThat(order.getNextRunOn()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    // ---------- postNextOccurrence ----------

    @Test
    void postNextOccurrence_shouldPostAtDueDateStartOfDayAndAdvanceOneMonth() {
        StandingOrder order = order(LocalDate.of(2026, 9, 5), 5, false);
        when(standingOrderRepository.findByIdAndUserIdForUpdate(order.getId(), userId)).thenReturn(Optional.of(order));
        stubTransactionService();

        service.postNextOccurrence(userId, order.getId());

        verify(transactionService).createStandingOrderTransaction(
                eq(userId), eq(order.getId()),
                eq(Instant.parse("2026-09-04T17:00:00Z")),
                eq("Rent"), eq(bank.getId()), eq(rent.getId()), eq(5_000_000L),
                eq("standing-order:" + order.getId() + ":2026-09-05"));
        assertThat(order.getNextRunOn()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void postNextOccurrence_shouldClampAdvanceForShortMonths() {
        StandingOrder order = order(LocalDate.of(2027, 1, 31), 31, false);
        Clock lateJanuary = Clock.fixed(Instant.parse("2027-01-31T05:00:00Z"), VIETNAM);
        StandingOrderService janService = new StandingOrderService(
                standingOrderRepository, accountRepository, userRepository,
                transactionService, standingOrderMapper, lateJanuary);
        when(standingOrderRepository.findByIdAndUserIdForUpdate(order.getId(), userId)).thenReturn(Optional.of(order));
        stubTransactionService();

        janService.postNextOccurrence(userId, order.getId());

        assertThat(order.getNextRunOn()).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void postNextOccurrence_shouldRejectWhenNotDue() {
        StandingOrder order = order(LocalDate.of(2026, 9, 20), 20, false);
        when(standingOrderRepository.findByIdAndUserIdForUpdate(order.getId(), userId)).thenReturn(Optional.of(order));

        assertBusinessError(() -> service.postNextOccurrence(userId, order.getId()),
                "STANDING_ORDER_NOT_DUE", HttpStatus.CONFLICT);
        verify(transactionService, never()).createStandingOrderTransaction(
                any(), any(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void postNextOccurrence_shouldRejectWhenPaused() {
        StandingOrder order = order(LocalDate.of(2026, 9, 5), 5, false);
        order.pause(Instant.now());
        when(standingOrderRepository.findByIdAndUserIdForUpdate(order.getId(), userId)).thenReturn(Optional.of(order));

        assertBusinessError(() -> service.postNextOccurrence(userId, order.getId()),
                "STANDING_ORDER_PAUSED", HttpStatus.CONFLICT);
    }

    @Test
    void postNextOccurrence_shouldThrowNotFound_whenOrderBelongsToAnotherUser() {
        UUID orderId = UUID.randomUUID();
        when(standingOrderRepository.findByIdAndUserIdForUpdate(orderId, userId)).thenReturn(Optional.empty());

        assertBusinessError(() -> service.postNextOccurrence(userId, orderId),
                "STANDING_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    // ---------- postDueOccurrences (scheduler) ----------

    @Test
    void postDueOccurrences_shouldCatchUpEveryMissedMonthWithDistinctKeys() {
        StandingOrder order = order(LocalDate.of(2026, 7, 5), 5, true);
        when(standingOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        stubTransactionService();

        int posted = service.postDueOccurrences(order.getId());

        // Jul 5, Aug 5, Sep 5 are due on Sep 15; Oct 5 is not
        assertThat(posted).isEqualTo(3);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(transactionService, times(3)).createStandingOrderTransaction(
                any(), any(), any(), any(), any(), any(), anyLong(), keys.capture());
        assertThat(keys.getAllValues()).containsExactly(
                "standing-order:" + order.getId() + ":2026-07-05",
                "standing-order:" + order.getId() + ":2026-08-05",
                "standing-order:" + order.getId() + ":2026-09-05");
        assertThat(order.getNextRunOn()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void postDueOccurrences_shouldDoNothing_whenOrderPausedOrManualOrMissing() {
        StandingOrder paused = order(LocalDate.of(2026, 9, 5), 5, true);
        paused.pause(Instant.now());
        StandingOrder manual = order(LocalDate.of(2026, 9, 5), 5, false);
        UUID missing = UUID.randomUUID();
        when(standingOrderRepository.findByIdForUpdate(paused.getId())).thenReturn(Optional.of(paused));
        when(standingOrderRepository.findByIdForUpdate(manual.getId())).thenReturn(Optional.of(manual));
        when(standingOrderRepository.findByIdForUpdate(missing)).thenReturn(Optional.empty());

        assertThat(service.postDueOccurrences(paused.getId())).isZero();
        assertThat(service.postDueOccurrences(manual.getId())).isZero();
        assertThat(service.postDueOccurrences(missing)).isZero();
        verify(transactionService, never()).createStandingOrderTransaction(
                any(), any(), any(), any(), any(), any(), anyLong(), any());
    }

    // ---------- helpers ----------

    private StandingOrderRequest request(int dayOfMonth, Boolean autoPost) {
        return new StandingOrderRequest("Rent", 5_000_000L, bank.getId(), rent.getId(), dayOfMonth, autoPost);
    }

    private StandingOrder order(LocalDate nextRunOn, int dayOfMonth, boolean autoPost) {
        return StandingOrder.builder()
                .id(UUID.randomUUID()).user(user).name("Rent").amountVnd(5_000_000L)
                .debitAccount(bank).creditAccount(rent)
                .dayOfMonth((short) dayOfMonth).nextRunOn(nextRunOn).autoPost(autoPost)
                .build();
    }

    private void stubAccounts() {
        when(accountRepository.findByIdAndUser_Id(bank.getId(), userId)).thenReturn(Optional.of(bank));
        when(accountRepository.findByIdAndUser_Id(rent.getId(), userId)).thenReturn(Optional.of(rent));
    }

    private void stubCreate() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        stubAccounts();
        when(standingOrderRepository.save(any(StandingOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubOwned(StandingOrder order) {
        when(standingOrderRepository.findByIdAndUser_Id(order.getId(), userId)).thenReturn(Optional.of(order));
    }

    private void stubTransactionService() {
        when(transactionService.createStandingOrderTransaction(
                any(), any(), any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(new TransactionResponse(UUID.randomUUID(), null, null, null, null, null, null, null));
    }

    private void assertBusinessError(Runnable call, String code, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo(code);
            assertThat(ex.getStatus()).isEqualTo(status);
        });
    }
}
