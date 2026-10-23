package dev.rma.returns.adapter.in.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.rma.returns.application.exception.ConcurrencyConflictException;
import dev.rma.returns.application.model.HistoryEntry;
import dev.rma.returns.application.port.out.StoredEventDecoder;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.events.RefundExecuted;

class ReturnLifecycleControllerTest {
    @Test
    void mapsConcurrencyConflictTo409WithoutLeakingInternalMessage() throws Exception {
        var controller = new ReturnLifecycleController(
                (number, cancelledBy, reason) -> {
                    throw new ConcurrencyConflictException("internal database constraint", null);
                },
                number -> List.of(),
                mock(ReturnHistoryResponseMapper.class),
                Jackson2ObjectMapperBuilder.json().build());
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ReturnApiExceptionHandler())
                .build();
        mvc.perform(post("/api/v1/returns/RMA-2026-000001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelledBy\":\"customer\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENCY_CONFLICT"))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("internal database constraint"))));
    }

    @Test
    void historyUsesCurrentPayloadSchemaVersionAndPreservesAggregateVersion() {
        var at = Instant.parse("2026-10-21T10:00:00Z");
        var event = new RefundExecuted(
                UUID.randomUUID(), "payout", new Money(new BigDecimal("85.00"), Currency.getInstance("EUR")), 1, at);
        var schemas = mock(StoredEventDecoder.class);
        when(schemas.schemaVersionOf(event)).thenReturn(2);
        var historyMapper = new ReturnHistoryResponseMapper(
                Jackson2ObjectMapperBuilder.json().build(), schemas);
        var response =
                historyMapper.from(new HistoryEntry(UUID.randomUUID(), event.rmaId(), 42, "RefundExecuted", at, event));
        assertThat(response.version()).isEqualTo(42);
        assertThat(response.schemaVersion()).isEqualTo(2);
        assertThat(response.payload().path("amount").path("amount").asText()).isEqualTo("85.00");
        assertThat(response.payload().path("amount").path("currency").asText()).isEqualTo("EUR");
        assertThat(response.payload().path("executedAt").asText()).isEqualTo(at.toString());
    }
}
