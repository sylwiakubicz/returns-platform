package dev.rma.returns.adapter.in.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.net.URI;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.rma.returns.application.exception.*;
import dev.rma.returns.domain.enums.RmaErrorCode;
import dev.rma.returns.domain.enums.RmaStatus;
import dev.rma.returns.domain.exceptions.*;

class ReturnApiExceptionHandlerTest {
    private static final String SECRET = "private database details";

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new InvalidReturnRequestException("size", "Invalid size"), 400, "INVALID_REQUEST"),
                Arguments.of(new InvalidReturnRequestException(new IOException(SECRET)), 400, "INVALID_REQUEST"),
                Arguments.of(new ReturnNotFoundException("RMA-2026-000001"), 404, "RETURN_NOT_FOUND"),
                Arguments.of(
                        new InvalidRmaStateTransitionException(RmaStatus.REQUESTED, RmaStatus.CLOSED),
                        409,
                        "INVALID_STATE_TRANSITION"),
                Arguments.of(
                        new ConcurrencyConflictException(SECRET, new IOException(SECRET)), 409, "CONCURRENCY_CONFLICT"),
                Arguments.of(
                        new RmaDomainException(RmaErrorCode.CANCELLATION_NOT_ALLOWED, "Cannot cancel"),
                        409,
                        "CANCELLATION_NOT_ALLOWED"),
                Arguments.of(new IdempotencyKeyReuseException(), 422, "IDEMPOTENCY_KEY_REUSED"),
                Arguments.of(
                        new InvalidSimulationCommandException("Amount differs from decision", new IOException(SECRET)),
                        422,
                        "SIMULATION_COMMAND_REJECTED"),
                Arguments.of(
                        new RmaDomainException(RmaErrorCode.INCOMPLETE_INSPECTION, "Missing item"),
                        422,
                        "INCOMPLETE_INSPECTION"),
                Arguments.of(new IllegalStateException(SECRET, new IOException(SECRET)), 500, "INTERNAL_ERROR"));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void rendersApiFailuresAsProblemDetails(RuntimeException failure, int status, String code) throws Exception {
        var mvc = mvc(new FailingController(failure), new ErrorCatalogController());
        var problem = assertProblem(mvc.perform(get("/test/failure")), status, code, "/test/failure")
                .andExpect(jsonPath("$.type")
                        .value("/docs/errors#"
                                + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty());
        assertCatalogLink(mvc, problem, code);
        if (failure instanceof InvalidReturnRequestException invalid) {
            problem.andExpect(jsonPath("$.errors[0].field").value(invalid.field()));
        }
        if (status == 500) {
            problem.andExpect(jsonPath("$.detail").value("The request could not be completed"));
        }
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of(
                        """
                        {"orderId":"","customerId":"customer-1"}
                        """,
                        new String[] {"orderId"}),
                Arguments.of("{}", new String[] {"orderId", "customerId"}),
                Arguments.of("{", new String[] {"$"}));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void reportsAllInvalidBodyFields(String body, String[] fields) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ValidationController())
                .setControllerAdvice(new ReturnApiExceptionHandler())
                .setValidator(new RequestValidator())
                .build();
        assertProblem(
                        mvc.perform(post("/test/validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)),
                        400,
                        "INVALID_REQUEST",
                        "/test/validation")
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder(fields)));
    }

    @Test
    void rendersInvalidQueryParametersAs400() throws Exception {
        var mvc = mvc(new ReturnQueryController(
                number -> {
                    throw new AssertionError("Invalid request must not reach use case");
                },
                query -> {
                    throw new AssertionError("Invalid request must not reach use case");
                }));
        for (String query : new String[] {"page=invalid", "size=invalid", "status=INVALID", "page=-1", "size=0"}) {
            assertProblem(mvc.perform(get("/api/v1/returns?" + query)), 400, "INVALID_REQUEST", "/api/v1/returns")
                    .andExpect(jsonPath("$.errors[0].field").isNotEmpty());
        }
    }

    @Test
    void rendersMissingHeaderAndBodyAs400() throws Exception {
        var mvc = mvc(new ReturnCreationController(null));
        assertProblem(
                        mvc.perform(post("/api/v1/returns")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")),
                        400,
                        "INVALID_REQUEST",
                        "/api/v1/returns")
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
        assertProblem(
                mvc.perform(post("/api/v1/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key")),
                400,
                "INVALID_REQUEST",
                "/api/v1/returns");
    }

    @Test
    void followsCatalogLinksFromAdminAndMvcErrors() throws Exception {
        var adminPath = "/admin/projections/rebuild";
        var returnsPath = "/api/v1/returns";
        var mvc = mvc(
                new ErrorCatalogController(),
                new ReturnCreationController(null),
                new RmaProjectionAdminController(() -> {
                    throw new IllegalStateException(SECRET);
                }));
        assertCatalogLink(
                mvc, assertProblem(mvc.perform(post(adminPath)), 500, "INTERNAL_ERROR", adminPath), "INTERNAL_ERROR");
        assertCatalogLink(
                mvc,
                assertProblem(mvc.perform(get(adminPath)), 405, "HTTP_REQUEST_ERROR", adminPath),
                "HTTP_REQUEST_ERROR");
        assertCatalogLink(
                mvc,
                assertProblem(
                        mvc.perform(post(returnsPath)
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("{}")),
                        415,
                        "UNSUPPORTED_MEDIA_TYPE",
                        returnsPath),
                "UNSUPPORTED_MEDIA_TYPE");
        assertCatalogLink(
                mvc,
                assertProblem(
                        mvc.perform(get("/docs/errors").accept(MediaType.APPLICATION_JSON)),
                        406,
                        "NOT_ACCEPTABLE",
                        "/docs/errors"),
                "NOT_ACCEPTABLE");
    }

    private void assertCatalogLink(MockMvc mvc, ResultActions problem, String code) throws Exception {
        var json = new ObjectMapper().readTree(problem.andReturn().getResponse().getContentAsString());
        var type = URI.create(json.get("type").asText());
        mvc.perform(get(type.getPath()).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"" + type.getFragment() + "\">")))
                .andExpect(content().string(containsString("<code>" + code + "</code>")));
    }

    private MockMvc mvc(Object... controllers) {
        return MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new ReturnApiExceptionHandler())
                .build();
    }

    private ResultActions assertProblem(ResultActions result, int status, String code, String instance)
            throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.instance").value(instance))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.cause").doesNotExist())
                .andExpect(content().string(not(containsString(SECRET))));
    }

    @RestController
    static class ValidationController {
        @PostMapping("/test/validation")
        public void validate(@RequestBody @Validated ValidationRequest request) {
            throw new AssertionError("Invalid request must not reach the controller");
        }
    }

    record ValidationRequest(String orderId, String customerId) {}

    static class RequestValidator implements Validator {
        @Override
        public boolean supports(Class<?> type) {
            return type == ValidationRequest.class;
        }

        @Override
        public void validate(Object target, Errors errors) {
            var request = (ValidationRequest) target;
            if (request.orderId() == null || request.orderId().isBlank()) {
                errors.rejectValue("orderId", "required", "Order ID is required");
            }
            if (request.customerId() == null || request.customerId().isBlank()) {
                errors.rejectValue("customerId", "required", "Customer ID is required");
            }
        }
    }

    @RestController
    static class FailingController {
        private final RuntimeException failure;

        FailingController(RuntimeException failure) {
            this.failure = failure;
        }

        @GetMapping("/test/failure")
        public void fail() {
            throw failure;
        }
    }
}
