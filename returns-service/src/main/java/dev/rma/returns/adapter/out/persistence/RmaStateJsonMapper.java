package dev.rma.returns.adapter.out.persistence;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.rma.returns.domain.RmaState;

/** Serializacja pełnego stanu agregatu, używanego przez adapter do snapshotów. */
@Component
public class RmaStateJsonMapper {
    private final ObjectMapper objectMapper;

    public RmaStateJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String toJson(RmaState state) {
        Objects.requireNonNull(state, "state must not be null");
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize state of RMA " + state.rmaId(), exception);
        }
    }

    public RmaState fromJson(String payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        try {
            RmaState state = objectMapper.readValue(payload, RmaState.class);
            if (state == null) {
                throw new IllegalStateException("RMA state payload must not be JSON null");
            }
            return state;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize RMA state", exception);
        }
    }
}
