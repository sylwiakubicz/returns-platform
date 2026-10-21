package dev.rma.returns.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import dev.rma.returns.application.exception.ReturnNotFoundException;
import dev.rma.returns.application.model.HistoryEntry;
import dev.rma.returns.application.port.out.EventStore;
import dev.rma.returns.application.port.out.RmaIdLookup;
import dev.rma.returns.application.port.out.StoredEvent;
import dev.rma.returns.application.port.out.StoredEventDecoder;
import dev.rma.returns.domain.events.LabelIssued;
import dev.rma.returns.domain.events.RmaCancelled;

class ReturnHistoryServiceTest {
    private static final UUID RMA_ID = UUID.randomUUID();
    private static final String NUMBER = "RMA-2026-000001";
    private static final Instant AT = Instant.parse("2026-10-21T10:00:00Z");
    private final EventStore store = mock(EventStore.class);
    private final RmaIdLookup lookup = mock(RmaIdLookup.class);
    private final StoredEventDecoder decoder = mock(StoredEventDecoder.class);
    private final ReturnHistoryService service = new ReturnHistoryService(store, lookup, decoder);

    @Test
    void readsFromFirstVersionPreservesMetadataAndOrderAndClosesStream() {
        var first = new StoredEvent(UUID.randomUUID(), RMA_ID, 2, "LabelIssued", 1, "{}", AT);
        var second = new StoredEvent(UUID.randomUUID(), RMA_ID, 3, "RmaCancelled", 1, "{}", AT.plusSeconds(1));
        var label = new LabelIssued(RMA_ID, "label", "tracking", "label.pdf", AT);
        var cancelled = new RmaCancelled(RMA_ID, "customer", AT.plusSeconds(1), null);
        var closed = new AtomicBoolean();
        when(lookup.findId(NUMBER)).thenReturn(Optional.of(RMA_ID));
        when(store.readFrom(RMA_ID, 1)).thenReturn(Stream.of(first, second).onClose(() -> closed.set(true)));
        when(decoder.decode(first)).thenReturn(label);
        when(decoder.decode(second)).thenReturn(cancelled);

        assertThat(service.history(NUMBER))
                .containsExactly(
                        new HistoryEntry(first.eventId(), RMA_ID, 2, first.type(), AT, label),
                        new HistoryEntry(second.eventId(), RMA_ID, 3, second.type(), AT.plusSeconds(1), cancelled));
        assertThat(closed).isTrue();
        verify(store, never()).load(any());
    }

    @Test
    void closesStreamAndPropagatesFailureIfDecodingFails() {
        var stored = new StoredEvent(UUID.randomUUID(), RMA_ID, 1, "UnknownEvent", 1, "{}", AT);
        var failure = new IllegalArgumentException("Unsupported event type");
        var closed = new AtomicBoolean();
        when(lookup.findId(NUMBER)).thenReturn(Optional.of(RMA_ID));
        when(store.readFrom(RMA_ID, 1)).thenReturn(Stream.of(stored).onClose(() -> closed.set(true)));
        when(decoder.decode(stored)).thenThrow(failure);

        assertThatThrownBy(() -> service.history(NUMBER)).isSameAs(failure);
        assertThat(closed).isTrue();
    }

    @Test
    void reportsUnknownReturnWithoutOpeningEventStream() {
        when(lookup.findId(NUMBER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.history(NUMBER)).isInstanceOf(ReturnNotFoundException.class);
        verifyNoInteractions(store, decoder);
    }
}
