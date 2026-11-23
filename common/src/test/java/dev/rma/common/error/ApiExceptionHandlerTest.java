package dev.rma.common.error;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {
    private static final String PATH = "/test/request";

    private final ApiExceptionHandler handler = new ApiExceptionHandler() {};
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("POST", PATH));

    @Test
    void reportsValidationFieldsAndObjectErrors() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "body");
        binding.addError(new FieldError("body", "orderId", "Order ID is required"));
        binding.addError(new FieldError("body", "quantity", null));
        binding.addError(new ObjectError("body", "The fields are inconsistent"));

        var response = handler.handleException(new MethodArgumentNotValidException(parameter(), binding), request);
        var problem = assertProblem(response, 400, "INVALID_REQUEST", "invalid-request");
        assertEquals(
                List.of(
                        Map.of("field", "orderId", "message", "Order ID is required"),
                        Map.of("field", "quantity", "message", "Invalid value"),
                        Map.of("field", "$", "message", "The fields are inconsistent")),
                problem.getProperties().get("errors"));
    }

    @Test
    void reportsTheInvalidParameterHeaderOrBody() throws Exception {
        assertInvalidInput(
                new MethodArgumentTypeMismatchException("abc", Integer.class, "quantity", parameter(), null),
                "quantity");
        assertInvalidInput(new MissingRequestHeaderException("X-Request-Id", parameter()), "X-Request-Id");
        assertInvalidInput(
                new HttpMessageNotReadableException("Invalid JSON", new MockHttpInputMessage(new byte[0])), "$");
    }

    @Test
    void mapsMvcStatusesAndPreservesHeaders() throws Exception {
        var method =
                handler.handleException(new HttpRequestMethodNotSupportedException("GET", List.of("POST")), request);
        assertProblem(method, 405, "HTTP_REQUEST_ERROR", "http-request-error");
        assertEquals("POST", method.getHeaders().getFirst(HttpHeaders.ALLOW));

        var unsupported = handler.handleException(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)),
                request);
        assertProblem(unsupported, 415, "UNSUPPORTED_MEDIA_TYPE", "unsupported-media-type");

        var unacceptable = handler.handleException(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), request);
        assertProblem(unacceptable, 406, "NOT_ACCEPTABLE", "not-acceptable");
    }

    @Test
    void returnsASafeProblemForUnexpectedAndMvcServerErrors() throws Exception {
        var unexpected = handler.unexpectedFailure(new IllegalStateException("Internal database details"), request);
        var mvc = handler.handleException(
                new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal database details"), request);

        for (var response : List.of(unexpected, mvc)) {
            var problem = assertProblem(response, 500, "INTERNAL_ERROR", "internal-error");
            assertEquals("The request could not be completed", problem.getDetail());
            assertEquals(Map.of("code", "INTERNAL_ERROR"), problem.getProperties());
        }
    }

    private void assertInvalidInput(Exception exception, String field) throws Exception {
        var problem =
                assertProblem(handler.handleException(exception, request), 400, "INVALID_REQUEST", "invalid-request");
        assertEquals(
                List.of(Map.of("field", field, "message", "Check request parameters, required headers and body")),
                problem.getProperties().get("errors"));
    }

    private ProblemDetail assertProblem(ResponseEntity<Object> response, int status, String code, String anchor) {
        assertEquals(status, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        var problem = assertInstanceOf(ProblemDetail.class, response.getBody());
        assertEquals(status, problem.getStatus());
        assertEquals(code, problem.getProperties().get("code"));
        assertEquals(URI.create("/docs/errors#" + anchor), problem.getType());
        assertEquals(URI.create(PATH), problem.getInstance());
        return problem;
    }

    private MethodParameter parameter() throws NoSuchMethodException {
        return new MethodParameter(getClass().getDeclaredMethod("requestHandler", Integer.class), 0);
    }

    private void requestHandler(Integer quantity) {}
}
