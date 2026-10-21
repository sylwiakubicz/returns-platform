package dev.rma.returns.application;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.rma.returns.application.exception.ReturnNotFoundException;
import dev.rma.returns.application.model.CreateReturnCommand;
import dev.rma.returns.application.model.ReturnView;
import dev.rma.returns.application.port.in.CancelReturnUseCase;
import dev.rma.returns.application.port.in.CreateReturnUseCase;
import dev.rma.returns.application.port.out.BusinessNumberGenerator;
import dev.rma.returns.application.port.out.EventStore;
import dev.rma.returns.application.port.out.RmaIdGenerator;
import dev.rma.returns.application.port.out.RmaIdLookup;
import dev.rma.returns.domain.Rma;
import dev.rma.returns.domain.enums.RmaStatus;

@Service
public class ReturnCommandService implements CreateReturnUseCase, CancelReturnUseCase {
    private final EventStore eventStore;
    private final RmaIdGenerator idGenerator;
    private final BusinessNumberGenerator numberGenerator;
    private final Clock clock;
    private final RmaIdLookup idLookup;
    private final int returnWindowDays;

    public ReturnCommandService(
            EventStore eventStore,
            RmaIdGenerator idGenerator,
            BusinessNumberGenerator numberGenerator,
            RmaIdLookup idLookup,
            Clock clock,
            @Value("${returns.window-days}") int returnWindowDays) {
        if (returnWindowDays < 1) {
            throw new IllegalArgumentException("Return window must be positive");
        }
        this.eventStore = eventStore;
        this.idGenerator = idGenerator;
        this.numberGenerator = numberGenerator;
        this.idLookup = idLookup;
        this.clock = clock;
        this.returnWindowDays = returnWindowDays;
    }

    @Override
    @Transactional
    public ReturnView create(CreateReturnCommand command) {
        var requestedAt = clock.instant();
        var rmaId = idGenerator.nextId();
        var businessNumber = numberGenerator.nextNumber(requestedAt);
        var event = Rma.request(
                rmaId,
                businessNumber,
                command.orderId(),
                command.customerId(),
                command.orderDeliveredAt(),
                command.reason(),
                command.customerNote(),
                command.items(),
                command.currency(),
                requestedAt,
                returnWindowDays);
        eventStore.append(rmaId, 0, List.of(event));
        return new ReturnView(businessNumber, RmaStatus.REQUESTED, null, requestedAt);
    }

    @Override
    @Transactional
    public void cancel(String businessNumber, String cancelledBy, String reason) {
        var rmaId = idLookup.findId(businessNumber).orElseThrow(() -> new ReturnNotFoundException(businessNumber));
        var rma = eventStore.load(rmaId).orElseThrow(() -> new ReturnNotFoundException(businessNumber));
        long expectedVersion = rma.version();
        var event = rma.cancel(cancelledBy, clock.instant(), reason);
        eventStore.append(rmaId, expectedVersion, List.of(event));
    }
}
