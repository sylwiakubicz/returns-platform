package dev.rma.returns.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
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

import dev.rma.returns.application.port.out.BusinessNumberGenerator;

@Testcontainers
@SpringJUnitConfig(PostgresBusinessNumberGeneratorIT.TestConfig.class)
class PostgresBusinessNumberGeneratorIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant YEAR_END = Instant.parse("2026-12-31T23:59:59Z");

    @Autowired
    BusinessNumberGenerator generator;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void resetCounter() {
        jdbcTemplate.update("DELETE FROM returns.rma_business_number_counters");
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void incrementsCounterAndStartsNewYearAtOne() {
        String first = transaction.execute(status -> generator.nextNumber(YEAR_END));
        String second = transaction.execute(status -> generator.nextNumber(YEAR_END));
        String nextYear = transaction.execute(status -> generator.nextNumber(YEAR_END.plusSeconds(1)));

        assertThat(first).isEqualTo("RMA-2026-000001");
        assertThat(second).isEqualTo("RMA-2026-000002");
        assertThat(nextYear).isEqualTo("RMA-2027-000001");
    }

    @Test
    void rollsBackCounterWithTheCallingTransaction() {
        transaction.executeWithoutResult(status -> {
            generator.nextNumber(YEAR_END);
            status.setRollbackOnly();
        });
        String number = transaction.execute(status -> generator.nextNumber(YEAR_END));
        assertThat(number).isEqualTo("RMA-2026-000001");
    }

    @Test
    void requiresAnExistingTransaction() {
        assertThatThrownBy(() -> generator.nextNumber(YEAR_END)).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM returns.rma_business_number_counters", Integer.class))
                .isZero();
    }

    @Test
    void rejectsCounterOverflow() {
        jdbcTemplate.update("INSERT INTO returns.rma_business_number_counters VALUES (2026, 999999)");
        assertThatThrownBy(() -> transaction.execute(status -> generator.nextNumber(YEAR_END)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT last_value FROM returns.rma_business_number_counters WHERE year = 2026", Integer.class))
                .isEqualTo(999999);
    }

    @Test
    void allocatesDifferentNumbersForConcurrentTransactions() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> createNumber = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent start");
                }
                return new TransactionTemplate(transactionManager).execute(status -> generator.nextNumber(YEAR_END));
            };
            var first = executor.submit(createNumber);
            var second = executor.submit(createNumber);
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("RMA-2026-000001", "RMA-2026-000002");
        }
    }

    @Configuration
    @EnableTransactionManagement
    @Import(PostgresBusinessNumberGenerator.class)
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            var adminSource =
                    new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            var admin = new JdbcTemplate(adminSource);
            admin.execute("CREATE ROLE returns_app LOGIN PASSWORD 'test-password'");
            admin.execute("CREATE SCHEMA returns");
            admin.execute("GRANT USAGE ON SCHEMA returns TO returns_app");
            admin.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA returns "
                    + "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO returns_app");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__create_returns_schema.sql"))
                    .execute(adminSource);
            return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "returns_app", "test-password");
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
