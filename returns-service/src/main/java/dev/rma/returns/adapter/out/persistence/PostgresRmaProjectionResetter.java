package dev.rma.returns.adapter.out.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.rma.returns.application.port.out.RmaProjectionResetter;

@Repository
public class PostgresRmaProjectionResetter implements RmaProjectionResetter {
    private final JdbcTemplate jdbcTemplate;

    public PostgresRmaProjectionResetter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void resetForRebuild() {
        RmaProjectionLock.forRebuild(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM returns.rma_projection");
    }
}
