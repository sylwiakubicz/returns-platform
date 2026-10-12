package dev.rma.returns.adapter.out.persistence;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Uruchamia kolejne transformacje bez modyfikowania historii w bazie. */
@Component
public class EventUpcasting {
    private final ObjectMapper objectMapper;
    private final List<EventUpcaster> upcasters;

    public EventUpcasting(ObjectMapper objectMapper, List<EventUpcaster> upcasters) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.upcasters = List.copyOf(upcasters);
        for (EventUpcaster upcaster : this.upcasters) {
            Objects.requireNonNull(upcaster.type(), "Upcaster type must not be null");
            if (upcaster.fromVersion() < 1 || upcaster.toVersion() != upcaster.fromVersion() + 1L) {
                throw new IllegalArgumentException("An upcaster must advance exactly one schema version");
            }
            long matches = this.upcasters.stream().filter(candidate ->
                    candidate.type().equals(upcaster.type())
                            && candidate.fromVersion() == upcaster.fromVersion()).count();
            if (matches != 1) {
                throw new IllegalArgumentException("Duplicate upcaster for " + upcaster.type()
                        + " at version " + upcaster.fromVersion());
            }
        }
    }

    public String upcast(String type, int storedVersion, int currentVersion, String payload)
            throws JsonProcessingException {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        if (storedVersion < 1 || currentVersion < 1 || storedVersion > currentVersion) {
            throw new IllegalArgumentException("Unsupported schema version " + storedVersion + " for event " + type);
        }
        if (storedVersion == currentVersion) {
            return payload;
        }
        JsonNode json = objectMapper.readTree(payload);
        for (int version = storedVersion; version < currentVersion; version++) {
            int fromVersion = version;
            EventUpcaster upcaster = upcasters.stream()
                    .filter(candidate -> candidate.type().equals(type) && candidate.fromVersion() == fromVersion)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Missing upcaster for " + type + " from schema version " + fromVersion));
            json = Objects.requireNonNull(upcaster.upcast(json), "Upcaster must return a JSON payload");
        }
        return objectMapper.writeValueAsString(json);
    }
}
