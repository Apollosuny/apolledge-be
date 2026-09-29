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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {
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
                pageable
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
        Transaction transaction = transactionRepository
                .findByIdAndUser_Id(transactionId, userId)
                .orElseThrow(() -> new BusinessException(
                        "TRANSACTION_NOT_FOUND",
                        "Transaction not found",
                        HttpStatus.NOT_FOUND
                ));

        List<LedgerEntry> entries = ledgerEntryRepository.findAllByTransaction_Id(transactionId);

        return transactionMapper.toResponse(transaction, entries);
    }

    @Transactional
    public TransactionResponse createTransaction(
            UUID userId,
            CreateTransactionRequest request
    ) {
        validateBalancedEntries(request.entries());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Map<UUID, Account> accounts = loadAndValidateAccounts(userId, request.entries());

        Transaction transaction = Transaction.builder()
                .user(user)
                .occurredAt(request.occurredAt())
                .note(request.note())
                .receiptUrl(request.receiptUrl())
                .idempotencyKey(request.idempotencyKey())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

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

    private void validateBalancedEntries(
            List<CreateLedgerEntryRequest> entries
    ) {
        long totalDebit = entries.stream()
                .filter(entry -> entry.direction() == EntryDirection.DEBIT)
                .mapToLong(CreateLedgerEntryRequest::amountVnd)
                .sum();

        long totalCredit = entries.stream()
                .filter(entry -> entry.direction() == EntryDirection.CREDIT)
                .mapToLong(CreateLedgerEntryRequest::amountVnd)
                .sum();

        if (totalDebit != totalCredit) {
            throw new BusinessException(
                    "TRANSACTION_NOT_BALANCED",
                    "Transaction is not balanced",
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

        List<Account> accounts = accountRepository.findAllById(accountIds);

        if (accounts.size() != accounts.size()) {
            throw new IllegalArgumentException("One or more accounts do not exits");
        }

        boolean containsForeignAccount = accounts.stream()
                .anyMatch(account -> !account.getUser().getId().equals(userId));

        if (containsForeignAccount) {
            throw new IllegalArgumentException("" +
                    "One or more accounts do not belong to current users");
        }

        return accounts.stream()
                .collect(Collectors.toMap(
                        Account::getId,
                        Function.identity()
                ));
    }
}
