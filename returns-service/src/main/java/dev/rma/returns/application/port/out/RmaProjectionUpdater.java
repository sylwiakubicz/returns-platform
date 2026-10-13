package dev.rma.returns.application.port.out;

import dev.rma.returns.domain.events.DomainEvent;

public interface RmaProjectionUpdater {
    /**
     * Uwzględnia zdarzenie w projekcji RMA.
     * Wywołujący przekazuje zdarzenia danego RMA w kolejności ich wersji.
     */
    void apply(DomainEvent event);
}
