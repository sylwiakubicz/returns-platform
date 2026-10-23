package dev.rma.returns.application.model;

import java.time.Instant;
import java.util.UUID;

import dev.rma.returns.domain.events.DomainEvent;

public record HistoryEntry(
        UUID eventId, UUID rmaId, long version, String type, Instant occurredAt, DomainEvent event) {}
