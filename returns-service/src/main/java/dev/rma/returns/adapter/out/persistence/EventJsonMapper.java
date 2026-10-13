package dev.rma.returns.adapter.out.persistence;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.rma.returns.domain.events.*;

@Component
public class EventJsonMapper {

    private final ObjectMapper objectMapper;
    private final EventUpcasting upcasting;

    public EventJsonMapper(ObjectMapper objectMapper, EventUpcasting upcasting) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.upcasting = Objects.requireNonNull(upcasting, "upcasting must not be null");
    }

    public String toJson(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize event " + typeOf(event), exception);
        }
    }

    public DomainEvent fromJson(String type, int schemaVersion, String payload) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Class<? extends DomainEvent> eventClass =
                switch (type) {
                    case "RmaRequested" -> RmaRequested.class;
                    case "LabelIssued" -> LabelIssued.class;
                    case "PickupScheduled" -> PickupScheduled.class;
                    case "ParcelInTransit" -> ParcelInTransit.class;
                    case "ParcelReceived" -> ParcelReceived.class;
                    case "InspectionStarted" -> InspectionStarted.class;
                    case "InspectionCompleted" -> InspectionCompleted.class;
                    case "RefundDecided" -> RefundDecided.class;
                    case "RefundExecuted" -> RefundExecuted.class;
                    case "RefundFailed" -> RefundFailed.class;
                    case "RmaExpired" -> RmaExpired.class;
                    case "RmaLost" -> RmaLost.class;
                    case "RmaCancelled" -> RmaCancelled.class;
                    case "RmaClosed" -> RmaClosed.class;
                    default -> throw new IllegalArgumentException("Unknown event type: " + type);
                };

        try {
            String currentPayload = upcasting.upcast(type, schemaVersion, currentSchemaVersion(eventClass), payload);
            DomainEvent event = objectMapper.readValue(currentPayload, eventClass);
            if (event == null) {
                throw new IllegalStateException("Event payload must not be JSON null for event " + type);
            }
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot deserialize event " + type + " with schema version " + schemaVersion, exception);
        }
    }

    public String typeOf(DomainEvent event) {
        return switch (event) {
            case RmaRequested e -> "RmaRequested";
            case LabelIssued e -> "LabelIssued";
            case PickupScheduled e -> "PickupScheduled";
            case ParcelInTransit e -> "ParcelInTransit";
            case ParcelReceived e -> "ParcelReceived";
            case InspectionStarted e -> "InspectionStarted";
            case InspectionCompleted e -> "InspectionCompleted";
            case RefundDecided e -> "RefundDecided";
            case RefundExecuted e -> "RefundExecuted";
            case RefundFailed e -> "RefundFailed";
            case RmaExpired e -> "RmaExpired";
            case RmaLost e -> "RmaLost";
            case RmaCancelled e -> "RmaCancelled";
            case RmaClosed e -> "RmaClosed";
        };
    }

    public int schemaVersionOf(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return currentSchemaVersion(event.getClass());
    }

    private int currentSchemaVersion(Class<? extends DomainEvent> eventClass) {
        // Wszystkie obecne typy zdarzeń mają pierwszy format JSON.
        return 1;
    }
}
