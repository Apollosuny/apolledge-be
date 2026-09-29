package com.apollosuny.apolledgebe.transaction.service;

import com.apollosuny.apolledgebe.account.entity.Account;
import com.apollosuny.apolledgebe.account.repository.AccountRepository;
import com.apollosuny.apolledgebe.common.exception.BusinessException;
import com.apollosuny.apolledgebe.transaction.dto.CreateLedgerEntryRequest;
import com.apollosuny.apolledgebe.transaction.dto.CreateTransactionRequest;
import com.apollosuny.apolledgebe.transaction.dto.TransactionResponse;
import com.apollosuny.apolledgebe.transaction.entity.EntryDirection;
import com.apollosuny.apolledgebe.transaction.entity.LedgerEntry;
import com.apollosuny.apolledgebe.transaction.entity.Transaction;
import com.apollosuny.apolledgebe.transaction.mapper.TransactionMapper;
import com.apollosuny.apolledgebe.transaction.repository.LedgerEntryRepository;
import com.apollosuny.apolledgebe.transaction.repository.TransactionRepository;
import com.apollosuny.apolledgebe.user.entity.User;
import com.apollosuny.apolledgebe.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private TransactionMapper transactionMapper = new TransactionMapper();

    @InjectMocks
    private TransactionService transactionService;

    private final UUID userId = UUID.randomUUID();
    private final Instant occurredAt = Instant.parse("2026-09-29T10:00:00Z");

    private User user;
    private Account cash;
    private Account food;

    @BeforeEach
    void setUp() {
        user = User.builder().id(userId).username("trung").build();
        cash = Account.builder().id(UUID.randomUUID()).user(user).name("Cash").build();
        food = Account.builder().id(UUID.randomUUID()).user(user).name("Food").build();
    }

    // ---------- createTransaction ----------

    @Test
    void createTransaction_shouldPersistTransactionAndEntries_whenBalanced() {
        CreateTransactionRequest request = request(null,
                entry(food, EntryDirection.DEBIT, 50_000),
                entry(cash, EntryDirection.CREDIT, 50_000));
        stubUserAndAccounts(cash, food);
        stubSaveTransaction();
        when(ledgerEntryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.createTransaction(userId, request);

        assertThat(response.entries()).hasSize(2);
        assertThat(response.entries())
                .extracting(e -> e.direction() + ":" + e.amountVnd())
                .containsExactlyInAnyOrder("DEBIT:50000", "CREDIT:50000");
    }

    @Test
    void createTransaction_shouldThrowNotBalanced_whenDebitDiffersFromCredit() {
        CreateTransactionRequest request = request(null,
                entry(food, EntryDirection.DEBIT, 50_000),
                entry(cash, EntryDirection.CREDIT, 40_000));

        assertBusinessError(request, "TRANSACTION_NOT_BALANCED", HttpStatus.BAD_REQUEST);
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTransaction_shouldRejectOverflowingAmounts_insteadOfWrappingAroundToBalanced() {
        // debit 5 vs credit (MAX + MAX + 7) which wraps to 5 with a plain long sum
        CreateTransactionRequest request = request(null,
                entry(food, EntryDirection.DEBIT, 5),
                entry(cash, EntryDirection.CREDIT, Long.MAX_VALUE),
                entry(cash, EntryDirection.CREDIT, Long.MAX_VALUE),
                entry(cash, EntryDirection.CREDIT, 7));

        assertBusinessError(request, "AMOUNT_OUT_OF_RANGE", HttpStatus.BAD_REQUEST);
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTransaction_shouldThrowNotFound_whenAccountMissingOrOwnedBySomeoneElse() {
        CreateTransactionRequest request = request(null,
                entry(food, EntryDirection.DEBIT, 10_000),
                entry(cash, EntryDirection.CREDIT, 10_000));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        // owner-scoped query only returns the account that belongs to the user
        when(accountRepository.findAllByIdInAndUser_Id(Set.of(food.getId(), cash.getId()), userId))
                .thenReturn(List.of(cash));

        assertBusinessError(request, "ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND);
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTransaction_shouldThrowConflict_whenAccountIsArchived() {
        Account archived = Account.builder()
                .id(UUID.randomUUID()).user(user).name("Old").archivedAt(Instant.now()).build();
        CreateTransactionRequest request = request(null,
                entry(food, EntryDirection.DEBIT, 10_000),
                entry(archived, EntryDirection.CREDIT, 10_000));
        stubUserAndAccounts(food, archived);

        assertBusinessError(request, "ACCOUNT_ARCHIVED", HttpStatus.CONFLICT);
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTransaction_shouldReturnExistingTransaction_whenIdempotencyKeyWasAlreadyUsed() {
        Transaction existing = Transaction.builder()
                .id(UUID.randomUUID()).user(user).occurredAt(occurredAt).idempotencyKey("key-1").build();
        LedgerEntry existingEntry = LedgerEntry.builder()
                .id(UUID.randomUUID()).transaction(existing).account(cash)
                .direction(EntryDirection.CREDIT).amountVnd(10_000L).occurredAt(occurredAt).build();
        when(transactionRepository.findByUser_IdAndIdempotencyKey(userId, "key-1"))
                .thenReturn(Optional.of(existing));
        when(ledgerEntryRepository.findAllByTransaction_Id(existing.getId()))
                .thenReturn(List.of(existingEntry));

        TransactionResponse response = transactionService.createTransaction(userId, request("key-1",
                entry(food, EntryDirection.DEBIT, 10_000),
                entry(cash, EntryDirection.CREDIT, 10_000)));

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(transactionRepository, never()).saveAndFlush(any());
        verifyNoInteractions(accountRepository, userRepository);
    }

    @Test
    void createTransaction_shouldStoreNullKey_whenIdempotencyKeyIsBlank() {
        CreateTransactionRequest request = request("   ",
                entry(food, EntryDirection.DEBIT, 10_000),
                entry(cash, EntryDirection.CREDIT, 10_000));
        stubUserAndAccounts(cash, food);
        stubSaveTransaction();
        when(ledgerEntryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.createTransaction(userId, request);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isNull();
        verify(transactionRepository, never()).findByUser_IdAndIdempotencyKey(any(), any());
    }

    @Test
    void createTransaction_shouldThrowConflict_whenConcurrentRequestWinsTheUniqueConstraint() {
        CreateTransactionRequest request = request("key-2",
                entry(food, EntryDirection.DEBIT, 10_000),
                entry(cash, EntryDirection.CREDIT, 10_000));
        when(transactionRepository.findByUser_IdAndIdempotencyKey(userId, "key-2"))
                .thenReturn(Optional.empty());
        stubUserAndAccounts(cash, food);
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("uq_transactions_user_idempotency"));

        assertBusinessError(request, "IDEMPOTENCY_KEY_CONFLICT", HttpStatus.CONFLICT);
        verify(ledgerEntryRepository, never()).saveAll(anyList());
    }

    // ---------- getTransaction ----------

    @Test
    void getTransaction_shouldThrowNotFound_whenTransactionBelongsToAnotherUser() {
        UUID transactionId = UUID.randomUUID();
        when(transactionRepository.findByIdAndUser_Id(transactionId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(userId, transactionId))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("TRANSACTION_NOT_FOUND");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void getTransaction_shouldReturnTransactionWithEntries() {
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID()).user(user).occurredAt(occurredAt).build();
        LedgerEntry entry = LedgerEntry.builder()
                .id(UUID.randomUUID()).transaction(transaction).account(cash)
                .direction(EntryDirection.DEBIT).amountVnd(1_000L).occurredAt(occurredAt).build();
        when(transactionRepository.findByIdAndUser_Id(transaction.getId(), userId))
                .thenReturn(Optional.of(transaction));
        when(ledgerEntryRepository.findAllByTransaction_Id(transaction.getId()))
                .thenReturn(List.of(entry));

        TransactionResponse response = transactionService.getTransaction(userId, transaction.getId());

        assertThat(response.id()).isEqualTo(transaction.getId());
        assertThat(response.entries()).singleElement()
                .satisfies(e -> assertThat(e.accountId()).isEqualTo(cash.getId()));
    }

    // ---------- getTransactions ----------

    @Test
    void getTransactions_shouldRejectSortByNonWhitelistedProperty() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("user.password"));

        assertThatThrownBy(() -> transactionService.getTransactions(userId, pageable))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("INVALID_SORT");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getTransactions_shouldAddIdTieBreakerToSort() {
        Pageable pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "occurredAt"));
        when(transactionRepository.findAllByUser_Id(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<TransactionResponse> page = transactionService.getTransactions(userId, pageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAllByUser_Id(any(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort()).containsExactly(
                Sort.Order.desc("occurredAt"),
                Sort.Order.asc("id"));
        assertThat(page.getContent()).isEmpty();
    }

    // ---------- helpers ----------

    private CreateLedgerEntryRequest entry(Account account, EntryDirection direction, long amount) {
        return new CreateLedgerEntryRequest(account.getId(), direction, amount);
    }

    private CreateTransactionRequest request(String idempotencyKey, CreateLedgerEntryRequest... entries) {
        return new CreateTransactionRequest(occurredAt, "note", null, idempotencyKey, List.of(entries));
    }

    private void stubUserAndAccounts(Account... accounts) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRepository.findAllByIdInAndUser_Id(any(), any()))
                .thenReturn(List.of(accounts));
    }

    private void stubSaveTransaction() {
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction t = invocation.getArgument(0);
                    return Transaction.builder()
                            .id(UUID.randomUUID()).user(t.getUser()).occurredAt(t.getOccurredAt())
                            .note(t.getNote()).idempotencyKey(t.getIdempotencyKey()).build();
                });
    }

    private void assertBusinessError(
            CreateTransactionRequest request,
            String expectedCode,
            HttpStatus expectedStatus
    ) {
        assertThatThrownBy(() -> transactionService.createTransaction(userId, request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(expectedCode);
                    assertThat(ex.getStatus()).isEqualTo(expectedStatus);
                });
    }
}
