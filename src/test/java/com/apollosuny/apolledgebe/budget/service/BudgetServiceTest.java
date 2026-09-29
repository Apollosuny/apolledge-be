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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private BudgetService budgetService;

    private final UUID userId = UUID.randomUUID();
    private Account food;
    private Account transport;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-15T05:00:00Z"), VIETNAM);
        budgetService = new BudgetService(budgetRepository, accountRepository, ledgerEntryRepository, clock);
        food = Account.builder().id(UUID.randomUUID()).type(AccountType.EXPENSE).name("Food").icon("restaurant").build();
        transport = Account.builder().id(UUID.randomUUID()).type(AccountType.EXPENSE).name("Transport").build();
    }

    // ---------- setBudget ----------

    @Test
    void setBudget_shouldUpsertWithFirstDayOfMonth() {
        when(accountRepository.findByIdAndUser_Id(food.getId(), userId)).thenReturn(Optional.of(food));

        BudgetResponse response = budgetService.setBudget(userId, food.getId(), YearMonth.of(2026, 9), 3_000_000);

        verify(budgetRepository).upsert(food.getId(), LocalDate.of(2026, 9, 1), 3_000_000);
        assertThat(response.limitVnd()).isEqualTo(3_000_000);
        assertThat(response.month()).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void setBudget_shouldRejectNonExpenseAccount() {
        Account wallet = Account.builder().id(UUID.randomUUID()).type(AccountType.ASSET).name("Cash").build();
        when(accountRepository.findByIdAndUser_Id(wallet.getId(), userId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> budgetService.setBudget(userId, wallet.getId(), YearMonth.of(2026, 9), 1_000))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("BUDGET_REQUIRES_EXPENSE_ACCOUNT");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verify(budgetRepository, never()).upsert(any(), any(), anyLong());
    }

    @Test
    void setBudget_shouldThrowNotFound_whenAccountBelongsToAnotherUser() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.setBudget(userId, accountId, YearMonth.of(2026, 9), 1_000))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_NOT_FOUND"));
        verify(budgetRepository, never()).upsert(any(), any(), anyLong());
    }

    // ---------- deleteBudget ----------

    @Test
    void deleteBudget_shouldDeleteByCompositeId() {
        when(accountRepository.findByIdAndUser_Id(food.getId(), userId)).thenReturn(Optional.of(food));

        budgetService.deleteBudget(userId, food.getId(), YearMonth.of(2026, 9));

        verify(budgetRepository).deleteById(new BudgetId(food.getId(), LocalDate.of(2026, 9, 1)));
    }

    @Test
    void deleteBudget_shouldThrowNotFound_whenAccountBelongsToAnotherUser() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.deleteBudget(userId, accountId, YearMonth.of(2026, 9)))
                .isInstanceOf(BusinessException.class);
        verify(budgetRepository, never()).deleteById(any());
    }

    // ---------- getSummary ----------

    @Test
    void getSummary_shouldMergeSpendAndLimits_includingBudgetsWithoutSpend_sortedBySpendDescending() {
        YearMonth month = YearMonth.of(2026, 9);
        when(budgetRepository.findAllByUserAndMonth(userId, LocalDate.of(2026, 9, 1)))
                .thenReturn(List.of(
                        budget(food, month, 3_000_000L),
                        budget(transport, month, 500_000L)));
        when(ledgerEntryRepository.sumSpentByExpenseAccount(any(), any(), any(), any()))
                .thenReturn(List.of(new AccountSpent(food.getId(), 3_200_000L)));
        when(accountRepository.findAllById(any())).thenReturn(List.of(food, transport));

        List<BudgetSummaryResponse> summary = budgetService.getSummary(userId, month);

        assertThat(summary).extracting(BudgetSummaryResponse::name).containsExactly("Food", "Transport");
        assertThat(summary.get(0).spentVnd()).isEqualTo(3_200_000L);
        assertThat(summary.get(0).remainingVnd()).isEqualTo(-200_000L);
        assertThat(summary.get(1).spentVnd()).isZero();
        assertThat(summary.get(1).remainingVnd()).isEqualTo(500_000L);
    }

    @Test
    void getSummary_shouldLeaveLimitAndRemainingNull_whenSpendHasNoBudget() {
        when(budgetRepository.findAllByUserAndMonth(any(), any())).thenReturn(List.of());
        when(ledgerEntryRepository.sumSpentByExpenseAccount(any(), any(), any(), any()))
                .thenReturn(List.of(new AccountSpent(food.getId(), 45_000L)));
        when(accountRepository.findAllById(any())).thenReturn(List.of(food));

        List<BudgetSummaryResponse> summary = budgetService.getSummary(userId, YearMonth.of(2026, 9));

        assertThat(summary).singleElement().satisfies(row -> {
            assertThat(row.limitVnd()).isNull();
            assertThat(row.remainingVnd()).isNull();
            assertThat(row.spentVnd()).isEqualTo(45_000L);
        });
    }

    @Test
    void getSummary_shouldUseMonthBoundariesInAppTimezone_andDefaultToCurrentMonth() {
        when(budgetRepository.findAllByUserAndMonth(any(), any())).thenReturn(List.of());
        when(ledgerEntryRepository.sumSpentByExpenseAccount(any(), any(), any(), any())).thenReturn(List.of());
        when(accountRepository.findAllById(any())).thenReturn(List.of());

        budgetService.getSummary(userId, null);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(ledgerEntryRepository).sumSpentByExpenseAccount(
                any(), from.capture(), to.capture(), any());
        // 2026-09-01T00:00+07:00 .. 2026-10-01T00:00+07:00
        assertThat(from.getValue()).isEqualTo(Instant.parse("2026-08-31T17:00:00Z"));
        assertThat(to.getValue()).isEqualTo(Instant.parse("2026-09-30T17:00:00Z"));
        verify(budgetRepository).findAllByUserAndMonth(userId, LocalDate.of(2026, 9, 1));
    }

    private Budget budget(Account account, YearMonth month, long limit) {
        return Budget.builder()
                .id(new BudgetId(account.getId(), month.atDay(1)))
                .account(account)
                .limitVnd(limit)
                .build();
    }
}
