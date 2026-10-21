package dev.rma.returns.application;

import java.time.Clock;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.rma.common.idempotency.RequestBodyHasher;
import dev.rma.returns.application.exception.IdempotencyKeyReuseException;
import dev.rma.returns.application.exception.InvalidReturnRequestException;
import dev.rma.returns.application.model.CreateReturnCommand;
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

    public IdempotentReturnCreationService(
            CreateReturnUseCase creator, IdempotencyStore store, ObjectMapper mapper, Clock clock) {
        this.creator = creator;
        this.store = store;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public ReturnCreationResponse create(String key, byte[] body) {
        if (key == null || key.isBlank() || key.length() > 255) {
            throw new IllegalArgumentException("Idempotency-Key must contain between 1 and 255 characters");
        }
        Objects.requireNonNull(body, "body must not be null");
        String hash = RequestBodyHasher.sha256(body);
        var saved = store.find(key, ENDPOINT);
        if (saved.isPresent()) {
            if (!saved.get().requestHash().equals(hash)) {
                throw new IdempotencyKeyReuseException();
            }
            return response(saved.get());
        }
        CreateReturnCommand command;
        try {
            command = mapper.readValue(body, CreateReturnCommand.class);
            if (command == null) {
                throw new InvalidReturnRequestException(null);
            }
        } catch (java.io.IOException exception) {
            throw new InvalidReturnRequestException(exception);
        }
        var view = creator.create(command);
        String responseBody;
        try {
            responseBody = mapper.writeValueAsString(view);
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
