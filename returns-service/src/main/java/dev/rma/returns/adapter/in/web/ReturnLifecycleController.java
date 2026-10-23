package dev.rma.returns.adapter.in.web;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.rma.returns.application.exception.InvalidReturnRequestException;
import dev.rma.returns.application.port.in.CancelReturnUseCase;
import dev.rma.returns.application.port.in.GetReturnHistoryUseCase;

@RestController
@RequestMapping("/api/v1/returns/{businessNumber}")
public class ReturnLifecycleController {
    private final CancelReturnUseCase cancelReturn;
    private final GetReturnHistoryUseCase getHistory;
    private final ReturnHistoryResponseMapper historyMapper;
    private final ObjectMapper mapper;

    public ReturnLifecycleController(
            CancelReturnUseCase cancelReturn,
            GetReturnHistoryUseCase getHistory,
            ReturnHistoryResponseMapper historyMapper,
            ObjectMapper mapper) {
        this.cancelReturn = cancelReturn;
        this.getHistory = getHistory;
        this.historyMapper = historyMapper;
        this.mapper = mapper;
    }

    @PostMapping(value = "/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> cancel(
            @PathVariable("businessNumber") String businessNumber, @RequestBody byte[] body) {
        validateNumber(businessNumber);
        var request = readCancellation(body);
        cancelReturn.cancel(businessNumber, request.cancelledBy(), request.reason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ReturnHistoryResponseMapper.HistoryResponse> history(
            @PathVariable("businessNumber") String businessNumber) {
        validateNumber(businessNumber);
        return getHistory.history(businessNumber).stream()
                .map(historyMapper::from)
                .toList();
    }

    private CancelRequest readCancellation(byte[] body) {
        try {
            JsonNode json = mapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(body);
            if (json == null || !json.isObject()) {
                throw new InvalidReturnRequestException("$", "Request must be a JSON object");
            }
            if (!json.path("cancelledBy").isTextual()
                    || json.path("cancelledBy").textValue().isBlank()) {
                throw new InvalidReturnRequestException("cancelledBy", "Non-blank text is required");
            }
            var reason = json.path("reason");
            if (!reason.isMissingNode() && !reason.isNull() && !reason.isTextual()) {
                throw new InvalidReturnRequestException("reason", "Reason must be text or null");
            }
            return mapper.readerFor(CancelRequest.class)
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

    public record CancelRequest(String cancelledBy, String reason) {}
}
