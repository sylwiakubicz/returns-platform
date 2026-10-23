package dev.rma.returns.adapter.in.web;

import java.io.IOException;
import java.util.List;

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

import dev.rma.returns.application.InspectionRefundSimulationService;
import dev.rma.returns.application.exception.InvalidReturnRequestException;
import dev.rma.returns.domain.InspectionResult;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.enums.ClosureReason;

/** Tymczasowe endpointy deprecated w OpenAPI, usuwane w etapie 6 projektu. */
@RestController
@RequestMapping("/internal/sim/returns/{businessNumber}")
public class InspectionRefundSimulationController {
    private final InspectionRefundSimulationService service;
    private final ObjectMapper mapper;

    public InspectionRefundSimulationController(InspectionRefundSimulationService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(value = "/inspection-started", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> start(@PathVariable("businessNumber") String number, @RequestBody byte[] body) {
        validateNumber(number);
        var json = object(body);
        text(json, "inspectorId", "inspectorId");
        service.startInspection(number, bind(json, StartRequest.class).inspectorId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/inspection-completed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> complete(@PathVariable("businessNumber") String number, @RequestBody byte[] body) {
        validateNumber(number);
        var json = object(body);
        text(json, "inspectorId", "inspectorId");
        var results = json.path("results");
        if (!results.isArray() || results.isEmpty()) {
            invalid("results", "At least one inspection result is required");
        }
        for (int i = 0; i < results.size(); i++) {
            var result = results.get(i);
            String path = "results[" + i + "]";
            text(result, "itemId", path + ".itemId");
            text(result, "grade", path + ".grade");
            optionalText(result, "note", path + ".note");
            if (result.path("note").isTextual()
                    && result.path("note").textValue().length() > 500) {
                invalid(path + ".note", "Note must not exceed 500 characters");
            }
            var photos = result.path("photoKeys");
            if (!photos.isMissingNode()) {
                if (!photos.isArray()) {
                    invalid(path + ".photoKeys", "Photo keys must be an array");
                }
                for (var photo : photos) {
                    if (!photo.isTextual() || photo.textValue().isBlank()) {
                        invalid(path + ".photoKeys", "Photo keys must be non-blank text");
                    }
                }
            }
        }
        optionalText(json, "approvedBy", "approvedBy");
        var request = bind(json, CompleteRequest.class);
        service.completeInspection(number, request.inspectorId(), request.results(), request.approvedBy());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/refund-executed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> refund(@PathVariable("businessNumber") String number, @RequestBody byte[] body) {
        validateNumber(number);
        var json = object(body);
        text(json, "payoutId", "payoutId");
        var amount = json.path("amount");
        text(amount, "amount", "amount.amount");
        if (!amount.path("amount").textValue().matches("[0-9]+\\.[0-9]{2}")) {
            invalid("amount.amount", "Use a non-negative decimal string with two decimal places");
        }
        text(amount, "currency", "amount.currency");
        if (!amount.path("currency").textValue().matches("[A-Z]{3}")) {
            invalid("amount.currency", "Use a three-letter ISO currency code");
        }
        var attempt = json.path("attempt");
        if (!attempt.isIntegralNumber() || !attempt.canConvertToInt() || attempt.intValue() < 1) {
            invalid("attempt", "Attempt must be a positive integer");
        }
        var request = bind(json, RefundRequest.class);
        service.executeRefund(number, request.payoutId(), request.amount(), request.attempt());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/close", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> close(@PathVariable("businessNumber") String number, @RequestBody byte[] body) {
        validateNumber(number);
        var json = object(body);
        text(json, "closureReason", "closureReason");
        service.close(number, bind(json, CloseRequest.class).closureReason());
        return ResponseEntity.noContent().build();
    }

    private JsonNode object(byte[] body) {
        try {
            JsonNode json = mapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(body);
            if (json == null || !json.isObject()) {
                invalid("$", "Request must be a JSON object");
            }
            return json;
        } catch (IOException exception) {
            throw new InvalidReturnRequestException(exception);
        }
    }

    private <T> T bind(JsonNode json, Class<T> type) {
        try {
            return mapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .readValue(json);
        } catch (IOException exception) {
            throw new InvalidReturnRequestException(exception);
        }
    }

    private void text(JsonNode json, String field, String path) {
        if (!json.path(field).isTextual() || json.path(field).textValue().isBlank()) {
            invalid(path, "Non-blank text is required");
        }
    }

    private void optionalText(JsonNode json, String field, String path) {
        var value = json.path(field);
        if (!value.isMissingNode() && !value.isNull() && !value.isTextual()) {
            invalid(path, "Use text or null");
        }
    }

    private void validateNumber(String number) {
        if (!number.matches("RMA-[0-9]{4}-[0-9]{6}")) {
            invalid("businessNumber", "Use the RMA-YYYY-NNNNNN business number");
        }
    }

    private void invalid(String field, String message) {
        throw new InvalidReturnRequestException(field, message);
    }

    public record StartRequest(String inspectorId) {}

    public record CompleteRequest(String inspectorId, List<InspectionResult> results, String approvedBy) {}

    public record RefundRequest(String payoutId, Money amount, int attempt) {}

    public record CloseRequest(ClosureReason closureReason) {}
}
