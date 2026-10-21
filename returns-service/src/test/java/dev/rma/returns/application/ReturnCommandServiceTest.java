package dev.rma.returns.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.rma.returns.application.exception.ConcurrencyConflictException;
import dev.rma.returns.application.exception.ReturnNotFoundException;
import dev.rma.returns.application.model.CreateReturnCommand;
import dev.rma.returns.application.model.ReturnView;
import dev.rma.returns.application.port.out.BusinessNumberGenerator;
import dev.rma.returns.application.port.out.EventStore;
import dev.rma.returns.application.port.out.RmaIdLookup;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.RequestedItem;
import dev.rma.returns.domain.Rma;
import dev.rma.returns.domain.enums.RmaErrorCode;
import dev.rma.returns.domain.enums.RmaReason;
import dev.rma.returns.domain.enums.RmaStatus;
import dev.rma.returns.domain.events.RmaCancelled;
import dev.rma.returns.domain.events.RmaRequested;
import dev.rma.returns.domain.exceptions.RmaDomainException;

class ReturnCommandServiceTest {
    private static final UUID RMA_ID = UUID.fromString("019a08ca-d100-7000-8000-000000000001");
    private static final String NUMBER = "RMA-2026-000001";
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final Currency PLN = Currency.getInstance("PLN");

    private final EventStore eventStore = mock(EventStore.class);
    private final RmaIdLookup lookup = mock(RmaIdLookup.class);

    private final BusinessNumberGenerator numbers = mock(BusinessNumberGenerator.class);

    private ReturnCommandService service(int windowDays) {
        return new ReturnCommandService(
                eventStore, () -> RMA_ID, numbers, lookup, Clock.fixed(NOW, ZoneOffset.UTC), windowDays);
    }

    private CreateReturnCommand command(Instant deliveredAt) {
        return new CreateReturnCommand(
                "order-1",
                "customer-1",
                deliveredAt,
                RmaReason.CHANGED_MIND,
                "Unwanted item",
                PLN,
                List.of(new RequestedItem("item-1", "sku-1", "Item", 2, new Money(new BigDecimal("100.00"), PLN))));
    }

    @Test
    void createsReturnWithGeneratedIdentifiersAndServerTimeAtVersionZero() {
        when(numbers.nextNumber(NOW)).thenReturn(NUMBER);
        var command = command(NOW.minus(Duration.ofDays(1)));

        var result = service(14).create(command);

        var expected = new RmaRequested(
                RMA_ID,
                NUMBER,
                command.orderId(),
                command.customerId(),
                command.orderDeliveredAt(),
                NOW,
                command.reason(),
                command.customerNote(),
                command.currency(),
                command.items());
        verify(numbers).nextNumber(NOW);
        verify(eventStore).append(RMA_ID, 0, List.of(expected));
        verifyNoMoreInteractions(eventStore);
        assertThat(result).isEqualTo(new ReturnView(NUMBER, RmaStatus.REQUESTED, null, NOW));
    }

    @Test
    void doesNotAppendEventsWhenReturnWindowHasExpired() {
        assertThatThrownBy(() -> service(14).create(command(NOW.minus(Duration.ofDays(15)))))
                .isInstanceOfSatisfying(RmaDomainException.class, error -> assertThat(error.code())
                        .isEqualTo(RmaErrorCode.RETURN_WINDOW_EXPIRED));
        verifyNoInteractions(eventStore);
    }

    @Test
    void usesConfiguredReturnWindow() {
        when(numbers.nextNumber(NOW)).thenReturn(NUMBER);
        assertThat(service(30).create(command(NOW.minus(Duration.ofDays(15)))).status())
                .isEqualTo(RmaStatus.REQUESTED);
        verify(eventStore).append(eq(RMA_ID), eq(0L), anyList());
    }

