package dev.rma.returns.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class EventUpcastingTest {
    @Test
    void shouldRenameFieldInOlderBookFormatWithoutChangingStoredPayload() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        EventUpcaster bookUpcaster = new EventUpcaster() {
            @Override
            public String type() {
                return "BookAdded";
            }

            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public int toVersion() {
                return 2;
            }

            @Override
            public JsonNode upcast(JsonNode payload) {
                ObjectNode updated = payload.deepCopy();
                updated.set("name", updated.remove("title"));
                return updated;
            }
        };
        EventUpcasting upcasting = new EventUpcasting(objectMapper, List.of(bookUpcaster));
        String storedPayload = "{\"title\":\"Hobbit\"}";

        String currentPayload = upcasting.upcast("BookAdded", 1, 2, storedPayload);

        assertThat(objectMapper.readTree(currentPayload))
                .isEqualTo(objectMapper.readTree("{\"name\":\"Hobbit\"}"));
        assertThat(storedPayload).isEqualTo("{\"title\":\"Hobbit\"}");
    }
}
