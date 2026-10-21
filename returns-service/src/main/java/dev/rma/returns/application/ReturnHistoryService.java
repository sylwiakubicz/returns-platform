package dev.rma.returns.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.rma.returns.application.exception.ReturnNotFoundException;
import dev.rma.returns.application.model.HistoryEntry;
import dev.rma.returns.application.port.in.GetReturnHistoryUseCase;
import dev.rma.returns.application.port.out.EventStore;
import dev.rma.returns.application.port.out.RmaIdLookup;
import dev.rma.returns.application.port.out.StoredEventDecoder;

@Service
public class ReturnHistoryService implements GetReturnHistoryUseCase {
    private final EventStore eventStore;
    private final RmaIdLookup idLookup;
    private final StoredEventDecoder decoder;

    public ReturnHistoryService(EventStore eventStore, RmaIdLookup idLookup, StoredEventDecoder decoder) {
        this.eventStore = eventStore;
        this.idLookup = idLookup;
        this.decoder = decoder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoryEntry> history(String businessNumber) {
        var rmaId = idLookup.findId(businessNumber).orElseThrow(() -> new ReturnNotFoundException(businessNumber));
        // Czytamy od początku historii; snapshoty służą tylko do odtwarzania agregatu.
        try (var events = eventStore.readFrom(rmaId, 1)) {
            return events.map(event -> new HistoryEntry(
                            event.eventId(),
                            event.rmaId(),
                            event.version(),
                            event.type(),
                            event.occurredAt(),
                            decoder.decode(event)))
                    .toList();
        }
    }
}
