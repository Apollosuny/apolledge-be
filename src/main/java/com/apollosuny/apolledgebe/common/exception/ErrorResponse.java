package com.apollosuny.apolledgebe.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        List<FieldViolation> errors
) {

    public ErrorResponse(String code, String message, Instant timestamp) {
        this(code, message, timestamp, null);
    }

    public record FieldViolation(String field, String message) {
    }
}
