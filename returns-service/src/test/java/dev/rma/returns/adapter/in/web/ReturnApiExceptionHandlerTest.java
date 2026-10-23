package dev.rma.returns.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
        var mvc = mvc(new FailingController(failure));
        assertProblem(mvc.perform(get("/test/failure")), status, code, "/test/failure")
                .andExpect(jsonPath("$.type")
                        .value("/docs/03-kontrakty.md#"
                                + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty());
        if (failure instanceof InvalidReturnRequestException invalid) {
            mvc.perform(get("/test/failure"))
                    .andExpect(jsonPath("$.errors[0].field").value(invalid.field()));
        }
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

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
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
