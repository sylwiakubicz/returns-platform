package dev.rma.returns.adapter.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import dev.rma.common.error.ApiExceptionHandler;
import dev.rma.returns.application.exception.ConcurrencyConflictException;
import dev.rma.returns.application.exception.IdempotencyKeyReuseException;
import dev.rma.returns.application.exception.InvalidReturnRequestException;
import dev.rma.returns.application.exception.InvalidSimulationCommandException;
import dev.rma.returns.application.exception.ReturnNotFoundException;
import dev.rma.returns.domain.enums.RmaErrorCode;
import dev.rma.returns.domain.exceptions.InvalidRmaStateTransitionException;
import dev.rma.returns.domain.exceptions.RmaDomainException;

@RestControllerAdvice
public class ReturnApiExceptionHandler extends ApiExceptionHandler {

    @ExceptionHandler(InvalidReturnRequestException.class)
    public ResponseEntity<Object> invalidRequest(InvalidReturnRequestException exception, WebRequest request) {
        var problem = problem(400, "INVALID_REQUEST", "Invalid request", exception.getMessage(), request);
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return response(problem, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(ReturnNotFoundException.class)
    public ResponseEntity<Object> notFound(ReturnNotFoundException exception, WebRequest request) {
        return response(
                problem(404, "RETURN_NOT_FOUND", "Return not found", exception.getMessage(), request),
                HttpHeaders.EMPTY);
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ResponseEntity<Object> keyReuse(IdempotencyKeyReuseException exception, WebRequest request) {
        return response(
                problem(422, "IDEMPOTENCY_KEY_REUSED", "Idempotency key conflict", exception.getMessage(), request),
                HttpHeaders.EMPTY);
    }

    @ExceptionHandler(RmaDomainException.class)
    public ResponseEntity<Object> domainFailure(RmaDomainException exception, WebRequest request) {
        return response(
                problem(
                        exception.code() == RmaErrorCode.CANCELLATION_NOT_ALLOWED ? 409 : 422,
                        exception.code().name(),
                        "Return command rejected",
                        exception.getMessage(),
                        request),
                HttpHeaders.EMPTY);
    }

    @ExceptionHandler(InvalidSimulationCommandException.class)
    public ResponseEntity<Object> invalidSimulation(InvalidSimulationCommandException exception, WebRequest request) {
        return response(
                problem(422, "SIMULATION_COMMAND_REJECTED", "Simulation rejected", exception.getMessage(), request),
                HttpHeaders.EMPTY);
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    public ResponseEntity<Object> concurrencyConflict(ConcurrencyConflictException exception, WebRequest request) {
        return response(
                problem(
                        409,
                        "CONCURRENCY_CONFLICT",
                        "Return changed",
                        "The return was changed by another request; read its current state before retrying",
                        request),
                HttpHeaders.EMPTY);
    }

    @ExceptionHandler(InvalidRmaStateTransitionException.class)
    public ResponseEntity<Object> invalidTransition(InvalidRmaStateTransitionException exception, WebRequest request) {
        return response(
                problem(409, "INVALID_STATE_TRANSITION", "Invalid state transition", exception.getMessage(), request),
                HttpHeaders.EMPTY);
    }
}
