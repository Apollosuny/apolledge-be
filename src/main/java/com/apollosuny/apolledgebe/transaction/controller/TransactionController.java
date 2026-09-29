package com.apollosuny.apolledgebe.transaction.controller;

import com.apollosuny.apolledgebe.auth.security.AuthenticatedUser;
import com.apollosuny.apolledgebe.transaction.dto.CreateTransactionRequest;
import com.apollosuny.apolledgebe.transaction.dto.TransactionResponse;
import com.apollosuny.apolledgebe.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public Page<TransactionResponse> getTransactions(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PageableDefault(
                    size = 20,
                    sort = "occurredAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return transactionService.getTransactions(
                currentUser.id(),
                pageable
        );
    }

    @GetMapping("/{transactionId}")
    public TransactionResponse getTransaction(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID transactionId
    ) {
        return transactionService.getTransaction(
                currentUser.id(),
                transactionId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransasction(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        return transactionService.createTransaction(
                currentUser.id(),
                request
        );
    }
}
