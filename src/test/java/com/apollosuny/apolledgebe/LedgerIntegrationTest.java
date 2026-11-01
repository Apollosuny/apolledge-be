package com.apollosuny.apolledgebe;

import com.apollosuny.apolledgebe.account.dto.AccountResponse;
import com.apollosuny.apolledgebe.account.entity.AccountType;
import com.apollosuny.apolledgebe.account.service.AccountService;
import com.apollosuny.apolledgebe.auth.dto.RegisterRequest;
import com.apollosuny.apolledgebe.auth.service.AuthService;
import com.apollosuny.apolledgebe.transaction.dto.CreateLedgerEntryRequest;
import com.apollosuny.apolledgebe.transaction.dto.CreateTransactionRequest;
import com.apollosuny.apolledgebe.transaction.dto.TransactionResponse;
import com.apollosuny.apolledgebe.transaction.entity.EntryDirection;
import com.apollosuny.apolledgebe.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers what mocks cannot: Flyway on real Postgres, the ledger triggers, and the JPQL behind the ledger screen.
 */
@Testcontainers
@SpringBootTest(properties = {
        "jwt.access-secret=integration-test-access-secret-0123456789",
        "jwt.refresh-secret=integration-test-refresh-secret-0123456789"
})
class LedgerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private AuthService authService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID cashId;
    private UUID expenseId;

    @BeforeEach
    void registerUser() {
        String username = "user-" + UUID.randomUUID();
        userId = authService.register(new RegisterRequest(username, username + "@example.test", "password-123"))
                .user().id();
        List<AccountResponse> accounts = accountService.getAccounts(userId, false);
        cashId = accounts.stream().filter(a -> a.type() == AccountType.ASSET).findFirst().orElseThrow().id();
        expenseId = accounts.stream().filter(a -> a.type() == AccountType.EXPENSE).findFirst().orElseThrow().id();
    }

    private TransactionResponse spend(Instant occurredAt) {
        return transactionService.createTransaction(userId, new CreateTransactionRequest(
                occurredAt, null, null, UUID.randomUUID().toString(), List.of(
                        new CreateLedgerEntryRequest(expenseId, EntryDirection.DEBIT, 50_000),
                        new CreateLedgerEntryRequest(cashId, EntryDirection.CREDIT, 50_000)
                )));
    }

    private List<UUID> idsInPeriod(Instant from, Instant to, boolean activeOnly) {
        return transactionService.getTransactions(userId, from, to, activeOnly, PageRequest.of(0, 20))
                .map(TransactionResponse::id)
                .getContent();
    }

    @Test
    void registerSeedsDefaultAccounts() {
        assertThat(accountService.getAccounts(userId, false))
                .hasSize(10)
                .extracting(AccountResponse::type)
                .containsOnly(AccountType.ASSET, AccountType.EXPENSE, AccountType.INCOME);
    }

    @Test
    void periodIsHalfOpenSoAMonthBoundaryBelongsToOneMonth() {
        Instant boundary = Instant.parse("2026-07-31T17:00:00Z");
        UUID atBoundary = spend(boundary).id();

        assertThat(idsInPeriod(boundary.minusSeconds(3600), boundary, false)).isEmpty();
        assertThat(idsInPeriod(boundary, boundary.plusSeconds(3600), false)).containsExactly(atBoundary);
    }

    @Test
    void activeOnlyHidesReversedTransactionsAndTheirReversals() {
        Instant at = Instant.parse("2026-08-10T03:00:00Z");
        UUID reversed = spend(at).id();
        UUID kept = spend(at).id();
        transactionService.reverseTransaction(userId, reversed);

        assertThat(idsInPeriod(at, at.plusSeconds(1), true)).containsExactly(kept);
        assertThat(idsInPeriod(at.minusSeconds(1), Instant.now().plusSeconds(60), false)).hasSize(3);
    }

    @Test
    void ledgerEntriesAreAppendOnly() {
        UUID transactionId = spend(Instant.parse("2026-08-10T03:00:00Z")).id();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update ledger_entries set amount_vnd = 1 where transaction_id = ?", transactionId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from ledger_entries where transaction_id = ?", transactionId))
                .hasMessageContaining("append-only");
    }
}
