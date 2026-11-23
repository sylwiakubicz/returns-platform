package dev.rma.common.error;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

public abstract class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String ERROR_CATALOG_PATH = "/docs/errors";

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    protected ProblemDetail problem(int status, String code, String title, String detail, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setTitle(title);
        problem.setType(URI.create(ERROR_CATALOG_PATH + "#"
                + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(request.getDescription(false).substring("uri=".length())));
        problem.setProperty("code", code);
        return problem;
    }

    protected ResponseEntity<Object> response(ProblemDetail problem, HttpHeaders headers) {
        return ResponseEntity.status(problem.getStatus())
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.value() == 500) {
            return internalError(exception, headers, request);
        }
        String code =
                switch (status.value()) {
                    case 400 -> "INVALID_REQUEST";
                    case 415 -> "UNSUPPORTED_MEDIA_TYPE";
                    case 406 -> "NOT_ACCEPTABLE";
                    default -> "HTTP_REQUEST_ERROR";
                };
        var problem = problem(
                status.value(), code, "HTTP request error", "Check the HTTP status and response headers", request);
        if (exception instanceof MethodArgumentNotValidException invalid) {
            var errors = invalid.getBindingResult().getAllErrors().stream()
                    .map(error -> Map.of(
                            "field", error instanceof FieldError fieldError ? fieldError.getField() : "$",
                            "message", error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
                    .toList();
            problem.setProperty("errors", errors);
        } else if (status.value() == 400) {
            String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                    ? mismatch.getName()
                    : exception instanceof MissingRequestHeaderException missing ? missing.getHeaderName() : "$";
            problem.setProperty(
                    "errors",
                    List.of(Map.of("field", field, "message", "Check request parameters, required headers and body")));
        }
        return response(problem, headers);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpectedFailure(Exception exception, WebRequest request) {
        return internalError(exception, HttpHeaders.EMPTY, request);
    }

    private ResponseEntity<Object> internalError(Exception exception, HttpHeaders headers, WebRequest request) {
        LOG.error("Unexpected API failure.", exception);
        return response(
                problem(500, "INTERNAL_ERROR", "Internal server error", "The request could not be completed", request),
                headers);
    }
}
