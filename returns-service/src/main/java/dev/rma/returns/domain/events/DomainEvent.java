package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.UUID;

public sealed interface DomainEvent
        permits RmaRequested,
                LabelIssued,
                PickupScheduled,
                ParcelInTransit,
                ParcelReceived,
                InspectionStarted,
                InspectionCompleted,
                RefundDecided,
                RefundExecuted,
                RefundFailed,
                RmaExpired,
                RmaLost,
                RmaCancelled,
                RmaClosed {
    UUID rmaId();

    Instant occurredAt();
}
