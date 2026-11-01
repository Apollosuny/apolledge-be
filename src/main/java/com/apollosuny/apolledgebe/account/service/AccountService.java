package com.apollosuny.apolledgebe.account.service;

import com.apollosuny.apolledgebe.account.dto.AccountBalanceResponse;
import com.apollosuny.apolledgebe.account.dto.AccountResponse;
import com.apollosuny.apolledgebe.account.dto.CreateAccountRequest;
import com.apollosuny.apolledgebe.account.entity.Account;
import com.apollosuny.apolledgebe.account.entity.AccountType;
import com.apollosuny.apolledgebe.account.mapper.AccountMapper;
import com.apollosuny.apolledgebe.account.repository.AccountRepository;
import com.apollosuny.apolledgebe.common.exception.BusinessException;
import com.apollosuny.apolledgebe.transaction.entity.EntryDirection;
import com.apollosuny.apolledgebe.transaction.repository.LedgerEntryRepository;
import com.apollosuny.apolledgebe.user.entity.User;
import com.apollosuny.apolledgebe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountMapper accountMapper;

    /** Icons are the design's Material Symbols names; each client maps them to its own glyphs. */
    private record DefaultAccount(AccountType type, String name, String icon) {
    }

    private static final List<DefaultAccount> DEFAULT_ACCOUNTS = List.of(
            new DefaultAccount(AccountType.ASSET, "Tiền mặt", "payments"),
            new DefaultAccount(AccountType.EXPENSE, "Ăn uống", "restaurant"),
            new DefaultAccount(AccountType.EXPENSE, "Đi lại", "directions_car"),
            new DefaultAccount(AccountType.EXPENSE, "Nhà cửa", "home"),
            new DefaultAccount(AccountType.EXPENSE, "Mua sắm", "shopping_bag"),
            new DefaultAccount(AccountType.EXPENSE, "Sức khoẻ", "medication"),
            new DefaultAccount(AccountType.EXPENSE, "Giải trí", "sports_esports"),
            new DefaultAccount(AccountType.EXPENSE, "Khác", "more_horiz"),
            new DefaultAccount(AccountType.INCOME, "Lương", "payments"),
            new DefaultAccount(AccountType.INCOME, "Thu nhập khác", "sell")
    );

    /** Without a wallet and categories a new user has nowhere to post their first transaction. */
    @Transactional
    public void createDefaultAccounts(User user) {
        accountRepository.saveAll(DEFAULT_ACCOUNTS.stream()
                .map(account -> Account.builder()
                        .user(user)
                        .type(account.type())
                        .name(account.name())
                        .icon(account.icon())
                        .build())
                .toList());
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(UUID userId, boolean includeArchived) {
        List<Account> accounts = includeArchived
                ? accountRepository.findAllByUser_Id(userId)
                : accountRepository.findAllByUser_IdAndArchivedAtIsNull(userId);

        return accounts.stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalance(
            UUID userId,
            UUID accountId,
            Instant asOf
    ) {
        Account account = getOwnedAccount(userId, accountId);

        long netDebit = asOf == null
                ? ledgerEntryRepository.sumNetDebitByAccount(accountId, EntryDirection.DEBIT)
                : ledgerEntryRepository.sumNetDebitByAccountAsOf(accountId, asOf, EntryDirection.DEBIT);

        long balance = calculateBalance(account.getType(), netDebit);

        return new AccountBalanceResponse(accountId, balance, asOf);
    }

    @Transactional
    public AccountResponse archiveAccount(UUID userId, UUID accountId) {
        Account account = getOwnedAccount(userId, accountId);

        account.archive(Instant.now());

        return accountMapper.toResponse(account);
    }

    @Transactional
    public AccountResponse unarchiveAccount(UUID userId, UUID accountId) {
        Account account = getOwnedAccount(userId, accountId);

        account.unarchive();

        return accountMapper.toResponse(account);
    }

    @Transactional
    public AccountResponse createAccount(
            UUID userId,
            CreateAccountRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND",
                        "User not found",
                        HttpStatus.NOT_FOUND
                ));

        Account parent = null;

        if (request.parentId() != null) {
            parent = accountRepository.findByIdAndUser_Id(request.parentId(), userId)
                    .orElseThrow(() -> new BusinessException(
                            "PARENT_ACCOUNT_NOT_FOUND",
                            "Parent account not found",
                            HttpStatus.NOT_FOUND
                    ));
        }

        Account.AccountBuilder builder = Account.builder()
                .user(user)
                .type(request.type())
                .name(request.name().trim())
                .icon(request.icon())
                .parent(parent);

        if (request.currency() != null) {
            builder.currency(request.currency());
        }

        Account account = builder.build();

        Account savedAccount = accountRepository.save(account);

        return accountMapper.toResponse(savedAccount);
    }

    private Account getOwnedAccount(UUID userId, UUID accountId) {
        return accountRepository
                .findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new BusinessException(
                        "ACCOUNT_NOT_FOUND",
                        "Account not found",
                        HttpStatus.NOT_FOUND
                ));
    }

    private long calculateBalance(
            AccountType type,
            long netDebit
    ) {
        return switch (type) {
            case ASSET, EXPENSE -> netDebit;

            case LIABILITY, INCOME, EQUITY -> -netDebit;
        };
    }
}
