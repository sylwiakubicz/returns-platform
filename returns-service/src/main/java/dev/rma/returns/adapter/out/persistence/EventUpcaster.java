package dev.rma.returns.adapter.out.persistence;

import com.fasterxml.jackson.databind.JsonNode;

/** Przekształca JSON jednego typu zdarzenia do kolejnej wersji schematu. */
public interface EventUpcaster {
    String type();

    int fromVersion();

    int toVersion();

    JsonNode upcast(JsonNode payload);
}
