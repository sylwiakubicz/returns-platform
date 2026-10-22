package dev.rma.returns.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.rma.returns.adapter.in.web.ReturnApiExceptionHandler;
import dev.rma.returns.adapter.in.web.ReturnQueryController;
import dev.rma.returns.application.ReturnQueryService;
import dev.rma.returns.application.exception.ReturnNotFoundException;
import dev.rma.returns.application.model.ReturnQuery;
import dev.rma.returns.application.model.ReturnView;
import dev.rma.returns.application.port.in.GetReturnUseCase;
import dev.rma.returns.application.port.in.ListReturnsUseCase;
import dev.rma.returns.application.port.out.RmaProjectionReader;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.enums.RmaStatus;

@Testcontainers
@SpringJUnitConfig(PostgresRmaProjectionReaderIT.TestConfig.class)
class PostgresRmaProjectionReaderIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String NUMBER = "RMA-2026-000001";
    private static final Instant UPDATED_AT = Instant.parse("2026-10-21T10:00:00.123456Z");

    @Autowired
    CountingJdbcTemplate jdbc;

    @Autowired
    RmaProjectionReader reader;

    @Autowired
    GetReturnUseCase getReturn;

    @Autowired
    ListReturnsUseCase listReturns;

    @Autowired
    ReturnQueryController controller;

    @Autowired
    ReturnApiExceptionHandler exceptionHandler;

    private MockMvc mvc;

    @BeforeEach
    void clearProjection() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .build();
        jdbc.afterCount = null;
        jdbc.update("DELETE FROM returns.rma_projection");
    }

    @Test
    void readsExactAmountCurrencyStatusAndStoredTimestamp() {
        insert(NUMBER, "REFUNDED", new BigDecimal("123.45"), "EUR");
        insert("RMA-2026-000002", "REQUESTED", null, "PLN");

        assertThat(reader.findByBusinessNumber(NUMBER))
                .contains(new ReturnView(
                        NUMBER,
                        RmaStatus.REFUNDED,
                        new Money(new BigDecimal("123.45"), Currency.getInstance("EUR")),
                        UPDATED_AT));
    }

    @Test
    void keepsMissingRefundDecisionAsNull() {
        insert(NUMBER, "REQUESTED", null, "PLN");
        assertThat(reader.findByBusinessNumber(NUMBER))
                .contains(new ReturnView(NUMBER, RmaStatus.REQUESTED, null, UPDATED_AT));
    }

    @Test
    void distinguishesZeroRefundFromMissingDecision() {
        insert(NUMBER, "REJECTED", new BigDecimal("0.00"), "PLN");
        assertThat(reader.findByBusinessNumber(NUMBER))
                .contains(new ReturnView(
                        NUMBER,
                        RmaStatus.REJECTED,
                        new Money(new BigDecimal("0.00"), Currency.getInstance("PLN")),
                        UPDATED_AT));
    }

    @Test
    void returnsEmptyForUnknownNumber() {
        insert(NUMBER, "REQUESTED", null, "PLN");
        assertThat(reader.findByBusinessNumber("RMA-2026-999999")).isEmpty();
    }

    @Test
    void treatsBusinessNumberAsLiteralText() {
        insert(NUMBER, "REQUESTED", null, "PLN");
        assertThat(reader.findByBusinessNumber("' OR 1=1 --")).isEmpty();
    }

    @Test
    void paginatesInDescendingBusinessNumberOrder() {
        insert(NUMBER, "REQUESTED", null, "PLN");
        insert("RMA-2026-000003", "REQUESTED", null, "PLN");
        insert("RMA-2026-000002", "REFUNDED", new BigDecimal("12.34"), "EUR");
        var first = reader.findAll(new ReturnQuery(0, 2, null));
        assertThat(first.content())
                .extracting(ReturnView::businessNumber)
                .containsExactly("RMA-2026-000003", "RMA-2026-000002");
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.content().get(1).refundAmount())
                .isEqualTo(new Money(new BigDecimal("12.34"), Currency.getInstance("EUR")));
        assertThat(first.content())
                .allSatisfy(view -> assertThat(view.updatedAt()).isEqualTo(UPDATED_AT));
        var second = reader.findAll(new ReturnQuery(1, 2, null));
        assertThat(second.content()).extracting(ReturnView::businessNumber).containsExactly(NUMBER);
        assertThat(second.totalElements()).isEqualTo(3);
        assertThat(second.totalPages()).isEqualTo(2);
    }

    @Test
    void countsOnlyReturnsMatchingStatusFilter() {
        insert(NUMBER, "REQUESTED", null, "PLN");
        insert("RMA-2026-000002", "REFUNDED", new BigDecimal("10.00"), "EUR");
        insert("RMA-2026-000003", "REQUESTED", null, "PLN");
        var page = reader.findAll(new ReturnQuery(1, 1, RmaStatus.REQUESTED));
        assertThat(page.content()).extracting(ReturnView::businessNumber).containsExactly(NUMBER);
        assertThat(page.content()).allSatisfy(view -> assertThat(view.status()).isEqualTo(RmaStatus.REQUESTED));
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void returnsZeroTotalsWhenNothingMatchesFilter() {
        insert(NUMBER, "REQUESTED", null, "PLN");
        var page = reader.findAll(new ReturnQuery(0, 20, RmaStatus.CLOSED));
        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void returnsEmptyPageButPreservesTotalsBeyondLastPage() {
        insert(NUMBER, "REQUESTED", null, "PLN");
        var page = reader.findAll(new ReturnQuery(Integer.MAX_VALUE, 100, null));
        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void returnsEmptyPageForEmptyTable() {
        var page = reader.findAll(new ReturnQuery(0, 20, null));
        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void readsDetailsAndListThroughSpringManagedServices() {
        insert(NUMBER, "REQUESTED", null, "PLN");
        assertThat(getReturn.get(NUMBER)).isEqualTo(new ReturnView(NUMBER, RmaStatus.REQUESTED, null, UPDATED_AT));
        assertThat(listReturns.list(new ReturnQuery(0, 20, null)).content()).containsExactly(getReturn.get(NUMBER));
        assertThatThrownBy(() -> getReturn.get("RMA-2026-999999")).isInstanceOf(ReturnNotFoundException.class);
    }

    @Test
    void countAndPageSeeTheSameDataEvenIfAnotherTransactionAddsReturnBetweenQueries() {
        insert(NUMBER, "REQUESTED", null, "PLN");
        jdbc.afterCount = () -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                    .isTrue();
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> insert("RMA-2026-000002", "REQUESTED", null, "PLN"))
                        .get(10, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException("Concurrent insertion failed", exception);
            }
        };
        var page = listReturns.list(new ReturnQuery(0, 20, null));
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).extracting(ReturnView::businessNumber).containsExactly(NUMBER);
        // Kolejny odczyt widzi już nowy zwrot.
        assertThat(listReturns.list(new ReturnQuery(0, 20, null)).totalElements())
                .isEqualTo(2);
    }

    @Test
    void httpReadsDetailsWithDecimalStringCurrencyAndOriginalTimestamp() throws Exception {
        insert(NUMBER, "REFUNDED", new BigDecimal("123.45"), "EUR");
        mvc.perform(get("/api/v1/returns/{number}", NUMBER))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.businessNumber").value(NUMBER))
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refundAmount.amount").isString())
                .andExpect(jsonPath("$.refundAmount.amount").value("123.45"))
                .andExpect(jsonPath("$.refundAmount.currency").value("EUR"))
                .andExpect(jsonPath("$.updatedAt").value(UPDATED_AT.toString()));
    }

    @Test
    void httpDistinguishesMissingDecisionFromZeroRefund() throws Exception {
        insert(NUMBER, "REQUESTED", null, "PLN");
        insert("RMA-2026-000002", "REJECTED", new BigDecimal("0.00"), "EUR");
        mvc.perform(get("/api/v1/returns/{number}", NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundAmount").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/v1/returns/RMA-2026-000002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundAmount.amount").value("0.00"))
                .andExpect(jsonPath("$.refundAmount.currency").value("EUR"));
    }

    @Test
    void httpUsesDefaultPaginationAndDescendingBusinessNumberOrder() throws Exception {
        insert(NUMBER, "REQUESTED", null, "PLN");
        insert("RMA-2026-000003", "REQUESTED", null, "PLN");
        insert("RMA-2026-000002", "REFUNDED", new BigDecimal("10.00"), "EUR");
        mvc.perform(get("/api/v1/returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[*].businessNumber")
                        .value(org.hamcrest.Matchers.contains("RMA-2026-000003", "RMA-2026-000002", NUMBER)))
                .andExpect(jsonPath("$.content[1].refundAmount.amount").value("10.00"))
                .andExpect(jsonPath("$.content[0].updatedAt").value(UPDATED_AT.toString()));
    }

    @Test
    void httpFiltersAndPaginatesReturnsAndCountsOnlyMatches() throws Exception {
        insert(NUMBER, "REQUESTED", null, "PLN");
        insert("RMA-2026-000002", "REFUNDED", new BigDecimal("10.00"), "EUR");
        insert("RMA-2026-000003", "REQUESTED", null, "PLN");
        mvc.perform(get("/api/v1/returns").param("page", "1").param("size", "1").param("status", "REQUESTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].businessNumber").value(NUMBER))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void httpReturnsEmptyPageRatherThan404WhenNothingMatches() throws Exception {
        insert(NUMBER, "REQUESTED", null, "PLN");
        mvc.perform(get("/api/v1/returns").param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void httpReturns404ForUnknownNumberAnd400ForMalformedNumber() throws Exception {
        mvc.perform(get("/api/v1/returns/RMA-2026-999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RETURN_NOT_FOUND"));
        mvc.perform(get("/api/v1/returns/" + UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("businessNumber"));
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,abc", "page,2147483648", "size,0", "size,101", "size,1.5", "status,UNKNOWN"})
    void httpRejectsInvalidQueryParameters(String parameter, String value) throws Exception {
        mvc.perform(get("/api/v1/returns").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value(parameter));
    }

    @Test
    void httpAcceptsMaximumPageSizeAndLargePageIndex() throws Exception {
        insert(NUMBER, "REQUESTED", null, "PLN");
        mvc.perform(get("/api/v1/returns").param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    private void insert(String number, String status, BigDecimal amount, String currency) {
        jdbc.update(
                """
                INSERT INTO returns.rma_projection
                    (rma_id, business_number, status, refund_amount, currency, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                number,
                status,
                amount,
                currency,
                Timestamp.from(UPDATED_AT));
    }
    /** Pozwala w teście wstawić drugi zwrot dokładnie pomiędzy COUNT i odczytem strony. */
    static class CountingJdbcTemplate extends JdbcTemplate {
        Runnable afterCount;

        CountingJdbcTemplate(DataSource source) {
            super(source);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            T result = super.queryForObject(sql, type, args);
            if (sql.startsWith("SELECT count(*)") && afterCount != null) {
                Runnable action = afterCount;
                afterCount = null;
                action.run();
            }
            return result;
        }
    }

    @Configuration
    @EnableTransactionManagement
    @Import({
        PostgresRmaProjectionReader.class,
        ReturnQueryService.class,
        ReturnQueryController.class,
        ReturnApiExceptionHandler.class
    })
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            var source =
                    new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            var admin = new JdbcTemplate(source);
            admin.execute("CREATE ROLE returns_app LOGIN PASSWORD 'test-password'");
            admin.execute("CREATE SCHEMA returns");
            admin.execute("GRANT USAGE ON SCHEMA returns TO returns_app");
            admin.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA returns "
                    + "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO returns_app");
            new ResourceDatabasePopulator(
                            new ClassPathResource("db/migration/V1__create_returns_schema.sql"),
                            new ClassPathResource("db/migration/V2__allow_null_refund_amount_in_rma_projection.sql"),
                            new ClassPathResource("db/migration/V3__add_currency_to_rma_projection.sql"))
                    .execute(source);
            return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "returns_app", "test-password");
        }

        @Bean
        CountingJdbcTemplate jdbcTemplate(DataSource source) {
            return new CountingJdbcTemplate(source);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }
}
