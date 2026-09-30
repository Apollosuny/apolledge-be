package com.apollosuny.apolledgebe.account.service;

import com.apollosuny.apolledgebe.account.dto.AccountBalanceResponse;
import com.apollosuny.apolledgebe.account.entity.Account;
import com.apollosuny.apolledgebe.account.entity.AccountType;
import com.apollosuny.apolledgebe.account.mapper.AccountMapper;
import com.apollosuny.apolledgebe.account.repository.AccountRepository;
import com.apollosuny.apolledgebe.transaction.entity.EntryDirection;
import com.apollosuny.apolledgebe.transaction.repository.LedgerEntryRepository;
import com.apollosuny.apolledgebe.user.repository.UserRepository;
import com.apollosuny.apolledgebe.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountService accountService;

    @Test
    void getBalance_shouldReturnNetDebit_whenAccountIsAsset() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Account account = Account.builder()
                .type(AccountType.ASSET)
                .name("Techcombank")
                .build();

        when(accountRepository.findByIdAndUser_Id(accountId, userId))
                .thenReturn(Optional.of(account));
        when(ledgerEntryRepository.sumNetDebitByAccount(accountId, EntryDirection.DEBIT))
                .thenReturn(9_900_000L);

        AccountBalanceResponse response = accountService.getBalance(userId, accountId, null);

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.balanceVnd()).isEqualTo(9_900_000L);
        assertThat(response.asOf()).isNull();
        verify(ledgerEntryRepository, never()).sumNetDebitByAccountAsOf(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"LIABILITY", "INCOME", "EQUITY"})
    void getBalance_shouldNegateNetDebit_whenAccountHasCreditNormalBalance(AccountType type) {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Account account = Account.builder()
                .type(type)
                .name("Credit-normal account")
                .build();

        when(accountRepository.findByIdAndUser_Id(accountId, userId))
                .thenReturn(Optional.of(account));
        when(ledgerEntryRepository.sumNetDebitByAccount(accountId, EntryDirection.DEBIT))
                .thenReturn(-4_800_000L);

        AccountBalanceResponse response = accountService.getBalance(userId, accountId, null);

        assertThat(response.balanceVnd()).isEqualTo(4_800_000L);
    }

    @Test
    void getBalance_shouldSumOnlyEntriesUpToAsOf_whenAsOfIsGiven() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant asOf = Instant.parse("2026-06-30T16:59:59Z");

        Account account = Account.builder()
                .type(AccountType.LIABILITY)
                .name("Credit card")
                .build();

        when(accountRepository.findByIdAndUser_Id(accountId, userId))
                .thenReturn(Optional.of(account));
        when(ledgerEntryRepository.sumNetDebitByAccountAsOf(accountId, asOf, EntryDirection.DEBIT))
                .thenReturn(-1_200_000L);

        AccountBalanceResponse response = accountService.getBalance(userId, accountId, asOf);

        assertThat(response.balanceVnd()).isEqualTo(1_200_000L);
        assertThat(response.asOf()).isEqualTo(asOf);
        verify(ledgerEntryRepository, never()).sumNetDebitByAccount(any(), any());
    }

    @Test
    void getBalance_shouldThrowNotFound_whenAccountDoesNotBelongToUser() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        when(accountRepository.findByIdAndUser_Id(accountId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getBalance(userId, accountId, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("ACCOUNT_NOT_FOUND");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        verifyNoInteractions(ledgerEntryRepository);
    }

    @Test
    void archiveAccount_shouldSetArchivedAt_whenAccountIsActive() {
        UUID userId = UUID.randomUUID();
        Account account = Account.builder().id(UUID.randomUUID()).type(AccountType.ASSET).name("Cash").build();
        when(accountRepository.findByIdAndUser_Id(account.getId(), userId)).thenReturn(Optional.of(account));

        accountService.archiveAccount(userId, account.getId());

        assertThat(account.getArchivedAt()).isNotNull();
    }

    @Test
    void archiveAccount_shouldKeepOriginalTimestamp_whenAlreadyArchived() {
        UUID userId = UUID.randomUUID();
        Instant archivedAt = Instant.parse("2026-01-01T00:00:00Z");
        Account account = Account.builder()
                .id(UUID.randomUUID()).type(AccountType.ASSET).name("Cash").archivedAt(archivedAt).build();
        when(accountRepository.findByIdAndUser_Id(account.getId(), userId)).thenReturn(Optional.of(account));

        accountService.archiveAccount(userId, account.getId());

        assertThat(account.getArchivedAt()).isEqualTo(archivedAt);
    }

    @Test
    void unarchiveAccount_shouldClearArchivedAt() {
        UUID userId = UUID.randomUUID();
        Account account = Account.builder()
                .id(UUID.randomUUID()).type(AccountType.ASSET).name("Cash").archivedAt(Instant.now()).build();
        when(accountRepository.findByIdAndUser_Id(account.getId(), userId)).thenReturn(Optional.of(account));

        accountService.unarchiveAccount(userId, account.getId());

        assertThat(account.getArchivedAt()).isNull();
    }

    @Test
    void archiveAccount_shouldThrowNotFound_whenAccountBelongsToAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.archiveAccount(userId, accountId))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void getAccounts_shouldExcludeArchivedByDefault() {
        UUID userId = UUID.randomUUID();
        when(accountRepository.findAllByUser_IdAndArchivedAtIsNull(userId)).thenReturn(List.of());

        assertThat(accountService.getAccounts(userId, false)).isEmpty();

        verify(accountRepository, never()).findAllByUser_Id(userId);
    }

    @Test
    void getAccounts_shouldIncludeArchived_whenRequested() {
        UUID userId = UUID.randomUUID();
        when(accountRepository.findAllByUser_Id(userId)).thenReturn(List.of());

        assertThat(accountService.getAccounts(userId, true)).isEmpty();

        verify(accountRepository, never()).findAllByUser_IdAndArchivedAtIsNull(userId);
    }
}
