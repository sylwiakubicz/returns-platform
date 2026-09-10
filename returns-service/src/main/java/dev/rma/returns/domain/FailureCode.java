package dev.rma.returns.domain;

public enum FailureCode {
    TIMEOUT,
    PROVIDER_UNAVAILABLE,
    RATE_LIMITED,
    INSUFFICIENT_FUNDS,
    INVALID_ACCOUNT,
    REJECTED_BY_PROVIDER
}
