package dev.rma.returns.adapter.in.web;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import dev.rma.returns.application.exception.ConcurrencyConflictException;
import dev.rma.returns.application.exception.IdempotencyKeyReuseException;
import dev.rma.returns.application.exception.InvalidReturnRequestException;
import dev.rma.returns.application.exception.InvalidSimulationCommandException;
import dev.rma.returns.application.exception.ReturnNotFoundException;
import dev.rma.returns.domain.enums.RmaErrorCode;
import dev.rma.returns.domain.exceptions.InvalidRmaStateTransitionException;
import dev.rma.returns.domain.exceptions.RmaDomainException;

/** Obsługa błędów API zwrotów; wspólny katalog błędów w module common pozostaje do kolejnego kroku. */
@RestControllerAdvice
public class ReturnApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ReturnApiExceptionHandler.class);

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpectedFailure(Exception exception, WebRequest request) {
        LOG.error("Unexpected return API failure", exception);
        return response(
                problem(500, "INTERNAL_ERROR", "Internal server error", "The request could not be completed", request),
                HttpHeaders.EMPTY);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code =
                switch (status.value()) {
                    case 400 -> "INVALID_REQUEST";
                    case 415 -> "UNSUPPORTED_MEDIA_TYPE";
                    case 406 -> "NOT_ACCEPTABLE";
                    default -> "HTTP_REQUEST_ERROR";
                };
        var problem =
                problem(status.value(), code, "HTTP request error", "Check the request headers and body", request);
        if (status.value() == 400) {
            String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                    ? mismatch.getName()
                    : exception instanceof MissingRequestHeaderException missing ? missing.getHeaderName() : "$";
            problem.setProperty(
                    "errors",
                    List.of(Map.of("field", field, "message", "Check request parameters, required headers and body")));
        }
        return response(problem, headers);
    }

    private ProblemDetail problem(int status, String code, String title, String detail, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setTitle(title);
        problem.setType(URI.create("/docs/03-kontrakty.md#"
                + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(request.getDescription(false).substring("uri=".length())));
        problem.setProperty("code", code);
        return problem;
    }

    private ResponseEntity<Object> response(ProblemDetail problem, HttpHeaders headers) {
        return ResponseEntity.status(problem.getStatus())
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
