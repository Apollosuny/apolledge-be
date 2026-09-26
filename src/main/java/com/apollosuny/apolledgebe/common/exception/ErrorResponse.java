package com.apollosuny.apolledgebe.common.exception;

import java.time.Instant;

public record ErrorResponse(
        String code,
        String message,
        Instant timestamp
) {
}
