package dev.rma.returns.application.port.out;

import java.time.Instant;
import java.util.Optional;

import dev.rma.returns.application.model.IdempotencyRecord;

public interface IdempotencyStore {
    /** Czeka na wyłączny dostęp do pary klucz–ścieżka do końca aktywnej transakcji. */
    void lock(String key, String endpoint);

    /** Usuwa wpisy starsze niż before; wymaga aktywnej transakcji. */
    int deleteExpiredBefore(Instant before);

    /** Usuwa wygasły wpis dla jednej pary klucz–ścieżka; wywołuj po lock(). */
    int deleteExpired(String key, String endpoint, Instant before);

    Optional<IdempotencyRecord> find(String key, String endpoint);

    /** Zapisuje nowy wynik w aktywnej transakcji operacji; nie nadpisuje istniejących wpisów. */
    void save(String key, String endpoint, IdempotencyRecord record);
}
