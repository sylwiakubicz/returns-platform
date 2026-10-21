package dev.rma.returns.application.port.out;

import java.util.Optional;

import dev.rma.returns.application.model.IdempotencyRecord;

public interface IdempotencyStore {
    Optional<IdempotencyRecord> find(String key, String endpoint);

    /** Zapisuje nowy wynik w aktywnej transakcji operacji; nie nadpisuje istniejących wpisów. */
    void save(String key, String endpoint, IdempotencyRecord record);
}
