package dev.rma.returns.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.rma.returns.application.port.out.EventStore;
import dev.rma.returns.application.exception.ConcurrencyConflictException;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.RequestedItem;
import dev.rma.returns.domain.Rma;
import dev.rma.returns.domain.enums.RmaReason;
import dev.rma.returns.domain.enums.RmaStatus;
import dev.rma.returns.domain.events.DomainEvent;
import dev.rma.returns.domain.events.RmaRequested;

@Testcontainers
@SpringJUnitConfig(PostgresEventStoreIT.TestConfig.class)
class PostgresEventStoreIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    EventStore eventStore;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistThreeEventsAndRestoreRma() {
        UUID rmaId = UUID.randomUUID();
        RmaRequested requested = requested(rmaId);
        Instant requestedAt = requested.requestedAt();

        Rma original = Rma.fromHistory(List.of(requested));
        List<DomainEvent> events = List.of(
            requested,
            original.issueLabel(
                "label", "tracking", "label.pdf",
                requestedAt.plusSeconds(60)),
            original.schedulePickup(
                "pickup", LocalDate.of(2026, 10, 3),
                requestedAt.plusSeconds(120)));
//        events   = [RmaRequested, LabelIssued, PickupScheduled]
//        original = status PICKUP_SCHEDULED, version 3


        eventStore.append(rmaId, 0, events);

        Rma restored = eventStore.load(rmaId).orElseThrow();
        assertThat(restored.status()).isEqualTo(RmaStatus.PICKUP_SCHEDULED);
        assertThat(restored.version()).isEqualTo(3);
        assertThat(restored).usingRecursiveComparison().isEqualTo(original);
        assertThat(jdbcTemplate.queryForList(
                "SELECT version FROM returns.rma_events WHERE rma_id = ? ORDER BY version", Integer.class, rmaId))
                .containsExactly(1, 2, 3);
    }

    @Test
    void shouldReturnEmptyForUnknownRma() {
        assertThat(eventStore.load(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldRejectStaleVersionWithoutChangingHistory() {
        UUID rmaId = UUID.randomUUID();
        RmaRequested requested = requested(rmaId);
        eventStore.append(rmaId, 0, List.of(requested));

        assertThatThrownBy(() -> eventStore.append(rmaId, 0, List.of(requested)))
                .isInstanceOf(ConcurrencyConflictException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
        assertThat(eventStore.load(rmaId).orElseThrow().version()).isEqualTo(1);
    }

    @RepeatedTest(20)
    void shouldAllowExactlyOneConcurrentWriter() throws Exception {
        UUID rmaId = UUID.randomUUID();
        RmaRequested requested = requested(rmaId);
        eventStore.append(rmaId, 0, List.of(requested));
        DomainEvent label = Rma.fromHistory(List.of(requested))
                .issueLabel("label", "tracking", "label.pdf", requested.requestedAt());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> writer = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent writers");
                }
                try {
                    eventStore.append(rmaId, 1, List.of(label));
                    return true;
                } catch (ConcurrencyConflictException exception) {
                    return false;
                }
            };
            var first = executor.submit(writer);
            var second = executor.submit(writer);
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(eventStore.load(rmaId).orElseThrow().version()).isEqualTo(2);
    }

    @Test
    void shouldRollbackWholeAppendAndPreserveOtherIntegrityErrors() {
        UUID rmaId = UUID.randomUUID();
        RmaRequested requested = requested(rmaId);
        eventStore.append(rmaId, 0, List.of(requested));
        Rma rma = Rma.fromHistory(List.of(requested));
        List<DomainEvent> events = List.of(
                rma.issueLabel("label", "tracking", "label.pdf", requested.requestedAt()),
                rma.schedulePickup("pickup", LocalDate.of(2026, 10, 3), requested.requestedAt()));
        // Wyłącznie testowe ograniczenie: wymuszamy błąd drugiego INSERT-a.
        jdbcTemplate.execute("ALTER TABLE returns.rma_events ADD CONSTRAINT test_reject_pickup CHECK ("
                + "rma_id <> '" + rmaId + "'::uuid OR type <> 'PickupScheduled')");
        try {
            assertThatThrownBy(() -> eventStore.append(rmaId, 1, events))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbcTemplate.queryForList(
                    "SELECT version FROM returns.rma_events WHERE rma_id = ? ORDER BY version",
                    Integer.class, rmaId)).containsExactly(1);
        } finally {
            jdbcTemplate.execute("ALTER TABLE returns.rma_events DROP CONSTRAINT test_reject_pickup");
        }
    }

    private static RmaRequested requested(UUID rmaId) {
        Instant at = Instant.parse("2026-10-01T12:00:00Z");
        Currency currency = Currency.getInstance("PLN");
        return Rma.request(rmaId, "RMA-2026-000001", "order", "customer", at.minusSeconds(86400),
                RmaReason.CHANGED_MIND, null,
                List.of(new RequestedItem("item", "sku", "Hobbit", 1,
                        new Money(new BigDecimal("100.00"), currency))),
                currency, at, 14);
    }

    @Configuration
    @EnableTransactionManagement
    @Import({PostgresEventStore.class, EventJsonMapper.class})
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DataSource dataSource = new DriverManagerDataSource(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            // Migracja zakłada, że infrastruktura utworzyła już rolę i schemat.
            jdbc.execute("CREATE ROLE returns_app");
            jdbc.execute("CREATE SCHEMA returns");
            new ResourceDatabasePopulator(new ClassPathResource(
                    "db/migration/V1__create_returns_schema.sql")).execute(dataSource);
            return dataSource;
        }

        @Bean
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
