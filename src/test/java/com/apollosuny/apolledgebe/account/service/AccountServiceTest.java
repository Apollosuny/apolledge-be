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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void getBalance_shouldCalculateDebitMinusCredit_whenAccountIsAsset() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Account account = Account.builder()
                .type(AccountType.ASSET)
                .name("Techcombank")
                .build();

        when(accountRepository.findByIdAndUser_Id(accountId, userId))
                .thenReturn(Optional.of(account));

        when(ledgerEntryRepository.sumAmountByAccountAndDirection(
                accountId,
                EntryDirection.DEBIT
        )).thenReturn(10_000_000L);

        when(ledgerEntryRepository.sumAmountByAccountAndDirection(
                accountId,
                EntryDirection.CREDIT
        )).thenReturn(100_000L);

        // Act
        AccountBalanceResponse response =
                accountService.getBalance(userId, accountId);

        // Assert
        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.balanceVnd()).isEqualTo(9_900_000L);
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"LIABILITY", "INCOME", "EQUITY"})
    void getBalance_shouldCalculateCreditMinusDebit_whenAccountHasCreditNormalBalance(AccountType type) {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Account account = Account.builder()
                .type(type)
                .name("Credit-normal account")
                .build();

        when(accountRepository.findByIdAndUser_Id(accountId, userId))
                .thenReturn(Optional.of(account));
        when(ledgerEntryRepository.sumAmountByAccountAndDirection(accountId, EntryDirection.DEBIT))
                .thenReturn(200_000L);
        when(ledgerEntryRepository.sumAmountByAccountAndDirection(accountId, EntryDirection.CREDIT))
                .thenReturn(5_000_000L);

        AccountBalanceResponse response = accountService.getBalance(userId, accountId);

        assertThat(response.balanceVnd()).isEqualTo(4_800_000L);
    }

    @Test
    void getBalance_shouldThrowNotFound_whenAccountDoesNotBelongToUser() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        when(accountRepository.findByIdAndUser_Id(accountId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getBalance(userId, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("ACCOUNT_NOT_FOUND");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        verifyNoInteractions(ledgerEntryRepository);
    }
}