    @Test
    void doesNotAppendEventsWhenNoItemsWereRequested() {
        var command = command(NOW);
        var empty = new CreateReturnCommand(
                command.orderId(),
                command.customerId(),
                command.orderDeliveredAt(),
                command.reason(),
                command.customerNote(),
                command.currency(),
                List.of());
        assertThatThrownBy(() -> service(14).create(empty))
                .isInstanceOfSatisfying(RmaDomainException.class, error -> assertThat(error.code())
                        .isEqualTo(RmaErrorCode.NO_ITEMS));
        verifyNoInteractions(eventStore);
    }

    @Test
    void propagatesPersistenceFailureInsteadOfReturningSuccess() {
        when(numbers.nextNumber(NOW)).thenReturn(NUMBER);
        var failure = new IllegalStateException("Database unavailable");
        doThrow(failure).when(eventStore).append(eq(RMA_ID), eq(0L), anyList());
        assertThatThrownBy(() -> service(14).create(command(NOW))).isSameAs(failure);
    }

    @Test
    void rejectsInvalidReturnWindowConfiguration() {
        assertThatIllegalArgumentException().isThrownBy(() -> service(0));
    }

    private Rma requestedReturn() {
        var command = command(NOW.minusSeconds(60));
        return Rma.fromHistory(List.of(Rma.request(
                RMA_ID,
                NUMBER,
                command.orderId(),
                command.customerId(),
                command.orderDeliveredAt(),
                command.reason(),
                command.customerNote(),
                command.items(),
                command.currency(),
                NOW,
                14)));
    }

    @Test
    void cancelsUsingVersionBeforeDomainCommandAndServerTime() {
        var rma = requestedReturn();
        when(lookup.findId(NUMBER)).thenReturn(Optional.of(RMA_ID));
        when(eventStore.load(RMA_ID)).thenReturn(Optional.of(rma));
        service(14).cancel(NUMBER, "customer-1", "Changed mind");
        verify(eventStore).append(RMA_ID, 1, List.of(new RmaCancelled(RMA_ID, "customer-1", NOW, "Changed mind")));
        assertThat(rma.version()).isEqualTo(2);
        assertThat(rma.status()).isEqualTo(RmaStatus.CANCELLED);
        verifyNoInteractions(numbers);
    }

    @Test
    void reportsUnknownNumberWithoutLoadingOrWritingEvents() {
        when(lookup.findId(NUMBER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(14).cancel(NUMBER, "customer-1", null))
                .isInstanceOf(ReturnNotFoundException.class);
        verifyNoInteractions(eventStore);
    }

    @Test
    void reportsMissingAggregateWithoutWritingEvents() {
        when(lookup.findId(NUMBER)).thenReturn(Optional.of(RMA_ID));
        when(eventStore.load(RMA_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(14).cancel(NUMBER, "customer-1", null))
                .isInstanceOf(ReturnNotFoundException.class);
        verify(eventStore, never()).append(any(), anyLong(), anyList());
    }

    @Test
    void doesNotWriteWhenDomainForbidsCancellation() {
        var rma = requestedReturn();
        rma.issueLabel("label", "tracking", "label.pdf", NOW);
        rma.markParcelInTransit(NOW);
        when(lookup.findId(NUMBER)).thenReturn(Optional.of(RMA_ID));
        when(eventStore.load(RMA_ID)).thenReturn(Optional.of(rma));
        assertThatThrownBy(() -> service(14).cancel(NUMBER, "customer-1", null))
                .isInstanceOfSatisfying(RmaDomainException.class, error -> assertThat(error.code())
                        .isEqualTo(RmaErrorCode.CANCELLATION_NOT_ALLOWED));
        verify(eventStore, never()).append(any(), anyLong(), anyList());
    }

    @Test
    void propagatesCancellationConcurrencyConflict() {
        when(lookup.findId(NUMBER)).thenReturn(Optional.of(RMA_ID));
        when(eventStore.load(RMA_ID)).thenReturn(Optional.of(requestedReturn()));
        var conflict = new ConcurrencyConflictException("Concurrent update", null);
        doThrow(conflict).when(eventStore).append(eq(RMA_ID), eq(1L), anyList());
        assertThatThrownBy(() -> service(14).cancel(NUMBER, "customer-1", null)).isSameAs(conflict);
    }
}
