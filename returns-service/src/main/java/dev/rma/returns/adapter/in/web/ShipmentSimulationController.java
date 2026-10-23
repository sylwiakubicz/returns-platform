package dev.rma.returns.adapter.in.web;

import java.io.IOException;
import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.rma.returns.application.ShipmentSimulationService;
import dev.rma.returns.application.exception.InvalidReturnRequestException;

/** Tymczasowe endpointy oznaczone jako deprecated w OpenAPI; usuwane w etapie 6 projektu. */
@RestController
@RequestMapping("/internal/sim/returns/{businessNumber}")
public class ShipmentSimulationController {
    private final ShipmentSimulationService service;
    private final ObjectMapper mapper;

    public ShipmentSimulationController(ShipmentSimulationService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(value = "/label-issued", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> issueLabel(@PathVariable("businessNumber") String number, @RequestBody byte[] body) {
        validateNumber(number);
        var request = read(body, LabelRequest.class, "labelId", "trackingNumber", "labelFileKey");
        service.issueLabel(number, request.labelId(), request.trackingNumber(), request.labelFileKey());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/pickup-scheduled", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> schedulePickup(
            @PathVariable("businessNumber") String number, @RequestBody byte[] body) {
        validateNumber(number);
        var request = read(body, PickupRequest.class, "pickupId", "scheduledFor");
        service.schedulePickup(number, request.pickupId(), request.scheduledFor());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/parcel-in-transit")
    public ResponseEntity<Void> markInTransit(@PathVariable("businessNumber") String number) {
        validateNumber(number);
        service.markInTransit(number);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/parcel-received", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receiveParcel(@PathVariable("businessNumber") String number, @RequestBody byte[] body) {
        validateNumber(number);
        var request = read(body, ReceiveRequest.class, "warehouseId");
        service.receiveParcel(number, request.warehouseId());
        return ResponseEntity.noContent().build();
    }

    private <T> T read(byte[] body, Class<T> type, String... requiredFields) {
        try {
            JsonNode json = mapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(body);
            if (json == null || !json.isObject()) {
                throw new InvalidReturnRequestException("$", "Request must be a JSON object");
            }
            for (String field : requiredFields) {
                if (!json.path(field).isTextual()
                        || json.path(field).textValue().isBlank()) {
                    throw new InvalidReturnRequestException(field, "Non-blank text is required");
                }
            }
            return mapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(json);
        } catch (IOException exception) {
            throw new InvalidReturnRequestException(exception);
        }
    }

    private void validateNumber(String number) {
        if (!number.matches("RMA-[0-9]{4}-[0-9]{6}")) {
            throw new InvalidReturnRequestException("businessNumber", "Use the RMA-YYYY-NNNNNN business number");
        }
    }

    public record LabelRequest(String labelId, String trackingNumber, String labelFileKey) {}

    public record PickupRequest(String pickupId, LocalDate scheduledFor) {}

    public record ReceiveRequest(String warehouseId) {}
}
