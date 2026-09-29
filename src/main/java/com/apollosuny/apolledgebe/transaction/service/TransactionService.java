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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final Set<String> SORTABLE_PROPERTIES = Set.of("occurredAt", "postedAt");

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionMapper transactionMapper;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
            UUID userId,
            Pageable pageable
    ) {
        Page<Transaction> transactions = transactionRepository.findAllByUser_Id(
                userId,
                withSafeSort(pageable)
        );

        if (transactions.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> transactionIds = transactions.stream()
                .map(Transaction::getId)
                .toList();

        List<LedgerEntry> entries = ledgerEntryRepository.findAllByTransaction_IdIn(transactionIds);

        Map<UUID, List<LedgerEntry>> entriesByTransactionId = entries.stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getTransaction().getId()
                ));

        return transactions.map(transaction -> {
            return transactionMapper.toResponse(
                    transaction,
                    entriesByTransactionId.getOrDefault(
                            transaction.getId(),
                            List.of()
                    )
            );
        });
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(
            UUID userId,
            UUID transactionId
    ) {
        return toResponse(getOwnedTransaction(userId, transactionId));
    }

    /**
     * The ledger is append-only: a mistake is corrected by posting a mirror transaction
     * (same amounts, opposite directions) instead of editing or deleting history.
     * The reversal keeps the original occurredAt so period reports net to zero;
     * postedAt records when the correction was made.
     */
    @Transactional
    public TransactionResponse reverseTransaction(
            UUID userId,
            UUID transactionId
    ) {
        Transaction original = getOwnedTransaction(userId, transactionId);

        if (original.getReverses() != null) {
            throw new BusinessException(
                    "CANNOT_REVERSE_A_REVERSAL",
                    "A reversal transaction cannot be reversed",
                    HttpStatus.CONFLICT
            );
        }

        if (transactionRepository.existsByReverses_Id(transactionId)) {
            throw transactionAlreadyReversed();
        }

        Transaction reversal = Transaction.builder()
                .user(original.getUser())
                .occurredAt(original.getOccurredAt())
                .note("Reversal of " + transactionId)
                .reverses(original)
                .build();

        Transaction savedReversal;
        try {
            savedReversal = transactionRepository.saveAndFlush(reversal);
        } catch (DataIntegrityViolationException exception) {
            // uq_transactions_reverses_id: a concurrent request reversed it first
            throw transactionAlreadyReversed();
        }

        List<LedgerEntry> mirroredEntries = ledgerEntryRepository
                .findAllByTransaction_Id(transactionId)
                .stream()
                .map(entry -> LedgerEntry.builder()
                        .transaction(savedReversal)
                        .account(entry.getAccount())
                        .direction(opposite(entry.getDirection()))
                        .amountVnd(entry.getAmountVnd())
                        .occurredAt(savedReversal.getOccurredAt())
                        .build())
                .toList();

        return transactionMapper.toResponse(
                savedReversal,
                ledgerEntryRepository.saveAll(mirroredEntries)
        );
    }

    @Transactional
    public TransactionResponse createTransaction(
            UUID userId,
            CreateTransactionRequest request
    ) {
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());

        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepository
                    .findByUser_IdAndIdempotencyKey(userId, idempotencyKey);

            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        validateBalancedEntries(request.entries());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND",
                        "User not found",
                        HttpStatus.NOT_FOUND
                ));

        Map<UUID, Account> accounts = loadAndValidateAccounts(userId, request.entries());

        Transaction transaction = Transaction.builder()
                .user(user)
                .occurredAt(request.occurredAt())
                .note(request.note())
                .receiptUrl(request.receiptUrl())
                .idempotencyKey(idempotencyKey)
                .build();

        Transaction savedTransaction = saveTransaction(transaction, idempotencyKey);

        List<LedgerEntry> entries = request.entries()
                .stream()
                .map(entry ->
                        LedgerEntry.builder()
                                .transaction(savedTransaction)
                                .account(accounts.get(entry.accountId()))
                                .direction(entry.direction())
                                .amountVnd(entry.amountVnd())
                                .occurredAt(request.occurredAt())
                                .build()
                )
                .toList();

        List<LedgerEntry> savedEntries = ledgerEntryRepository.saveAll(entries);

        return transactionMapper.toResponse(savedTransaction, savedEntries);
    }

    private Transaction getOwnedTransaction(UUID userId, UUID transactionId) {
        return transactionRepository
                .findByIdAndUser_Id(transactionId, userId)
                .orElseThrow(() -> new BusinessException(
                        "TRANSACTION_NOT_FOUND",
                        "Transaction not found",
                        HttpStatus.NOT_FOUND
                ));
    }

    private EntryDirection opposite(EntryDirection direction) {
        return direction == EntryDirection.DEBIT ? EntryDirection.CREDIT : EntryDirection.DEBIT;
    }

    private BusinessException transactionAlreadyReversed() {
        return new BusinessException(
                "TRANSACTION_ALREADY_REVERSED",
                "Transaction has already been reversed",
                HttpStatus.CONFLICT
        );
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return transactionMapper.toResponse(
                transaction,
                ledgerEntryRepository.findAllByTransaction_Id(transaction.getId())
        );
    }

    private Pageable withSafeSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new BusinessException(
                        "INVALID_SORT",
                        "Sorting by '" + order.getProperty() + "' is not supported",
                        HttpStatus.BAD_REQUEST
                );
            }
        }

        // id is a tie-breaker so rows sharing the same timestamp keep a stable order across pages
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().and(Sort.by("id"))
        );
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    /**
     * Two concurrent requests with the same key can both pass the lookup in createTransaction;
     * the unique constraint (user_id, idempotency_key) is the real guard, so the loser fails here.
     */
    private Transaction saveTransaction(Transaction transaction, String idempotencyKey) {
        try {
            return transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException exception) {
            if (idempotencyKey == null) {
                throw exception;
            }
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_CONFLICT",
                    "A request with this idempotency key is already being processed",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateBalancedEntries(
            List<CreateLedgerEntryRequest> entries
    ) {
        long totalDebit = sumAmount(entries, EntryDirection.DEBIT);
        long totalCredit = sumAmount(entries, EntryDirection.CREDIT);

        if (totalDebit != totalCredit) {
            throw new BusinessException(
                    "TRANSACTION_NOT_BALANCED",
                    "Transaction is not balanced",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * A plain long sum wraps around silently, which would let crafted amounts look balanced.
     */
    private long sumAmount(
            List<CreateLedgerEntryRequest> entries,
            EntryDirection direction
    ) {
        try {
            return entries.stream()
                    .filter(entry -> entry.direction() == direction)
                    .mapToLong(CreateLedgerEntryRequest::amountVnd)
                    .reduce(0L, Math::addExact);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                    "AMOUNT_OUT_OF_RANGE",
                    "Total amount is too large",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private Map<UUID, Account> loadAndValidateAccounts(
            UUID userId,
            List<CreateLedgerEntryRequest> entries
    ) {
        Set<UUID> accountIds = entries.stream()
                .map(CreateLedgerEntryRequest::accountId)
                .collect(Collectors.toSet());

        // Filtering by owner in the query makes "missing" and "someone else's" indistinguishable
        List<Account> accounts = accountRepository.findAllByIdInAndUser_Id(accountIds, userId);

        if (accounts.size() != accountIds.size()) {
            throw new BusinessException(
                    "ACCOUNT_NOT_FOUND",
                    "One or more accounts not found",
                    HttpStatus.NOT_FOUND
            );
        }

        boolean containsArchivedAccount = accounts.stream()
                .anyMatch(account -> account.getArchivedAt() != null);

        if (containsArchivedAccount) {
            throw new BusinessException(
                    "ACCOUNT_ARCHIVED",
                    "Cannot post to an archived account",
                    HttpStatus.CONFLICT
            );
        }

        return accounts.stream()
                .collect(Collectors.toMap(
                        Account::getId,
                        Function.identity()
                ));
    }
}
