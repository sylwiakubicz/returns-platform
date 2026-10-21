package dev.rma.returns.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.rma.returns.adapter.out.persistence.mappers.EventJsonMapper;
import dev.rma.returns.adapter.out.persistence.mappers.RmaStateJsonMapper;
import dev.rma.returns.application.RebuildRmaProjectionService;
import dev.rma.returns.application.ReturnHistoryService;
import dev.rma.returns.application.ReturnQueryService;
import dev.rma.returns.application.model.ReturnQuery;
import dev.rma.returns.application.port.in.GetReturnHistoryUseCase;
import dev.rma.returns.application.port.in.GetReturnUseCase;
import dev.rma.returns.application.port.in.ListReturnsUseCase;
import dev.rma.returns.application.port.in.RebuildRmaProjectionUseCase;
import dev.rma.returns.application.port.out.EventStore;
import dev.rma.returns.domain.InspectionResult;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.RequestedItem;
import dev.rma.returns.domain.Rma;
import dev.rma.returns.domain.enums.ClosureReason;
import dev.rma.returns.domain.enums.FailureCode;
import dev.rma.returns.domain.enums.ItemGrade;
import dev.rma.returns.domain.enums.RmaReason;
import dev.rma.returns.domain.events.DomainEvent;
import dev.rma.returns.domain.events.RmaRequested;
import dev.rma.returns.domain.policy.InspectionPolicy;
import dev.rma.returns.domain.policy.RefundPolicy;

