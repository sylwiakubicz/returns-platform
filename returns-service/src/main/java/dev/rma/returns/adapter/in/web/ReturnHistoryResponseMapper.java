package dev.rma.returns.adapter.in.web;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import dev.rma.returns.application.model.HistoryEntry;
import dev.rma.returns.application.port.out.StoredEventDecoder;
import dev.rma.returns.domain.Money;

@Component
public class ReturnHistoryResponseMapper {
    private final ObjectMapper mapper;
    private final StoredEventDecoder schemas;

    public ReturnHistoryResponseMapper(ObjectMapper mapper, StoredEventDecoder schemas) {
        this.schemas = schemas;
        var moneyModule = new SimpleModule().addSerializer(Money.class, new JsonSerializer<Money>() {
            @Override
            public void serialize(Money money, JsonGenerator json, SerializerProvider provider) throws IOException {
                json.writeStartObject();
                json.writeStringField("amount", money.amount().toPlainString());
                json.writeStringField("currency", money.currency().getCurrencyCode());
                json.writeEndObject();
            }
        });
        this.mapper = mapper.copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .registerModule(moneyModule);
    }

    public HistoryResponse from(HistoryEntry entry) {
        return new HistoryResponse(
                entry.eventId(),
                entry.rmaId(),
                entry.version(),
                entry.type(),
                schemas.schemaVersionOf(entry.event()),
                entry.occurredAt(),
                mapper.valueToTree(entry.event()));
    }

    public record HistoryResponse(
            UUID eventId,
            UUID rmaId,
            long version,
            String type,
            int schemaVersion,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
            JsonNode payload) {}
}
