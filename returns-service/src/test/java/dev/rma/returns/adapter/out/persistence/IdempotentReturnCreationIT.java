package dev.rma.returns.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.rma.returns.adapter.in.scheduling.IdempotencyCleanupJob;
import dev.rma.returns.adapter.in.web.InspectionRefundSimulationController;
import dev.rma.returns.adapter.in.web.ReturnApiExceptionHandler;
import dev.rma.returns.adapter.in.web.ReturnCreationController;
import dev.rma.returns.adapter.in.web.ReturnHistoryResponseMapper;
import dev.rma.returns.adapter.in.web.ReturnLifecycleController;
import dev.rma.returns.adapter.in.web.ReturnQueryController;
import dev.rma.returns.adapter.in.web.ShipmentSimulationController;
import dev.rma.returns.adapter.out.id.UuidV7RmaIdGenerator;
import dev.rma.returns.adapter.out.persistence.mappers.EventJsonMapper;
import dev.rma.returns.adapter.out.persistence.mappers.RmaStateJsonMapper;
import dev.rma.returns.application.IdempotentReturnCreationService;
import dev.rma.returns.application.InspectionRefundSimulationService;
import dev.rma.returns.application.RebuildRmaProjectionService;
import dev.rma.returns.application.ReturnCommandService;
import dev.rma.returns.application.ReturnHistoryService;
import dev.rma.returns.application.ReturnQueryService;
import dev.rma.returns.application.ShipmentSimulationService;
import dev.rma.returns.application.exception.IdempotencyKeyReuseException;
import dev.rma.returns.application.exception.InvalidReturnRequestException;
import dev.rma.returns.application.model.ReturnCreationResponse;
import dev.rma.returns.application.port.out.EventStore;
import dev.rma.returns.application.port.out.IdempotencyStore;
import dev.rma.returns.application.port.out.StoredEvent;
import dev.rma.returns.config.SimulationPolicyConfiguration;
import dev.rma.returns.domain.InspectionResult;
import dev.rma.returns.domain.Rma;
import dev.rma.returns.domain.enums.FailureCode;
import dev.rma.returns.domain.enums.ItemGrade;
import dev.rma.returns.domain.events.DomainEvent;
import dev.rma.returns.domain.exceptions.RmaDomainException;
import dev.rma.returns.domain.policy.InspectionPolicy;
import dev.rma.returns.domain.policy.RefundPolicy;

@Testcontainers
@SpringJUnitConfig(IdempotentReturnCreationIT.TestConfig.class)
@TestPropertySource(
        properties = {
            "returns.window-days=14",
            "returns.return-shipping-fee=15.00",
            "returns.deduction.grade-a-percent=0",
            "returns.deduction.grade-b-percent=15",
            "returns.deduction.grade-c-percent=40",
            "inspection.supervisor-approval-threshold=500.00"
        })
class IdempotentReturnCreationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    IdempotentReturnCreationService service;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("adminJdbcTemplate")
    JdbcTemplate admin;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    IdempotencyCleanupJob cleanupJob;

    @Autowired
    IdempotencyStore store;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ReturnCreationController controller;

    @Autowired
    ReturnApiExceptionHandler exceptionHandler;

    @Autowired
    ReturnLifecycleController lifecycleController;

    @Autowired
    EventStore eventStore;

    @Autowired
    ShipmentSimulationController shipmentController;

    @Autowired
    InspectionRefundSimulationController inspectionController;

    @Autowired
    ReturnQueryController queryController;

    private MockMvc mvc;

    private static final String JSON =
            """
        {"orderId":"order-1","customerId":"customer-1","orderDeliveredAt":"2026-10-20T10:00:00Z",
         "reason":"CHANGED_MIND","currency":"PLN","items":[{"itemId":"item-1","sku":"sku-1",
         "name":"Book","quantity":1,"unitPrice":{"amount":"100.00","currency":"PLN"}}]}
        """;

    private byte[] body() {
        return JSON.getBytes(StandardCharsets.UTF_8);
    }

    @BeforeEach
    void clearDatabase() {
        mvc = MockMvcBuilders.standaloneSetup(
                        controller, lifecycleController, shipmentController, inspectionController, queryController)
                .setControllerAdvice(exceptionHandler)
                .build();
        admin.update("DELETE FROM returns.rma_snapshots");
        admin.update("DELETE FROM returns.rma_events");
        admin.update("DELETE FROM returns.rma_projection");
        admin.update("DELETE FROM returns.idempotency_keys");
        admin.update("DELETE FROM returns.rma_business_number_counters");
    }

    @Test
    void replaysOriginalResponseEvenWhenCurrentReturnHasChanged() throws Exception {
        var first = service.create("key-1", body());
        assertThat(first.status()).isEqualTo(201);
        assertThat(first.location()).isEqualTo("/api/v1/returns/RMA-2026-000001");
        jdbcTemplate.update("UPDATE returns.rma_projection SET status = 'CANCELLED'");
        var replay = service.create("key-1", body());
        assertThat(replay.status()).isEqualTo(first.status());
        assertThat(replay.location()).isEqualTo(first.location());
        assertThat(mapper.readTree(replay.body())).isEqualTo(mapper.readTree(first.body()));
        assertThat(mapper.readTree(replay.body()).path("status").asText()).isEqualTo("REQUESTED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT last_value FROM returns.rma_business_number_counters", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsChangedBodyBeforeAttemptingToParseIt() {
        service.create("key-1", body());
        assertThatThrownBy(() -> service.create("key-1", "{invalid".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IdempotencyKeyReuseException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void malformedNewRequestDoesNotReserveKey() {
        assertThatThrownBy(() -> service.create("key-1", "{invalid".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidReturnRequestException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.idempotency_keys", Integer.class))
                .isZero();
        assertThat(service.create("key-1", body()).status()).isEqualTo(201);
    }

    @Test
    void domainRejectionRollsBackNumberAndDoesNotReserveKey() {
        byte[] expired = JSON.replace("2026-10-20", "2026-09-01").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.create("key-1", expired)).isInstanceOf(RmaDomainException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.idempotency_keys", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isZero();
        assertThat(service.create("key-1", body()).location()).endsWith("RMA-2026-000001");
    }

    @Test
    void failureToStoreResponseRollsBackReturnProjectionAndCounter() {
        admin.execute(
                "ALTER TABLE returns.idempotency_keys ADD CONSTRAINT test_reject_response CHECK (response_status <> 201)");
        try {
            assertThatThrownBy(() -> service.create("key-1", body()))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_projection", Integer.class))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.idempotency_keys", Integer.class))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM returns.rma_business_number_counters", Integer.class))
                    .isZero();
        } finally {
            admin.execute("ALTER TABLE returns.idempotency_keys DROP CONSTRAINT test_reject_response");
        }
        assertThat(service.create("key-1", body()).location()).endsWith("RMA-2026-000001");
    }

    @RepeatedTest(20)
    void concurrentIdenticalRequestsCreateOneReturnAndReplayResponse() throws Exception {
        var results = concurrentRequests(body(), body());
        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(ReturnCreationResponse.class));
        var first = (ReturnCreationResponse) results.get(0);
        var second = (ReturnCreationResponse) results.get(1);
        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(first.location()).isEqualTo(second.location());
        assertThat(mapper.readTree(first.body())).isEqualTo(mapper.readTree(second.body()));
        assertSingleReturn();
    }

    @Test
    void concurrentDifferentBodiesProduceOneSuccessAndOneKeyConflict() throws Exception {
        var results =
                concurrentRequests(body(), JSON.replace("order-1", "order-2").getBytes(StandardCharsets.UTF_8));
        assertThat(results.stream()
                        .filter(ReturnCreationResponse.class::isInstance)
                        .count())
                .isEqualTo(1);
        assertThat(results.stream()
                        .filter(IdempotencyKeyReuseException.class::isInstance)
                        .count())
                .isEqualTo(1);
        assertSingleReturn();
    }

    @Test
    void waitingRequestCanCreateReturnAfterFirstTransactionRollsBack() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var waiting = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timeout");
                }
                return service.create("key-1", body());
            });
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                store.lock("key-1", "/api/v1/returns");
                start.countDown();
                awaitWaitingRequests(1);
                byte[] expired = JSON.replace("2026-10-20", "2026-09-01").getBytes(StandardCharsets.UTF_8);
                assertThatThrownBy(() -> service.create("key-1", expired)).isInstanceOf(RmaDomainException.class);
                status.setRollbackOnly();
            });
            assertThat(waiting.get(10, TimeUnit.SECONDS).location()).endsWith("RMA-2026-000001");
        }
        assertSingleReturn();
    }

    private List<Object> concurrentRequests(byte[] firstBody, byte[] secondBody) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(request(start, firstBody));
            var second = executor.submit(request(start, secondBody));
            // Wstrzymujemy obydwa żądania w bazie, aby na pewno nałożyły się w czasie.
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                store.lock("key-1", "/api/v1/returns");
                start.countDown();
                awaitWaitingRequests(2);
            });
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private Callable<Object> request(CountDownLatch start, byte[] body) {
        return () -> {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start timeout");
            }
            try {
                return service.create("key-1", body);
            } catch (RuntimeException exception) {
                return exception;
            }
        };
    }

    private void awaitWaitingRequests(int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            int waiting = admin.queryForObject(
                    "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted AND objsubid = 1",
                    Integer.class);
            if (waiting == expected) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while checking database locks", exception);
            }
        }
        throw new AssertionError("Expected " + expected + " requests waiting for the idempotency lock");
    }

    private void assertSingleReturn() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_projection", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.idempotency_keys", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT last_value FROM returns.rma_business_number_counters", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void expiredKeyCanCreateNewReturnWithoutWaitingForCleanup() {
        var first = service.create("key-1", body());
        Instant cutoff = Instant.parse("2026-10-21T10:00:00Z").minus(Duration.ofHours(24));
        admin.update(
                "UPDATE returns.idempotency_keys SET created_at = ? WHERE idempotency_key = ?",
                Timestamp.from(cutoff.minusNanos(1000)),
                "key-1");
        var second = service.create("key-1", JSON.replace("order-1", "order-2").getBytes(StandardCharsets.UTF_8));
        assertThat(second.location()).isNotEqualTo(first.location()).endsWith("RMA-2026-000002");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.idempotency_keys", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void cleanupDeletesOnlyEntriesOlderThan24HoursAndKeepsReturnHistory() {
        service.create("old", body());
        var boundary = service.create("boundary", body());
        service.create("recent", body());
        Instant cutoff = Instant.parse("2026-10-21T10:00:00Z").minus(Duration.ofHours(24));
        admin.update(
                "UPDATE returns.idempotency_keys SET created_at = ? WHERE idempotency_key = 'old'",
                Timestamp.from(cutoff.minusNanos(1000)));
        admin.update(
                "UPDATE returns.idempotency_keys SET created_at = ? WHERE idempotency_key = 'boundary'",
                Timestamp.from(cutoff));
        admin.update(
                "UPDATE returns.idempotency_keys SET created_at = ? WHERE idempotency_key = 'recent'",
                Timestamp.from(cutoff.plusNanos(1000)));

        assertThat(cleanupJob.cleanup()).isEqualTo(1);
        assertThat(store.find("old", "/api/v1/returns")).isEmpty();
        assertThat(store.find("boundary", "/api/v1/returns")).isPresent();
        assertThat(store.find("recent", "/api/v1/returns")).isPresent();
        assertThat(service.create("boundary", body()).location()).isEqualTo(boundary.location());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_projection", Integer.class))
                .isEqualTo(3);
        assertThat(cleanupJob.cleanup()).isZero();
    }

    @Test
    void concurrentRequestsAfterExpiryStillCreateOnlyOneNewReturn() throws Exception {
        var old = service.create("key-1", body());
        admin.update(
                "UPDATE returns.idempotency_keys SET created_at = ?",
                Timestamp.from(Instant.parse("2026-10-20T09:59:59Z")));
        var results = concurrentRequests(body(), body());
        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(ReturnCreationResponse.class));
        var first = (ReturnCreationResponse) results.get(0);
        var second = (ReturnCreationResponse) results.get(1);
        assertThat(first.location()).isEqualTo(second.location()).isNotEqualTo(old.location());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.idempotency_keys", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void httpCreatesAndReplaysReturnWithLocation() throws Exception {
        var first = mvc.perform(post("/api/v1/returns")
                        .header("Idempotency-Key", "http-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Location", "/api/v1/returns/RMA-2026-000001"))
                .andExpect(jsonPath("$.businessNumber").value("RMA-2026-000001"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.refundAmount").isEmpty())
                .andExpect(jsonPath("$.updatedAt").value("2026-10-21T10:00:00Z"))
                .andReturn();
        var second = mvc.perform(post("/api/v1/returns")
                        .header("Idempotency-Key", "http-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/returns/RMA-2026-000001"))
                .andReturn();
        assertThat(mapper.readTree(first.getResponse().getContentAsByteArray()))
                .isEqualTo(mapper.readTree(second.getResponse().getContentAsByteArray()));
        assertSingleReturn();
    }

    @Test
    void httpReturns422ForReusedKeyWithDifferentBody() throws Exception {
        service.create("http-key", body());
        mvc.perform(post("/api/v1/returns")
                        .header("Idempotency-Key", "http-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance").value("/api/v1/returns"));
        assertSingleReturn();
    }

    @Test
    void httpRejectsMissingHeaderAndUnsupportedMediaType() throws Exception {
        mvc.perform(post("/api/v1/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/returns")
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(body()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{invalid", "null", "{}", "[]"})
    void httpRejectsMalformedOrIncompleteRequestsWithoutReservingKey(String body) throws Exception {
        mvc.perform(post("/api/v1/returns")
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").isNotEmpty());
        assertThat(store.find("key", "/api/v1/returns")).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isZero();
    }

    @Test
    void httpRejectsUnknownFieldsAndExtraJsonObjects() throws Exception {
        for (String invalid :
                new String[] {JSON + "{}", JSON.replace("\"orderId\":", "\"unknown\":true,\"orderId\":")}) {
            mvc.perform(post("/api/v1/returns")
                            .header("Idempotency-Key", "key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid))
                    .andExpect(status().isBadRequest());
        }
        assertThat(store.find("key", "/api/v1/returns")).isEmpty();
    }

    @Test
    void httpRejectsIncompatibleAcceptHeader() throws Exception {
        mvc.perform(post("/api/v1/returns")
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content(body()))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isZero();
    }

    @Test
    void httpRejectsFractionalQuantityInsteadOfSilentlyRoundingIt() throws Exception {
        mvc.perform(post("/api/v1/returns")
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.replace("\"quantity\":1", "\"quantity\":1.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));
    }

    @Test
    void httpPreservesRawBodyForIdempotencyComparison() throws Exception {
        service.create("key", body());
        mvc.perform(post("/api/v1/returns")
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON + " "))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void httpRejectsBlankOrOversizedKeys() throws Exception {
        for (String key : new String[] {" ", "a".repeat(256)}) {
            mvc.perform(post("/api/v1/returns")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
        }
    }

    @Test
    void httpReportsDomainRejectionAs422() throws Exception {
        mvc.perform(post("/api/v1/returns")
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.replace("2026-10-20", "2026-09-01")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RETURN_WINDOW_EXPIRED"));
    }

    @Test
    void httpHidesDatabaseDetailsOnUnexpectedFailure() throws Exception {
        admin.execute(
                "ALTER TABLE returns.idempotency_keys ADD CONSTRAINT test_http_failure CHECK (response_status <> 201)");
        try {
            mvc.perform(post("/api/v1/returns")
                            .header("Idempotency-Key", "key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(content()
                            .string(org.hamcrest.Matchers.not(
                                    org.hamcrest.Matchers.containsString("test_http_failure"))));
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                    .isZero();
        } finally {
            admin.execute("ALTER TABLE returns.idempotency_keys DROP CONSTRAINT test_http_failure");
        }
    }

    @Test
    void httpCancelsReturnAndExposesCompleteHistory() throws Exception {
        service.create("key", body());
        mvc.perform(post("/api/v1/returns/RMA-2026-000001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelledBy\":\"customer-1\",\"reason\":\"Changed mind\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(get("/api/v1/returns/RMA-2026-000001/history"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("RmaRequested"))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].schemaVersion").value(1))
                .andExpect(jsonPath("$[0].eventId").isString())
                .andExpect(jsonPath("$[0].payload.requestedAt").value("2026-10-21T10:00:00Z"))
                .andExpect(jsonPath("$[0].payload.items[0].unitPrice.amount").value("100.00"))
                .andExpect(jsonPath("$[0].payload.items[0].unitPrice.currency").value("PLN"))
                .andExpect(jsonPath("$[1].type").value("RmaCancelled"))
                .andExpect(jsonPath("$[1].version").value(2))
                .andExpect(jsonPath("$[1].occurredAt").value("2026-10-21T10:00:00Z"))
                .andExpect(jsonPath("$[1].payload.cancelledBy").value("customer-1"))
                .andExpect(jsonPath("$[1].payload.reason").value("Changed mind"));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM returns.rma_projection", String.class))
                .isEqualTo("CANCELLED");
        mvc.perform(post("/api/v1/returns/RMA-2026-000001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelledBy\":\"customer-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCELLATION_NOT_ALLOWED"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void httpReturnsFullHistoryDespiteSnapshotAndMissingProjection() throws Exception {
        var rma = refundPendingReturn();
        Instant at = Instant.parse("2026-10-21T11:00:00Z");
        long expectedVersion = rma.version();
        var events = new ArrayList<DomainEvent>();
        while (rma.version() < 51) {
            events.add(
                    rma.failRefund(FailureCode.TIMEOUT, "timeout", rma.state().payoutAttempt(), true, at));
            at = at.plusSeconds(60);
            events.add(rma.retryRefund(at));
            at = at.plusSeconds(60);
        }
        eventStore.append(rma.rmaId(), expectedVersion, events);
        long snapshotVersion = rma.version();
        eventStore.append(
                rma.rmaId(),
                snapshotVersion,
                List.of(rma.failRefund(
                        FailureCode.TIMEOUT, "timeout", rma.state().payoutAttempt(), true, at)));
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM returns.rma_snapshots", Integer.class))
                .isEqualTo((int) snapshotVersion);
        jdbcTemplate.update("DELETE FROM returns.rma_projection");
        var result = mvc.perform(get("/api/v1/returns/RMA-2026-000001/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(rma.version()))
                .andExpect(jsonPath("$[6].type").value("RefundDecided"))
                .andExpect(jsonPath("$[6].payload.refundAmount.amount").value("85.00"))
                .andReturn();
        var json = mapper.readTree(result.getResponse().getContentAsByteArray());
        var ids = jdbcTemplate.queryForList("SELECT event_id FROM returns.rma_events ORDER BY version", UUID.class);
        for (int i = 0; i < json.size(); i++) {
            assertThat(json.get(i).path("version").asInt()).isEqualTo(i + 1);
            assertThat(json.get(i).path("schemaVersion").asInt()).isEqualTo(1);
            assertThat(json.get(i).path("eventId").asText())
                    .isEqualTo(ids.get(i).toString());
            assertThat(json.get(i).path("rmaId").asText()).isEqualTo(rma.rmaId().toString());
        }
    }

    @Test
    void httpRejectsCancellationAfterInspectionWithoutAddingEvent() throws Exception {
        var rma = refundPendingReturn();
        int version = rma.version();
        mvc.perform(post("/api/v1/returns/RMA-2026-000001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelledBy\":\"customer-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCELLATION_NOT_ALLOWED"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(version);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "null",
                "{invalid",
                "{\"cancelledBy\":\" \"}",
                "{\"cancelledBy\":\"customer\",\"reason\":42}",
                "{\"cancelledBy\":\"customer\",\"extra\":true}"
            })
    void httpRejectsInvalidCancellationBody(String body) throws Exception {
        mvc.perform(post("/api/v1/returns/RMA-2026-000001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void httpValidatesNumbersAndReturns404ForUnknownReturn() throws Exception {
        mvc.perform(get("/api/v1/returns/RMA-2026-999999/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RETURN_NOT_FOUND"));
        mvc.perform(post("/api/v1/returns/RMA-2026-999999/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelledBy\":\"customer-1\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/returns/not-a-business-number/history"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("businessNumber"));
        mvc.perform(post("/api/v1/returns/not-a-business-number/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelledBy\":\"customer-1\"}"))
                .andExpect(status().isBadRequest());
    }

    private dev.rma.returns.domain.Rma refundPendingReturn() {
        service.create("key", body());
        UUID id = jdbcTemplate.queryForObject("SELECT rma_id FROM returns.rma_projection", UUID.class);
        var rma = eventStore.load(id).orElseThrow();
        long expected = rma.version();
        Instant at = Instant.parse("2026-10-21T10:00:00Z");
        var events = new ArrayList<DomainEvent>();
        events.add(rma.issueLabel("label", "tracking", "label.pdf", at));
        events.add(rma.markParcelInTransit(at));
        events.add(rma.receive(at, "warehouse"));
        events.add(rma.startInspection("inspector", at));
        events.addAll(rma.completeInspection(
                "inspector",
                at,
                List.of(new InspectionResult("item-1", ItemGrade.A, null, List.of())),
                null,
                new InspectionPolicy(new BigDecimal("500.00")),
                new RefundPolicy(0, 15, 40, new BigDecimal("15.00"))));
        eventStore.append(id, expected, events);
        return rma;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void httpSimulatesShipmentWithOptionalPickupAndRecordsOrderedEvents(boolean pickup) throws Exception {
        service.create("key", body());
        simulate("RMA-2026-000001", "label-issued")
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM returns.rma_projection", String.class))
                .isEqualTo("LABEL_ISSUED");
        if (pickup) {
            simulate("RMA-2026-000001", "pickup-scheduled").andExpect(status().isNoContent());
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM returns.rma_projection", String.class))
                    .isEqualTo("PICKUP_SCHEDULED");
        }
        simulate("RMA-2026-000001", "parcel-in-transit").andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM returns.rma_projection", String.class))
                .isEqualTo("IN_TRANSIT");
        simulate("RMA-2026-000001", "parcel-received").andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM returns.rma_projection", String.class))
                .isEqualTo("RECEIVED");
        var result = mvc.perform(get("/api/v1/returns/RMA-2026-000001/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(pickup ? 5 : 4))
                .andExpect(jsonPath("$[1].payload.labelId").value("label-1"))
                .andExpect(jsonPath("$[1].payload.trackingNumber").value("tracking-1"))
                .andExpect(jsonPath("$[1].payload.labelFileKey").value("labels/label-1.pdf"))
                .andReturn();
        var history = mapper.readTree(result.getResponse().getContentAsByteArray());
        for (int i = 0; i < history.size(); i++) {
            assertThat(history.get(i).path("version").asInt()).isEqualTo(i + 1);
            assertThat(history.get(i).path("occurredAt").asText()).isEqualTo("2026-10-21T10:00:00Z");
        }
        assertThat(history.get(history.size() - 1).path("type").asText()).isEqualTo("ParcelReceived");
        assertThat(history.get(history.size() - 1)
                        .path("payload")
                        .path("warehouseId")
                        .asText())
                .isEqualTo("warehouse-1");
        if (pickup) {
            assertThat(history.get(2).path("payload").path("scheduledFor").asText())
                    .isEqualTo("2026-10-22");
        }
    }

    @Test
    void httpRejectsOutOfOrderAndRepeatedShipmentOperationsWithoutAddingEvents() throws Exception {
        service.create("key", body());
        for (String operation : new String[] {"pickup-scheduled", "parcel-in-transit", "parcel-received"}) {
            simulate("RMA-2026-000001", operation)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(1);
        simulate("RMA-2026-000001", "label-issued").andExpect(status().isNoContent());
        simulate("RMA-2026-000001", "label-issued").andExpect(status().isConflict());
        simulate("RMA-2026-000001", "parcel-received").andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"label-issued", "pickup-scheduled", "parcel-in-transit", "parcel-received"})
    void httpValidatesBusinessNumberAndMissingReturnForShipmentOperations(String operation) throws Exception {
        simulate("RMA-2026-999999", operation)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RETURN_NOT_FOUND"));
        simulate("not-a-business-number", operation)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("businessNumber"));
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "label-issued|{}",
                "label-issued|{\"labelId\":12,\"trackingNumber\":\"t\",\"labelFileKey\":\"f\"}",
                "label-issued|{\"labelId\":\"l\",\"trackingNumber\":\"t\",\"labelFileKey\":\"f\",\"issuedAt\":\"2026-10-01T00:00:00Z\"}",
                "pickup-scheduled|{\"pickupId\":\"p\",\"scheduledFor\":\"not-a-date\"}",
                "pickup-scheduled|{\"pickupId\":\"p\"}",
                "parcel-received|{\"warehouseId\":\" \"}",
                "parcel-received|null",
                "parcel-received|{invalid"
            },
            delimiter = '|')
    void httpRejectsInvalidShipmentBodies(String operation, String body) throws Exception {
        service.create("key", body());
        mvc.perform(post("/internal/sim/returns/RMA-2026-000001/" + operation)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void httpRequiresJsonForShipmentData() throws Exception {
        mvc.perform(post("/internal/sim/returns/RMA-2026-000001/label-issued")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("label"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    private ResultActions simulate(String number, String operation) throws Exception {
        var request = post("/internal/sim/returns/" + number + "/" + operation);
        String body =
                switch (operation) {
                    case "label-issued" -> "{\"labelId\":\"label-1\",\"trackingNumber\":\"tracking-1\",\"labelFileKey\":\"labels/label-1.pdf\"}";
                    case "pickup-scheduled" -> "{\"pickupId\":\"pickup-1\",\"scheduledFor\":\"2026-10-22\"}";
                    case "parcel-received" -> "{\"warehouseId\":\"warehouse-1\"}";
                    default -> null;
                };
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    @Test
    void httpCompletesPositivePathFromCreationToClosedAndFullHistory() throws Exception {
        receiveAndStartInspection(JSON);
        inspect("inspection-completed", completeBody("A", null)).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM returns.rma_projection", String.class))
                .isEqualTo("REFUND_PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(7);
        inspect("refund-executed", refundBody("85.00", "PLN")).andExpect(status().isNoContent());
        inspect("close", "{\"closureReason\":\"REFUND_COMPLETED\"}").andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/returns/RMA-2026-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.refundAmount.amount").value("85.00"));
        mvc.perform(get("/api/v1/returns/RMA-2026-000001/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[*].type")
                        .value(org.hamcrest.Matchers.contains(
                                "RmaRequested",
                                "LabelIssued",
                                "ParcelInTransit",
                                "ParcelReceived",
                                "InspectionStarted",
                                "InspectionCompleted",
                                "RefundDecided",
                                "RefundExecuted",
                                "RmaClosed")))
                .andExpect(jsonPath("$[5].payload.inspectorId").value("inspector-1"))
                .andExpect(jsonPath("$[6].payload.refundAmount.amount").value("85.00"))
                .andExpect(jsonPath("$[7].payload.amount.currency").value("PLN"))
                .andExpect(jsonPath("$[8].payload.closureReason").value("REFUND_COMPLETED"));
        var historyResponse = mvc.perform(get("/api/v1/returns/RMA-2026-000001/history"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        var history = mapper.readTree(historyResponse.getContentAsByteArray());
        var eventIds = new java.util.HashSet<String>();
        for (int i = 0; i < history.size(); i++) {
            var entry = history.get(i);
            assertThat(entry.path("version").asLong()).isEqualTo(i + 1);
            assertThat(entry.path("schemaVersion").asInt()).isPositive();
            assertThat(entry.path("occurredAt").asText()).isEqualTo("2026-10-21T10:00:00Z");
            assertThat(entry.path("rmaId")).isEqualTo(entry.path("payload").path("rmaId"));
            assertThat(UUID.fromString(entry.path("eventId").asText())).isNotNull();
            eventIds.add(entry.path("eventId").asText());
        }
        assertThat(eventIds).hasSize(9);
        mvc.perform(post("/api/v1/returns")
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.businessNumber").value("RMA-2026-000001"));
        inspect("close", "{\"closureReason\":\"REFUND_COMPLETED\"}").andExpect(status().isConflict());
    }

    @ParameterizedTest
    @CsvSource({"B,70.00,REFUND_PENDING", "C,45.00,REFUND_PENDING", "D,0.00,REJECTED"})
    void httpUsesConfiguredDeductionsAndCanCloseRejectedReturn(String grade, String amount, String state)
            throws Exception {
        receiveAndStartInspection(JSON);
        inspect("inspection-completed", completeBody(grade, null)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/returns/RMA-2026-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(state))
                .andExpect(jsonPath("$.refundAmount.amount").value(amount));
        if (grade.equals("D")) {
            inspect("refund-executed", refundBody("0.00", "PLN")).andExpect(status().isConflict());
            inspect("close", "{\"closureReason\":\"REJECTED_AFTER_INSPECTION\"}")
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void httpRefundsFullItemValueForTransitDamageDespiteGradeC() throws Exception {
        receiveAndStartInspection(JSON.replace("CHANGED_MIND", "DAMAGED_IN_TRANSIT"));
        inspect("inspection-completed", completeBody("C", null)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/returns/RMA-2026-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundAmount.amount").value("100.00"));
    }

    @Test
    void httpRequiresApprovalAboveConfiguredThresholdAndWritesInspectionAtomically() throws Exception {
        receiveAndStartInspection(JSON.replace("100.00", "1000.00"));
        inspect("inspection-completed", completeBody("D", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SUPERVISOR_APPROVAL_REQUIRED"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(5);
        inspect("inspection-completed", completeBody("D", "supervisor-1")).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(7);
    }

    @Test
    void httpRejectsMismatchedRefundAndClosureReasonWithoutWritingEvents() throws Exception {
        receiveAndStartInspection(JSON);
        inspect("inspection-completed", completeBody("A", null)).andExpect(status().isNoContent());
        inspect("refund-executed", refundBody("1.00", "PLN"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SIMULATION_COMMAND_REJECTED"));
        inspect("refund-executed", refundBody("85.00", "EUR")).andExpect(status().isUnprocessableEntity());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(7);
        inspect("refund-executed", refundBody("85.00", "PLN")).andExpect(status().isNoContent());
        inspect("close", "{\"closureReason\":\"CANCELLED_BY_CUSTOMER\"}").andExpect(status().isUnprocessableEntity());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM returns.rma_projection", String.class))
                .isEqualTo("REFUNDED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"inspection-started", "inspection-completed", "refund-executed", "close"})
    void httpChecksInspectionSimulationNumbersMissingReturnsAndStateOrder(String operation) throws Exception {
        String body =
                switch (operation) {
                    case "inspection-started" -> "{\"inspectorId\":\"inspector-1\"}";
                    case "inspection-completed" -> completeBody("A", null);
                    case "refund-executed" -> refundBody("85.00", "PLN");
                    default -> "{\"closureReason\":\"REFUND_COMPLETED\"}";
                };
        mvc.perform(post("/internal/sim/returns/RMA-2026-999999/" + operation)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
        mvc.perform(post("/internal/sim/returns/invalid/" + operation)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        service.create("key", body());
        inspect(operation, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "inspection-started|{}",
                "inspection-completed|{\"inspectorId\":\"i\",\"results\":[]}",
                "inspection-completed|{\"inspectorId\":\"i\",\"results\":[{\"itemId\":\"item-1\",\"grade\":\"Z\"}]}",
                "inspection-completed|{\"inspectorId\":\"i\",\"results\":[{\"itemId\":\"item-1\",\"grade\":\"A\",\"photoKeys\":[42]}]}",
                "refund-executed|{\"payoutId\":\"p\",\"amount\":{\"amount\":\"85.00\",\"currency\":\"PLN\"},\"attempt\":1.5}",
                "refund-executed|{\"payoutId\":\"p\",\"amount\":{\"amount\":85,\"currency\":\"PLN\"},\"attempt\":1}",
                "close|{\"closureReason\":\"UNKNOWN\"}",
                "close|{\"closureReason\":\"REFUND_COMPLETED\",\"closedAt\":\"2020-01-01T00:00:00Z\"}"
            },
            delimiter = '|')
    void httpRejectsMalformedInspectionAndRefundBodies(String operation, String body) throws Exception {
        service.create("key", body());
        inspect(operation, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(1);
    }

    private void receiveAndStartInspection(String json) throws Exception {
        mvc.perform(post("/api/v1/returns")
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
        simulate("RMA-2026-000001", "label-issued").andExpect(status().isNoContent());
        simulate("RMA-2026-000001", "parcel-in-transit").andExpect(status().isNoContent());
        simulate("RMA-2026-000001", "parcel-received").andExpect(status().isNoContent());
        inspect("inspection-started", "{\"inspectorId\":\"inspector-1\"}").andExpect(status().isNoContent());
    }

    private ResultActions inspect(String operation, String body) throws Exception {
        return mvc.perform(post("/internal/sim/returns/RMA-2026-000001/" + operation)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String completeBody(String grade, String approvedBy) {
        return "{\"inspectorId\":\"inspector-1\",\"results\":[{\"itemId\":\"item-1\",\"grade\":\"" + grade + "\"}]"
                + (approvedBy == null ? "" : ",\"approvedBy\":\"" + approvedBy + "\"") + "}";
    }

    private String refundBody(String amount, String currency) {
        return "{\"payoutId\":\"payout-1\",\"amount\":{\"amount\":\"" + amount + "\",\"currency\":\"" + currency
                + "\"},\"attempt\":1}";
    }

    @Test
    void httpPaginatesAndFiltersWithoutDuplicatesOrIncorrectTotals() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mvc.perform(post("/api/v1/returns")
                            .header("Idempotency-Key", "page-" + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/returns/RMA-2026-000002/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelledBy\":\"customer\"}"))
                .andExpect(status().isNoContent());
        var numbers = new ArrayList<String>();
        for (int page = 0; page < 3; page++) {
            var response = mvc.perform(
                            get("/api/v1/returns").param("page", "" + page).param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.content.length()").value(page == 2 ? 1 : 2))
                    .andReturn();
            for (var entry : mapper.readTree(response.getResponse().getContentAsByteArray())
                    .path("content")) {
                numbers.add(entry.path("businessNumber").asText());
                assertThat(entry.path("updatedAt").asText()).isEqualTo("2026-10-21T10:00:00Z");
            }
        }
        assertThat(numbers)
                .containsExactlyInAnyOrder(
                        "RMA-2026-000001", "RMA-2026-000002", "RMA-2026-000003", "RMA-2026-000004", "RMA-2026-000005");
        mvc.perform(get("/api/v1/returns").param("status", "CANCELLED").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].businessNumber").value("RMA-2026-000002"));
        mvc.perform(get("/api/v1/returns")
                        .param("status", "REQUESTED")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[*].status")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("REQUESTED"))));
        mvc.perform(get("/api/v1/returns").param("page", "3").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get("/api/v1/returns").param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        mvc.perform(get("/api/v1/returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/v1/returns").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=text", "size=0", "size=101", "size=text", "status=UNKNOWN"})
    void httpRejectsInvalidPaginationAndFilters(String query) throws Exception {
        mvc.perform(get("/api/v1/returns?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void httpReturns404ForMissingDetails() throws Exception {
        mvc.perform(get("/api/v1/returns/RMA-2026-999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RETURN_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void httpHandlesConcurrentIdempotency(boolean differentBody) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<org.springframework.mock.web.MockHttpServletResponse> firstRequest = () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(post("/api/v1/returns")
                                .header("Idempotency-Key", "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JSON))
                        .andReturn()
                        .getResponse();
            };
            var first = executor.submit(firstRequest);
            var second = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(post("/api/v1/returns")
                                .header("Idempotency-Key", "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(differentBody ? JSON.replace("order-1", "order-2") : JSON))
                        .andReturn()
                        .getResponse();
            });
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                store.lock("key-1", "/api/v1/returns");
                start.countDown();
                awaitWaitingRequests(2);
            });
            var responses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(responses)
                    .extracting(response -> response.getStatus())
                    .containsExactlyInAnyOrder(201, differentBody ? 422 : 201);
            if (differentBody) {
                var conflict = responses.stream()
                        .filter(response -> response.getStatus() == 422)
                        .findFirst()
                        .orElseThrow();
                assertThat(conflict.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                assertThat(mapper.readTree(conflict.getContentAsByteArray())
                                .path("code")
                                .asText())
                        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
            } else {
                assertThat(mapper.readTree(responses.get(0).getContentAsByteArray()))
                        .isEqualTo(mapper.readTree(responses.get(1).getContentAsByteArray()));
                assertThat(responses.get(0).getHeader("Location"))
                        .isEqualTo(responses.get(1).getHeader("Location"));
            }
        }
        assertSingleReturn();
    }

    @Test
    void httpAllowsExactlyOneWriteWhenBothCommandsDecideOnSameVersion() throws Exception {
        service.create("key", body());
        simulate("RMA-2026-000001", "label-issued").andExpect(status().isNoContent());
        var versions = new ConcurrentLinkedQueue<Long>();
        var gatedStore = new DecisionBarrierStore(eventStore, new CountDownLatch(2), versions);
        var shipmentService = new ShipmentSimulationService(
                gatedStore,
                new PostgresRmaIdLookup(jdbcTemplate),
                Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC));
        // TransactionTemplate replaces only the Spring transaction proxy around the real use case.
        var transactionalService =
                new ShipmentSimulationService(
                        gatedStore,
                        new PostgresRmaIdLookup(jdbcTemplate),
                        Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC)) {
                    @Override
                    public void markInTransit(String number) {
                        new TransactionTemplate(transactionManager)
                                .executeWithoutResult(status -> shipmentService.markInTransit(number));
                    }
                };
        var racingMvc = MockMvcBuilders.standaloneSetup(new ShipmentSimulationController(transactionalService, mapper))
                .setControllerAdvice(exceptionHandler)
                .build();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<org.springframework.mock.web.MockHttpServletResponse> command = () -> racingMvc
                    .perform(post("/internal/sim/returns/RMA-2026-000001/parcel-in-transit"))
                    .andReturn()
                    .getResponse();
            var first = executor.submit(command);
            var second = executor.submit(command);
            var responses = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(versions).containsExactly(2L, 2L);
            assertThat(responses).extracting(response -> response.getStatus()).containsExactlyInAnyOrder(204, 409);
            var conflict = responses.stream()
                    .filter(response -> response.getStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(mapper.readTree(conflict.getContentAsByteArray())
                            .path("code")
                            .asText())
                    .isEqualTo("CONCURRENCY_CONFLICT");
        }
        mvc.perform(get("/api/v1/returns/RMA-2026-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
        mvc.perform(get("/api/v1/returns/RMA-2026-000001/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].version").value(org.hamcrest.Matchers.contains(1, 2, 3)))
                .andExpect(jsonPath("$[2].type").value("ParcelInTransit"));
    }

    /** Wait after both domain decisions, before either real database append. No sleeps or production hooks. */
    private record DecisionBarrierStore(EventStore delegate, CountDownLatch decisions, Queue<Long> versions)
            implements EventStore {
        @Override
        public void append(UUID id, long expectedVersion, List<DomainEvent> events) {
            versions.add(expectedVersion);
            decisions.countDown();
            try {
                if (!decisions.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Both commands must decide before writing");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            delegate.append(id, expectedVersion, events);
        }

        @Override
        public Optional<Rma> load(UUID id) {
            return delegate.load(id);
        }

        @Override
        public Stream<StoredEvent> readFrom(UUID id, long version) {
            return delegate.readFrom(id, version);
        }

        @Override
        public Stream<StoredEvent> readAll() {
            return delegate.readAll();
        }
    }

    @Configuration
    @EnableTransactionManagement
    @Import({
        PostgresEventStore.class,
        IdempotentReturnCreationService.class,
        ReturnCreationController.class,
        ReturnLifecycleController.class,
        ShipmentSimulationController.class,
        ShipmentSimulationService.class,
        InspectionRefundSimulationController.class,
        InspectionRefundSimulationService.class,
        SimulationPolicyConfiguration.class,
        ReturnQueryController.class,
        ReturnHistoryResponseMapper.class,
        ReturnApiExceptionHandler.class,
        IdempotencyCleanupJob.class,
        ReturnCommandService.class,
        PostgresIdempotencyStore.class,
        PostgresBusinessNumberGenerator.class,
        UuidV7RmaIdGenerator.class,
        PostgresRmaProjectionUpdater.class,
        PostgresRmaProjectionResetter.class,
        RebuildRmaProjectionService.class,
        ReturnHistoryService.class,
        ReturnQueryService.class,
        PostgresRmaProjectionReader.class,
        PostgresRmaIdLookup.class,
        EventJsonMapper.class,
        EventUpcasting.class,
        RmaStateJsonMapper.class
    })
    static class TestConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        JdbcTemplate adminJdbcTemplate() {
            DataSource dataSource =
                    new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE ROLE returns_app LOGIN PASSWORD 'test-password'");
            jdbc.execute("CREATE SCHEMA returns");
            jdbc.execute("GRANT USAGE ON SCHEMA returns TO returns_app");
            // Odtwarzamy uprawnienia tabel nadawane przez infrastrukturę.
            jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA returns "
                    + "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO returns_app");
            new ResourceDatabasePopulator(
                            new ClassPathResource("db/migration/V1__create_returns_schema.sql"),
                            new ClassPathResource("db/migration/V2__allow_null_refund_amount_in_rma_projection.sql"),
                            new ClassPathResource("db/migration/V3__add_currency_to_rma_projection.sql"))
                    .execute(dataSource);
            return jdbc;
        }

        @Bean
        DataSource dataSource(@Qualifier("adminJdbcTemplate") JdbcTemplate initializedAdmin) {
            return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "returns_app", "test-password");
        }

        @Bean
        @Primary
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ObjectMapper objectMapper() {
            return Jackson2ObjectMapperBuilder.json().build();
        }
    }
}