@Testcontainers
@SpringJUnitConfig(RebuildRmaProjectionIT.TestConfig.class)
class RebuildRmaProjectionIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    RebuildRmaProjectionUseCase rebuild;

    @Autowired
    EventStore eventStore;

    @Autowired
    GetReturnUseCase getReturn;

    @Autowired
    ListReturnsUseCase listReturns;

    @Autowired
    GetReturnHistoryUseCase getHistory;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("adminJdbcTemplate")
    JdbcTemplate admin;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearTestDatabase() {
        admin.update("DELETE FROM returns.rma_snapshots");
        admin.update("DELETE FROM returns.rma_events");
        admin.update("DELETE FROM returns.rma_projection");
    }

    @Test
    void shouldRebuildMissingProjectionAsReturnsAppWithoutChangingHistory() {
        RmaRequested requested = requested();
        var label = Rma.fromHistory(List.of(requested))
                .issueLabel(
                        "label", "tracking", "label.pdf", requested.occurredAt().plusSeconds(60));
        eventStore.append(requested.rmaId(), 0, List.of(requested, label));
        jdbcTemplate.update("DELETE FROM returns.rma_projection");

        rebuild.rebuild();

        assertThat(jdbcTemplate.queryForObject("SELECT current_user", String.class))
                .isEqualTo("returns_app");
        var row = jdbcTemplate.queryForMap("SELECT * FROM returns.rma_projection");
        assertThat(row.get("rma_id")).isEqualTo(requested.rmaId());
        assertThat(row.get("business_number")).isEqualTo(requested.businessNumber());
        assertThat(row.get("status")).isEqualTo("LABEL_ISSUED");
        assertThat(row.get("refund_amount")).isNull();
        assertThat(row.get("currency")).isEqualTo("PLN");
        assertThat(((java.sql.Timestamp) row.get("updated_at")).toInstant()).isEqualTo(label.occurredAt());
        assertThat(eventStore.load(requested.rmaId()).orElseThrow().version()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT has_table_privilege(current_user, 'returns.rma_events', 'DELETE')", Boolean.class))
                .isFalse();
    }

    @Test
    void shouldRestorePreviousProjectionWhenReplayFails() {
        RmaRequested requested = requested();
        var label = Rma.fromHistory(List.of(requested))
                .issueLabel(
                        "label", "tracking", "label.pdf", requested.occurredAt().plusSeconds(60));
        eventStore.append(requested.rmaId(), 0, List.of(requested, label));
        var before = jdbcTemplate.queryForList("SELECT * FROM returns.rma_projection");
        admin.update("UPDATE returns.rma_events SET type = 'UnknownTestEvent' WHERE version = 2");

        assertThatThrownBy(() -> rebuild.rebuild()).isInstanceOf(IllegalArgumentException.class);

        assertThat(jdbcTemplate.queryForList("SELECT * FROM returns.rma_projection"))
                .isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM returns.rma_events", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void shouldRebuildEmptyHistory() {
        rebuild.rebuild();

        assertThat(jdbcTemplate.queryForList("SELECT * FROM returns.rma_projection"))
                .isEmpty();
    }

    @Test
    void shouldRebuildIdenticalProjectionForReturnsInDifferentStates() {
        RmaRequested requested = requested();
        eventStore.append(requested.rmaId(), 0, List.of(requested));

        RmaRequested labeled = requested();
        eventStore.append(
                labeled.rmaId(),
                0,
                List.of(
                        labeled,
                        Rma.fromHistory(List.of(labeled))
                                .issueLabel(
                                        "label",
                                        "tracking",
                                        "label.pdf",
                                        labeled.occurredAt().plusSeconds(60))));

        persistHistory(inspectionHistory(ItemGrade.A));
        persistHistory(inspectionHistory(ItemGrade.C));
        persistHistory(inspectionHistory(ItemGrade.D));

        List<DomainEvent> failedHistory = inspectionHistory(ItemGrade.A);
        Rma failed = Rma.fromHistory(failedHistory);
        failedHistory.add(failed.failRefund(
                FailureCode.TIMEOUT,
                "timeout",
                1,
                true,
                failedHistory.getLast().occurredAt().plusSeconds(60)));
        persistHistory(failedHistory);

        List<DomainEvent> closedHistory = inspectionHistory(ItemGrade.A);
        Rma closed = Rma.fromHistory(closedHistory);
        Instant paidAt = closedHistory.getLast().occurredAt().plusSeconds(60);
        closedHistory.add(closed.executeRefund(
                "payout", new Money(new BigDecimal("85.00"), Currency.getInstance("PLN")), 1, paidAt));
        closedHistory.add(closed.close(ClosureReason.REFUND_COMPLETED, paidAt.plusSeconds(60)));
        persistHistory(closedHistory);

        String comparisonSql =
                """
                SELECT rma_id, business_number, status, refund_amount, currency
                FROM returns.rma_projection
                ORDER BY rma_id
                """;
        var before = jdbcTemplate.queryForList(comparisonSql);
        assertThat(before).hasSize(7);
        assertThat(before)
                .extracting(row -> row.get("status"))
                .containsExactlyInAnyOrder(
                        "REQUESTED",
                        "LABEL_ISSUED",
                        "REFUND_PENDING",
                        "REFUND_PENDING",
                        "REJECTED",
                        "REFUND_FAILED",
                        "CLOSED");

        assertThat(jdbcTemplate.update("DELETE FROM returns.rma_projection")).isEqualTo(7);
        assertThat(jdbcTemplate.queryForList(comparisonSql)).isEmpty();

        rebuild.rebuild();

        assertThat(jdbcTemplate.queryForList(comparisonSql)).containsExactlyElementsOf(before);
    }

    private void persistHistory(List<DomainEvent> history) {
        eventStore.append(history.getFirst().rmaId(), 0, history);
    }

    private static List<DomainEvent> inspectionHistory(ItemGrade grade) {
        RmaRequested requested = requested();
        List<DomainEvent> history = new ArrayList<>(List.of(requested));
        Rma rma = Rma.fromHistory(history);
        Instant at = requested.occurredAt();
        history.add(rma.issueLabel("label", "tracking", "label.pdf", at.plusSeconds(60)));
        history.add(rma.markParcelInTransit(at.plusSeconds(120)));
        history.add(rma.receive(at.plusSeconds(180), "warehouse"));
        history.add(rma.startInspection("inspector", at.plusSeconds(240)));
        history.addAll(rma.completeInspection(
                "inspector",
                at.plusSeconds(300),
                List.of(new InspectionResult("item", grade, grade == ItemGrade.D ? "Damaged" : null, List.of())),
                null,
                new InspectionPolicy(new BigDecimal("500.00")),
                new RefundPolicy(0, 15, 40, new BigDecimal("15.00"))));
        return history;
    }

    @Test
    void shouldBlockAppendUntilRebuildCommitsAndThenKeepItsChanges() throws Exception {
        RmaRequested requested = requested();
        eventStore.append(requested.rmaId(), 0, List.of(requested));
        var label = Rma.fromHistory(List.of(requested))
                .issueLabel(
                        "label", "tracking", "label.pdf", requested.occurredAt().plusSeconds(60));
        CountDownLatch rebuilt = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var rebuilding =
                    executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        rebuild.rebuild();
                        rebuilt.countDown();
                        await(commit);
                    }));
            try {
                assertThat(rebuilt.await(10, TimeUnit.SECONDS)).isTrue();
                var writing = executor.submit(() -> eventStore.append(requested.rmaId(), 1, List.of(label)));
                awaitBlockedLock("ShareLock");
                assertThat(writing.isDone()).isFalse();
                commit.countDown();
                rebuilding.get(10, TimeUnit.SECONDS);
                writing.get(10, TimeUnit.SECONDS);
            } finally {
                commit.countDown();
            }
        }
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM returns.rma_projection", String.class))
                .isEqualTo("LABEL_ISSUED");
        assertThat(eventStore.load(requested.rmaId()).orElseThrow().version()).isEqualTo(2);
    }

    @Test
    void shouldWaitForInFlightAppendAndIncludeItsCommittedEvents() throws Exception {
        RmaRequested requested = requested();
        CountDownLatch appended = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writing =
                    executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        eventStore.append(requested.rmaId(), 0, List.of(requested));
                        appended.countDown();
                        await(commit);
                    }));
            try {
                assertThat(appended.await(10, TimeUnit.SECONDS)).isTrue();
                var rebuilding = executor.submit(() -> rebuild.rebuild());
                awaitBlockedLock("ExclusiveLock");
                assertThat(rebuilding.isDone()).isFalse();
                commit.countDown();
                writing.get(10, TimeUnit.SECONDS);
                rebuilding.get(10, TimeUnit.SECONDS);
            } finally {
                commit.countDown();
            }
        }
        assertThat(jdbcTemplate.queryForObject("SELECT rma_id FROM returns.rma_projection", UUID.class))
                .isEqualTo(requested.rmaId());
    }

    @Test
    void shouldReadCompleteHistoryBeforeAndAfterSnapshotWithoutProjection() {
        List<DomainEvent> events = inspectionHistory(ItemGrade.A);
        var rma = Rma.fromHistory(events);
        Instant at = events.getLast().occurredAt();
        while (events.size() < 51) {
            at = at.plusSeconds(60);
            events.add(
                    rma.failRefund(FailureCode.TIMEOUT, "timeout", rma.state().payoutAttempt(), true, at));
            at = at.plusSeconds(60);
            events.add(rma.retryRefund(at));
        }
        eventStore.append(rma.rmaId(), 0, events);
        int snapshotVersion = events.size();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT version FROM returns.rma_snapshots WHERE rma_id = ?", Integer.class, rma.rmaId()))
                .isEqualTo(snapshotVersion);
        var lastEvent =
                rma.failRefund(FailureCode.TIMEOUT, "timeout", rma.state().payoutAttempt(), true, at.plusSeconds(60));
        eventStore.append(rma.rmaId(), snapshotVersion, List.of(lastEvent));
        events.add(lastEvent);
        // Historia musi działać również bez tabeli z bieżącym stanem zwrotu.
        jdbcTemplate.update("DELETE FROM returns.rma_projection");

        var history = getHistory.history(rma.businessNumber());

        assertThat(history).hasSize(events.size());
        assertThat(history).extracting(entry -> entry.event()).containsExactlyElementsOf(events);
        assertThat(history)
                .extracting(entry -> entry.version())
                .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, events.size())
                        .boxed()
                        .toList());
        assertThat(history).extracting(entry -> entry.eventId()).doesNotHaveDuplicates();
        assertThat(history)
                .extracting(entry -> entry.eventId())
                .containsExactlyElementsOf(jdbcTemplate.queryForList(
                        "SELECT event_id FROM returns.rma_events WHERE rma_id = ? ORDER BY version",
                        UUID.class,
                        rma.rmaId()));
        assertThat(history).allSatisfy(entry -> {
            assertThat(entry.rmaId()).isEqualTo(rma.rmaId());
            assertThat(entry.occurredAt()).isEqualTo(entry.event().occurredAt());
        });
    }

    @Test
    void shouldReturnSameDetailsListAndHistoryAfterProjectionRebuild() {
        var events = inspectionHistory(ItemGrade.A);
        persistHistory(events);
        String number = ((RmaRequested) events.getFirst()).businessNumber();
        var detailsBefore = getReturn.get(number);
        var listBefore = listReturns.list(new ReturnQuery(0, 20, null));
        var historyBefore = getHistory.history(number);
        jdbcTemplate.update("DELETE FROM returns.rma_projection");

        rebuild.rebuild();

        assertThat(getReturn.get(number)).isEqualTo(detailsBefore);
        assertThat(listReturns.list(new ReturnQuery(0, 20, null))).isEqualTo(listBefore);
        assertThat(getHistory.history(number)).isEqualTo(historyBefore);
    }

    private void awaitBlockedLock(String mode) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean waiting = admin.queryForObject(
                    """
                    SELECT EXISTS (
                        SELECT 1 FROM pg_locks
                        WHERE locktype = 'advisory' AND classid = 1380794624 AND objid = 1
                            AND mode = ? AND NOT granted
                    )
                    """,
                    Boolean.class,
                    mode);
            if (Boolean.TRUE.equals(waiting)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Expected blocked " + mode + " for projection rebuild");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for commit");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static RmaRequested requested() {
        UUID id = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-01T12:00:00Z");
        Currency currency = Currency.getInstance("PLN");
        return Rma.request(
                id,
                "RMA-2026-" + id,
                "order",
                "customer",
                at.minusSeconds(86400),
                RmaReason.CHANGED_MIND,
                null,
                List.of(new RequestedItem("item", "sku", "Book", 1, new Money(new BigDecimal("100.00"), currency))),
                currency,
                at,
                14);
    }

    @Test
    void shouldPreserveEuroCurrencyOnCreationAndRebuild() {
        var original = requested();
        var euro = Currency.getInstance("EUR");
        var request = new RmaRequested(
                original.rmaId(),
                original.businessNumber(),
                original.orderId(),
                original.customerId(),
                original.orderDeliveredAt(),
                original.requestedAt(),
                original.reason(),
                original.customerNote(),
                euro,
                List.of(new RequestedItem("item", "sku", "Book", 1, new Money(new BigDecimal("100.00"), euro))));
        eventStore.append(request.rmaId(), 0, List.of(request));
        assertThat(jdbcTemplate.queryForObject("SELECT currency FROM returns.rma_projection", String.class))
                .isEqualTo("EUR");
        jdbcTemplate.update("DELETE FROM returns.rma_projection");
        rebuild.rebuild();
        var row = jdbcTemplate.queryForMap("SELECT currency, refund_amount FROM returns.rma_projection");
        assertThat(row.get("currency")).isEqualTo("EUR");
        assertThat(row.get("refund_amount")).isNull();
    }

    @Configuration
    @EnableTransactionManagement
    @Import({
        PostgresEventStore.class,
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
