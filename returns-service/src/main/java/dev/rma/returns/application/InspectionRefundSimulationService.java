package dev.rma.returns.application;

import java.time.Clock;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.rma.returns.application.exception.InvalidSimulationCommandException;
import dev.rma.returns.application.exception.ReturnNotFoundException;
import dev.rma.returns.application.port.out.EventStore;
import dev.rma.returns.application.port.out.RmaIdLookup;
import dev.rma.returns.domain.InspectionResult;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.Rma;
import dev.rma.returns.domain.enums.ClosureReason;
import dev.rma.returns.domain.events.DomainEvent;
import dev.rma.returns.domain.policy.InspectionPolicy;
import dev.rma.returns.domain.policy.RefundPolicy;

/** Tymczasowe symulacje do etapu 6 projektu. */
@Service
public class InspectionRefundSimulationService {
    private final EventStore eventStore;
    private final RmaIdLookup idLookup;
    private final Clock clock;
    private final InspectionPolicy inspectionPolicy;
    private final RefundPolicy refundPolicy;

    public InspectionRefundSimulationService(
            EventStore eventStore,
            RmaIdLookup idLookup,
            Clock clock,
            InspectionPolicy inspectionPolicy,
            RefundPolicy refundPolicy) {
        this.eventStore = eventStore;
        this.idLookup = idLookup;
        this.clock = clock;
        this.inspectionPolicy = inspectionPolicy;
        this.refundPolicy = refundPolicy;
    }

    @Transactional
    public void startInspection(String number, String inspectorId) {
        execute(number, rma -> List.of(rma.startInspection(inspectorId, clock.instant())));
    }

    @Transactional
    public void completeInspection(
            String number, String inspectorId, List<InspectionResult> results, String approvedBy) {
        execute(
                number,
                rma -> rma.completeInspection(
                        inspectorId, clock.instant(), results, approvedBy, inspectionPolicy, refundPolicy));
    }

    @Transactional
    public void executeRefund(String number, String payoutId, Money amount, int attempt) {
        execute(number, rma -> List.of(rma.executeRefund(payoutId, amount, attempt, clock.instant())));
    }

    @Transactional
    public void close(String number, ClosureReason reason) {
        execute(number, rma -> List.of(rma.close(reason, clock.instant())));
    }

    private void execute(String number, Function<Rma, List<DomainEvent>> command) {
        var id = idLookup.findId(number).orElseThrow(() -> new ReturnNotFoundException(number));
        var rma = eventStore.load(id).orElseThrow(() -> new ReturnNotFoundException(number));
        long version = rma.version();
        List<DomainEvent> events;
        try {
            events = command.apply(rma);
        } catch (IllegalArgumentException exception) {
            throw new InvalidSimulationCommandException(exception.getMessage(), exception);
        }
        eventStore.append(id, version, events);
    }
}
