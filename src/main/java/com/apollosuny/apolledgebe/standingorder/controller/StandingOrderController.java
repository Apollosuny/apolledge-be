package com.apollosuny.apolledgebe.standingorder.controller;

import com.apollosuny.apolledgebe.auth.security.AuthenticatedUser;
import com.apollosuny.apolledgebe.standingorder.dto.StandingOrderRequest;
import com.apollosuny.apolledgebe.standingorder.dto.StandingOrderResponse;
import com.apollosuny.apolledgebe.standingorder.service.StandingOrderService;
import com.apollosuny.apolledgebe.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}/standing-orders")
@RequiredArgsConstructor
public class StandingOrderController {

    private final StandingOrderService standingOrderService;

    @GetMapping
    public List<StandingOrderResponse> getStandingOrders(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return standingOrderService.getStandingOrders(currentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StandingOrderResponse create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody StandingOrderRequest request
    ) {
        return standingOrderService.create(currentUser.id(), request);
    }

    @PutMapping("/{standingOrderId}")
    public StandingOrderResponse update(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID standingOrderId,
            @Valid @RequestBody StandingOrderRequest request
    ) {
        return standingOrderService.update(currentUser.id(), standingOrderId, request);
    }

    @PostMapping("/{standingOrderId}/pause")
    public StandingOrderResponse pause(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID standingOrderId
    ) {
        return standingOrderService.pause(currentUser.id(), standingOrderId);
    }

    @PostMapping("/{standingOrderId}/resume")
    public StandingOrderResponse resume(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID standingOrderId
    ) {
        return standingOrderService.resume(currentUser.id(), standingOrderId);
    }

    @PostMapping("/{standingOrderId}/post")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse postNextOccurrence(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID standingOrderId
    ) {
        return standingOrderService.postNextOccurrence(currentUser.id(), standingOrderId);
    }
}
