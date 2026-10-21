package dev.rma.returns.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.rma.returns.application.exception.ReturnNotFoundException;
import dev.rma.returns.application.model.ReturnPage;
import dev.rma.returns.application.model.ReturnQuery;
import dev.rma.returns.application.model.ReturnView;
import dev.rma.returns.application.port.out.RmaProjectionReader;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.enums.RmaStatus;

class ReturnQueryServiceTest {
    private final RmaProjectionReader reader = mock(RmaProjectionReader.class);
    private final ReturnQueryService service = new ReturnQueryService(reader);

    @Test
    void returnsProjectionWithItsOriginalFreshnessTimestampAndMoney() {
        var updatedAt = Instant.parse("2026-10-21T10:00:00Z");
        var view = new ReturnView(
                "RMA-2026-000001",
                RmaStatus.REFUNDED,
                new Money(new BigDecimal("123.45"), Currency.getInstance("PLN")),
                updatedAt);
        when(reader.findByBusinessNumber(view.businessNumber())).thenReturn(Optional.of(view));

        assertThat(service.get(view.businessNumber())).isEqualTo(view);
    }

    @Test
    void reportsMissingSingleReturn() {
        when(reader.findByBusinessNumber("RMA-2026-000001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("RMA-2026-000001"))
                .isInstanceOf(ReturnNotFoundException.class)
                .hasMessageContaining("RMA-2026-000001");
    }

    @Test
    void emptyFilteredPageIsAValidResultRatherThanNotFound() {
        var query = new ReturnQuery(0, 20, RmaStatus.CLOSED);
        when(reader.findAll(query)).thenReturn(new ReturnPage(List.of(), 0, 20, 0, 0));

        assertThat(service.list(query)).isEqualTo(new ReturnPage(List.of(), 0, 20, 0, 0));
    }
}
