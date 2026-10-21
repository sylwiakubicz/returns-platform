package dev.rma.returns.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.rma.returns.application.port.out.RmaIdLookup;

@Repository
public class PostgresRmaIdLookup implements RmaIdLookup {
    private final JdbcTemplate jdbcTemplate;

    public PostgresRmaIdLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UUID> findId(String businessNumber) {
        // Historia jest źródłem prawdy również przy opóźnieniu lub odbudowie projekcji.
        var ids = jdbcTemplate.query(
                """
                SELECT rma_id FROM returns.rma_events
                WHERE type = 'RmaRequested' AND payload ->> 'businessNumber' = ?
                """,
                (resultSet, row) -> resultSet.getObject("rma_id", UUID.class),
                businessNumber);
        return DataAccessUtils.optionalResult(ids);
    }
}
