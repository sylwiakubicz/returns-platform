package dev.rma.returns.application.model;

import java.time.Instant;
import java.util.Objects;

public record IdempotencyRecord(String requestHash, int responseStatus, String responseBody, Instant createdAt) {
    public IdempotencyRecord {
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        Objects.requireNonNull(responseBody, "responseBody must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
