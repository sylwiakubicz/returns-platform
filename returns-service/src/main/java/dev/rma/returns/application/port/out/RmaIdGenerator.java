package dev.rma.returns.application.port.out;

import java.util.UUID;

/** Nadaje techniczne identyfikatory agregatów w formacie UUIDv7. */
public interface RmaIdGenerator {
    UUID nextId();
}
