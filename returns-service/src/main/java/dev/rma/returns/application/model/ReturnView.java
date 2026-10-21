package dev.rma.returns.application.model;

import java.time.Instant;

import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.enums.RmaStatus;

/**
 * Wynik utworzenia lub odczytu zwrotu. refundAmount jest null do podjęcia decyzji
 * refundacyjnej; updatedAt to czas ostatniego uwzględnionego zdarzenia.
 */
public record ReturnView(
    String businessNumber,
    RmaStatus status,
    Money refundAmount,
    Instant updatedAt) {}
