package com.apollosuny.apolledgebe.transaction.repository;

import java.util.UUID;

public record AccountSpent(
        UUID accountId,
        Long spentVnd
) {
}
