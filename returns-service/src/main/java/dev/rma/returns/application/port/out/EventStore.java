package dev.rma.returns.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import dev.rma.returns.domain.Rma;
import dev.rma.returns.domain.events.DomainEvent;

public interface EventStore {
    /**
     * Dopisuje całą listę zdarzeń atomowo, w kolejności listy, nadając wersje
     * od expectedVersion + 1. Numeracja jest osobna dla każdego RMA.
     *
     * @param expectedVersion wersja przed wykonaniem polecenia; 0 dla nowego RMA
     * @throws dev.rma.returns.application.exception.ConcurrencyConflictException gdy zapis koliduje
     *     z istniejącą wersją zdarzenia
     */
    void append(UUID rmaId, long expectedVersion, List<DomainEvent> events);

    /**
     * Odtwarza agregat do ostatniej zapisanej wersji.
     * Zwraca Optional.empty(), gdy RMA nie istnieje.
     */
    Optional<Rma> load(UUID rmaId);

    /**
     * Czyta zdarzenia wskazanego RMA od fromVersion włącznie, rosnąco po wersji.
     * Zwraca pusty strumień, gdy brak pasujących zdarzeń.
     * Wywołujący odpowiada za domknięcie strumienia i utrzymanie transakcji
     * przez czas jego konsumowania.
     *
     * @param fromVersion pierwsza uwzględniana wersja, co najmniej 1
     */
    Stream<StoredEvent> readFrom(UUID rmaId, long fromVersion);
}
