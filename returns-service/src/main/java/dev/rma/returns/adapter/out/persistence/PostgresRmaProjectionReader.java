package dev.rma.returns.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.Optional;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.rma.returns.application.model.ReturnView;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.enums.RmaStatus;

public class PostgresRmaProjectionReader {
    private final JdbcTemplate jdbcTemplate;

    public PostgresRmaProjectionReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ReturnView> findByBusinessNumber(String businessNumber) {
        var rows = jdbcTemplate.query(
                """
                SELECT business_number, status, refund_amount, currency, updated_at
                FROM returns.rma_projection
                WHERE business_number = ?
                """,
                (row, rowNumber) -> toView(row),
                businessNumber);
        return DataAccessUtils.optionalResult(rows);
    }

    private ReturnView toView(ResultSet row) throws SQLException {
        var amount = row.getBigDecimal("refund_amount");
        var refundAmount = amount == null ? null : new Money(amount, Currency.getInstance(row.getString("currency")));
        return new ReturnView(
                row.getString("business_number"),
                RmaStatus.valueOf(row.getString("status")),
                refundAmount,
                row.getTimestamp("updated_at").toInstant());
    }
}
