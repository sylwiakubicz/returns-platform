package dev.rma.returns.adapter.out.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.rma.returns.application.port.out.BusinessNumberGenerator;

@Repository
public class PostgresBusinessNumberGenerator implements BusinessNumberGenerator {
    private final JdbcTemplate jdbcTemplate;

    public PostgresBusinessNumberGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextNumber(Instant requestedAt) {
        int year = requestedAt.atZone(ZoneOffset.UTC).getYear();
        Integer counter = jdbcTemplate.queryForObject(
                """
                INSERT INTO returns.rma_business_number_counters (year, last_value)
                VALUES (?, 1)
                ON CONFLICT (year) DO UPDATE
                SET last_value = returns.rma_business_number_counters.last_value + 1
                RETURNING last_value
                """,
                Integer.class,
                year);
        return String.format(Locale.ROOT, "RMA-%04d-%06d", year, counter);
    }
}
