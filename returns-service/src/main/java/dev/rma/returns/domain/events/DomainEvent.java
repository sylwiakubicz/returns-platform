package dev.rma.returns.domain.events;

public sealed interface DomainEvent
    permits RmaRequested, LabelIssued, PickupScheduled, ParcelInTransit,
    ParcelReceived, InspectionStarted, InspectionCompleted, RefundDecided,
    RefundExecuted, RefundFailed, RmaExpired, RmaLost, RmaCancelled, RmaClosed {
}
