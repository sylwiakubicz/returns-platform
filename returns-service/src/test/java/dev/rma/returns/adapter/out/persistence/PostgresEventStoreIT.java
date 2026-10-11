package dev.rma.returns.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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
        UUID rmaId = UUID.fromString("018f0000-0000-7000-8000-000000000001");
        Instant requestedAt = Instant.parse("2026-10-01T12:00:00Z");
        Currency currency = Currency.getInstance("PLN");
        RmaRequested requested = Rma.request(
                rmaId, "RMA-2026-000001", "order", "customer", requestedAt.minusSeconds(86400),
                RmaReason.CHANGED_MIND, null,
                List.of(new RequestedItem("item", "sku", "Hobbit", 1,
                        new Money(new BigDecimal("100.00"), currency))),
                currency, requestedAt, 14);
        Rma original = Rma.fromHistory(List.of(requested));
        List<DomainEvent> events = List.of(
                requested,
                original.issueLabel("label", "tracking", "label.pdf", requestedAt.plusSeconds(60)),
                original.schedulePickup("pickup", LocalDate.of(2026, 10, 3), requestedAt.plusSeconds(120)));

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
