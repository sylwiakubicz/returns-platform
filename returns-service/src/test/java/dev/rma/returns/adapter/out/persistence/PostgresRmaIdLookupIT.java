package dev.rma.returns.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.RequestedItem;
import dev.rma.returns.domain.enums.RmaReason;
import dev.rma.returns.domain.events.RmaRequested;

@Testcontainers
class PostgresRmaIdLookupIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate admin;
    private static JdbcTemplate app;
    private static PostgresRmaIdLookup lookup;
    private static final String NUMBER = "RMA-2026-000001";

    @BeforeAll
    static void prepareDatabase() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        admin = new JdbcTemplate(source);
        admin.execute("CREATE ROLE returns_app LOGIN PASSWORD 'test-password'");
        admin.execute("CREATE SCHEMA returns");
        admin.execute("GRANT USAGE ON SCHEMA returns TO returns_app");
        admin.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA returns "
                + "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO returns_app");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__create_returns_schema.sql"))
                .execute(source);
        app = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "returns_app", "test-password"));
        lookup = new PostgresRmaIdLookup(app);
    }

    @BeforeEach
    void clearHistory() {
        admin.update("DELETE FROM returns.rma_events");
    }

    @Test
    void findsIdFromActualEventPayloadWithoutProjection() throws Exception {
        var id = UUID.randomUUID();
        insertRequest(id, NUMBER);
        insertRequest(UUID.randomUUID(), "RMA-2026-000002");

        assertThat(lookup.findId(NUMBER)).contains(id);
        assertThat(app.queryForObject("SELECT count(*) FROM returns.rma_projection", Integer.class))
                .isZero();
    }

    @Test
    void returnsEmptyForUnknownBusinessNumber() throws Exception {
        insertRequest(UUID.randomUUID(), NUMBER);
        assertThat(lookup.findId("RMA-2026-999999")).isEmpty();
    }

    @Test
    void treatsParameterAsLiteralText() throws Exception {
        insertRequest(UUID.randomUUID(), NUMBER);
        assertThat(lookup.findId("' OR 1=1 --")).isEmpty();
    }

    @Test
    void rejectsAmbiguousBusinessNumberInsteadOfChoosingAnArbitraryReturn() throws Exception {
        insertRequest(UUID.randomUUID(), NUMBER);
        insertRequest(UUID.randomUUID(), NUMBER);
        assertThatThrownBy(() -> lookup.findId(NUMBER)).isInstanceOf(IncorrectResultSizeDataAccessException.class);
    }

    private void insertRequest(UUID id, String number) throws Exception {
        var currency = Currency.getInstance("PLN");
        var now = Instant.parse("2026-10-21T10:00:00Z");
        var event = new RmaRequested(
                id,
                number,
                "order",
                "customer",
                now.minusSeconds(60),
                now,
                RmaReason.CHANGED_MIND,
                null,
                currency,
                List.of(new RequestedItem("item", "sku", "Item", 1, new Money(new BigDecimal("100.00"), currency))));
        String payload = Jackson2ObjectMapperBuilder.json().build().writeValueAsString(event);
        app.update(
                """
                INSERT INTO returns.rma_events
                    (event_id, rma_id, version, type, payload, schema_version, occurred_at)
                VALUES (?, ?, 1, 'RmaRequested', ?::jsonb, 1, ?)
                """,
                UUID.randomUUID(),
                id,
                payload,
                Timestamp.from(now));
    }
}
