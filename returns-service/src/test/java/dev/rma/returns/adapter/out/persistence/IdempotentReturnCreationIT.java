package dev.rma.returns.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.rma.returns.adapter.out.id.UuidV7RmaIdGenerator;
import dev.rma.returns.adapter.out.persistence.mappers.EventJsonMapper;
import dev.rma.returns.adapter.out.persistence.mappers.RmaStateJsonMapper;
import dev.rma.returns.application.IdempotentReturnCreationService;
import dev.rma.returns.application.RebuildRmaProjectionService;
import dev.rma.returns.application.ReturnCommandService;
import dev.rma.returns.application.ReturnHistoryService;
import dev.rma.returns.application.ReturnQueryService;
import dev.rma.returns.application.exception.IdempotencyKeyReuseException;
import dev.rma.returns.application.exception.InvalidReturnRequestException;
import dev.rma.returns.domain.exceptions.RmaDomainException;

@Testcontainers
@SpringJUnitConfig(IdempotentReturnCreationIT.TestConfig.class)
@TestPropertySource(properties = "returns.window-days=14")
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

    @Configuration
    @EnableTransactionManagement
    @Import({
        PostgresEventStore.class,
        IdempotentReturnCreationService.class,
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
