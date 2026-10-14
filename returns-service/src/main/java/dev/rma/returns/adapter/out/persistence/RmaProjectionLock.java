package dev.rma.returns.adapter.out.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

final class RmaProjectionLock {
    private static final int NAMESPACE = 1380794624;
    private static final int KEY = 1;

    static void forAppend(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock_shared(?, ?)", row -> {}, NAMESPACE, KEY);
    }

    static void forRebuild(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?, ?)", row -> {}, NAMESPACE, KEY);
    }

    private RmaProjectionLock() {}
}
