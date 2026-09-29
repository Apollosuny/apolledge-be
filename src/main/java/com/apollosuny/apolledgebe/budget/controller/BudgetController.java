package com.apollosuny.apolledgebe.budget.controller;

import com.apollosuny.apolledgebe.auth.security.AuthenticatedUser;
import com.apollosuny.apolledgebe.budget.dto.BudgetResponse;
import com.apollosuny.apolledgebe.budget.dto.BudgetSummaryResponse;
import com.apollosuny.apolledgebe.budget.dto.SetBudgetRequest;
import com.apollosuny.apolledgebe.budget.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public List<BudgetSummaryResponse> getSummary(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month
    ) {
        return budgetService.getSummary(currentUser.id(), month);
    }

    @PutMapping("/{accountId}/{month}")
    public BudgetResponse setBudget(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID accountId,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @Valid @RequestBody SetBudgetRequest request
    ) {
        return budgetService.setBudget(
                currentUser.id(),
                accountId,
                month,
                request.limitVnd()
        );
    }

    @DeleteMapping("/{accountId}/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBudget(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID accountId,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month
    ) {
        budgetService.deleteBudget(currentUser.id(), accountId, month);
    }
}
