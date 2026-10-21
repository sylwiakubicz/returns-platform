package dev.rma.returns.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.rma.returns.application.model.IdempotencyRecord;
import dev.rma.returns.application.port.out.IdempotencyStore;

@Testcontainers
@SpringJUnitConfig(PostgresIdempotencyStoreIT.TestConfig.class)
class PostgresIdempotencyStoreIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ENDPOINT = "/api/v1/returns";
    private static final Instant CREATED_AT = Instant.parse("2026-10-21T10:00:00Z");

    @Autowired
    IdempotencyStore store;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void clearEntries() {
        jdbc.update("DELETE FROM returns.idempotency_keys");
        transaction = new TransactionTemplate(transactionManager);
    }

    private IdempotencyRecord record(String number) {
        return new IdempotencyRecord("a".repeat(64), 201, "{\"businessNumber\":\"" + number + "\"}", CREATED_AT);
    }

    @Test
    void storesAndReadsHashStatusJsonAndTimestamp() throws Exception {
        var expected = record("RMA-2026-000001");
        transaction.executeWithoutResult(status -> store.save("key-1", ENDPOINT, expected));
        var actual = store.find("key-1", ENDPOINT).orElseThrow();
        assertThat(actual.requestHash()).isEqualTo(expected.requestHash());
        assertThat(actual.responseStatus()).isEqualTo(201);
        assertThat(actual.createdAt()).isEqualTo(CREATED_AT);
        var mapper = new ObjectMapper();
        assertThat(mapper.readTree(actual.responseBody())).isEqualTo(mapper.readTree(expected.responseBody()));
    }

    @Test
    void scopesKeyToEndpoint() {
        transaction.executeWithoutResult(status -> {
            store.save("same-key", ENDPOINT, record("RMA-2026-000001"));
            store.save("same-key", "/another-endpoint", record("RMA-2026-000002"));
        });
        assertThat(store.find("same-key", ENDPOINT).orElseThrow().responseBody())
                .contains("RMA-2026-000001");
        assertThat(store.find("same-key", "/another-endpoint").orElseThrow().responseBody())
                .contains("RMA-2026-000002");
        assertThat(store.find("unknown", ENDPOINT)).isEmpty();
        assertThat(store.find("same-key", "/unknown-endpoint")).isEmpty();
    }

    @Test
    void rejectsDuplicateWithoutOverwritingOriginalResponse() {
        transaction.executeWithoutResult(status -> store.save("key", ENDPOINT, record("RMA-2026-000001")));
        assertThatThrownBy(() -> transaction.executeWithoutResult(
                        status -> store.save("key", ENDPOINT, record("RMA-2026-000002"))))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(store.find("key", ENDPOINT).orElseThrow().responseBody()).contains("RMA-2026-000001");
    }

    @Test
    void rollbackLeavesKeyAvailableForRetry() {
        transaction.executeWithoutResult(status -> {
            store.save("key", ENDPOINT, record("RMA-2026-000001"));
            status.setRollbackOnly();
        });
        assertThat(store.find("key", ENDPOINT)).isEmpty();
        transaction.executeWithoutResult(status -> store.save("key", ENDPOINT, record("RMA-2026-000002")));
        assertThat(store.find("key", ENDPOINT).orElseThrow().responseBody()).contains("RMA-2026-000002");
    }

    @Test
    void requiresTransactionForSaving() {
        assertThatThrownBy(() -> store.save("key", ENDPOINT, record("RMA-2026-000001")))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(store.find("key", ENDPOINT)).isEmpty();
    }

    @Configuration
    @EnableTransactionManagement
    @Import(PostgresIdempotencyStore.class)
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            var source =
                    new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            var admin = new JdbcTemplate(source);
            admin.execute("CREATE ROLE returns_app LOGIN PASSWORD 'test-password'");
            admin.execute("CREATE SCHEMA returns");
            admin.execute("GRANT USAGE ON SCHEMA returns TO returns_app");
            admin.execute(
                    "ALTER DEFAULT PRIVILEGES IN SCHEMA returns GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO returns_app");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__create_returns_schema.sql"))
                    .execute(source);
            return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "returns_app", "test-password");
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }
}
