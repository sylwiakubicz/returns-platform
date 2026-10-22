package dev.rma.returns.application;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import dev.rma.common.idempotency.RequestBodyHasher;
import dev.rma.returns.application.exception.IdempotencyKeyReuseException;
import dev.rma.returns.application.exception.InvalidReturnRequestException;
import dev.rma.returns.application.model.IdempotencyRecord;
import dev.rma.returns.application.model.ReturnCreationResponse;
import dev.rma.returns.application.port.in.CreateReturnUseCase;
import dev.rma.returns.application.port.out.IdempotencyStore;

@Service
public class IdempotentReturnCreationService {
    private static final String ENDPOINT = "/api/v1/returns";
    private final CreateReturnUseCase creator;
    private final IdempotencyStore store;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Duration retention;
    private final ReturnCreationRequestReader requestReader;

    public IdempotentReturnCreationService(
            CreateReturnUseCase creator,
            IdempotencyStore store,
            ObjectMapper mapper,
            Clock clock,
            @Value("${returns.idempotency.retention:PT24H}") String retention) {
        var duration = Duration.parse(retention);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Idempotency retention must be positive");
        }
        this.retention = duration;
        this.creator = creator;
        this.store = store;
        this.mapper = mapper;
        this.requestReader = new ReturnCreationRequestReader(mapper);
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReturnCreationResponse create(String key, byte[] body) {
        if (key == null || key.isBlank() || key.length() > 255) {
            throw new InvalidReturnRequestException(
                    "Idempotency-Key", "Idempotency-Key must contain between 1 and 255 characters");
        }
        if (body == null) {
            throw new InvalidReturnRequestException("$", "Request body is required");
        }
        String hash = RequestBodyHasher.sha256(body);
        store.lock(key, ENDPOINT);
        store.deleteExpired(key, ENDPOINT, clock.instant().minus(retention));
        var saved = store.find(key, ENDPOINT);
        if (saved.isPresent()) {
            if (!saved.get().requestHash().equals(hash)) {
                throw new IdempotencyKeyReuseException();
            }
            return response(saved.get());
        }
        var command = requestReader.read(body);
        var view = creator.create(command);
        String responseBody;
        try {
            responseBody = mapper.writer()
                    .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValueAsString(view);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize return creation response", exception);
        }
        var record = new IdempotencyRecord(hash, 201, responseBody, clock.instant());
        store.save(key, ENDPOINT, record);
        return response(record);
    }

    private ReturnCreationResponse response(IdempotencyRecord record) {
        try {
            var number = mapper.readTree(record.responseBody()).path("businessNumber");
            if (!number.isTextual() || !number.textValue().matches("RMA-[0-9]{4}-[0-9]{6}")) {
                throw new IllegalStateException("Stored response has an invalid business number");
            }
            return new ReturnCreationResponse(
                    record.responseStatus(), record.responseBody(), ENDPOINT + "/" + number.textValue());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read stored return creation response", exception);
        }
    }
}
