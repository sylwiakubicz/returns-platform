package dev.rma.returns.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.rma.returns.application.exception.ReturnNotFoundException;
import dev.rma.returns.application.port.out.EventStore;
import dev.rma.returns.application.port.out.RmaIdLookup;
import dev.rma.returns.domain.Rma;
import dev.rma.returns.domain.events.DomainEvent;

/** Tymczasowe operacje zastępujące integracje wysyłkowe do etapu 6 projektu. */
@Service
public class ShipmentSimulationService {
    private final EventStore eventStore;
    private final RmaIdLookup idLookup;
    private final Clock clock;

    public ShipmentSimulationService(EventStore eventStore, RmaIdLookup idLookup, Clock clock) {
        this.eventStore = eventStore;
        this.idLookup = idLookup;
        this.clock = clock;
    }

    @Transactional
    public void issueLabel(String number, String labelId, String trackingNumber, String labelFileKey) {
        execute(number, rma -> rma.issueLabel(labelId, trackingNumber, labelFileKey, clock.instant()));
    }

    @Transactional
    public void schedulePickup(String number, String pickupId, LocalDate scheduledFor) {
        execute(number, rma -> rma.schedulePickup(pickupId, scheduledFor, clock.instant()));
    }

    @Transactional
    public void markInTransit(String number) {
        execute(number, rma -> rma.markParcelInTransit(clock.instant()));
    }

    @Transactional
    public void receiveParcel(String number, String warehouseId) {
        execute(number, rma -> rma.receive(clock.instant(), warehouseId));
    }

    private void execute(String number, Function<Rma, DomainEvent> command) {
        var id = idLookup.findId(number).orElseThrow(() -> new ReturnNotFoundException(number));
        var rma = eventStore.load(id).orElseThrow(() -> new ReturnNotFoundException(number));
        long expectedVersion = rma.version();
        var event = command.apply(rma);
        eventStore.append(id, expectedVersion, List.of(event));
    }
}
