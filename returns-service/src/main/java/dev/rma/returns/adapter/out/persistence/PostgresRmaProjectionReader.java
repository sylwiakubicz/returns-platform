package dev.rma.returns.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Optional;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import dev.rma.returns.application.model.ReturnPage;
import dev.rma.returns.application.model.ReturnQuery;
import dev.rma.returns.application.model.ReturnView;
import dev.rma.returns.application.port.out.RmaProjectionReader;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.enums.RmaStatus;

public class PostgresRmaProjectionReader implements RmaProjectionReader {
    private final JdbcTemplate jdbcTemplate;

    public PostgresRmaProjectionReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
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

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReturnPage findAll(ReturnQuery query) {
        String filter = query.status() == null ? "" : " WHERE status = ?";
        var parameters = new ArrayList<Object>();
        if (query.status() != null) {
            parameters.add(query.status().name());
        }
        long totalElements = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM returns.rma_projection" + filter, Long.class, parameters.toArray());

        parameters.add(query.size());
        parameters.add(query.offset());
        var content = jdbcTemplate.query(
                """
                SELECT business_number, status, refund_amount, currency, updated_at
                FROM returns.rma_projection
                """
                        + filter + " ORDER BY business_number DESC LIMIT ? OFFSET ?",
                (row, rowNumber) -> toView(row),
                parameters.toArray());
        long totalPages = totalElements / query.size() + (totalElements % query.size() == 0 ? 0 : 1);
        return new ReturnPage(content, query.page(), query.size(), totalElements, totalPages);
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
