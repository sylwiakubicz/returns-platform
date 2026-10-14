package dev.rma.returns.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import dev.rma.returns.application.port.in.RebuildRmaProjectionUseCase;
import dev.rma.returns.application.port.out.EventStore;
import dev.rma.returns.application.port.out.RmaProjectionResetter;
import dev.rma.returns.application.port.out.RmaProjectionUpdater;
import dev.rma.returns.application.port.out.StoredEventDecoder;

@Service
public class RebuildRmaProjectionService implements RebuildRmaProjectionUseCase {
    private final EventStore eventStore;
    private final RmaProjectionResetter resetter;
    private final RmaProjectionUpdater updater;
    private final StoredEventDecoder decoder;

    public RebuildRmaProjectionService(
            EventStore eventStore,
            RmaProjectionResetter resetter,
            RmaProjectionUpdater updater,
            StoredEventDecoder decoder) {
        this.eventStore = eventStore;
        this.resetter = resetter;
        this.updater = updater;
        this.decoder = decoder;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void rebuild() {
        resetter.resetForRebuild();
        try (var events = eventStore.readAll()) {
            events.forEachOrdered(event -> updater.apply(decoder.decode(event)));
        }
    }
}
