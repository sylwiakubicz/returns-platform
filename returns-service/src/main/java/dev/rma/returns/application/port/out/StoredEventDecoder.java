package dev.rma.returns.application.port.out;

import dev.rma.returns.domain.events.DomainEvent;

public interface StoredEventDecoder {
    DomainEvent decode(StoredEvent event);
}
