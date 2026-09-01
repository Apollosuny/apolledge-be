package com.apollosuny.apolledgebe.account.controller;

import com.apollosuny.apolledgebe.account.dto.AccountResponse;
import com.apollosuny.apolledgebe.account.dto.CreateAccountRequest;
import com.apollosuny.apolledgebe.account.service.AccountService;
import com.apollosuny.apolledgebe.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${api.prefix}/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public List<AccountResponse> getAccounts(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return accountService.getAccounts(user.id());
    }

    @PostMapping
    public AccountResponse createAccount(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateAccountRequest request
    ) {
        return accountService.createAccount(
                currentUser.id(),
                request
        );
    }
}
