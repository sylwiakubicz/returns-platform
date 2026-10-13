package dev.rma.returns.application.port.out;

import java.time.Instant;
import java.util.UUID;

public record StoredEvent(
        UUID eventId, UUID rmaId, long version, String type, int schemaVersion, String payload, Instant occurredAt) {}
