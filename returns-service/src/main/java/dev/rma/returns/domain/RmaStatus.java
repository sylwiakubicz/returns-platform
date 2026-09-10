package dev.rma.returns.domain;

public enum RmaStatus {
    REQUESTED,
    LABEL_ISSUED,
    PICKUP_SCHEDULED,
    IN_TRANSIT,
    RECEIVED,
    UNDER_INSPECTION,
    REFUND_PENDING,
    REFUND_FAILED,
    REFUNDED,
    REJECTED,
    EXPIRED,
    LOST,
    CANCELLED,
    CLOSED
}
