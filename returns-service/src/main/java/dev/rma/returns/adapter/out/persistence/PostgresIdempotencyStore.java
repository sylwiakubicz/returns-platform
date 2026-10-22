package dev.rma.returns.adapter.out.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Optional;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.rma.common.idempotency.RequestBodyHasher;
import dev.rma.returns.application.model.IdempotencyRecord;
import dev.rma.returns.application.port.out.IdempotencyStore;

@Repository
public class PostgresIdempotencyStore implements IdempotencyStore {
    private final JdbcTemplate jdbcTemplate;

    public PostgresIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String key, String endpoint) {
        String resource = endpoint.length() + ":" + endpoint + key;
        String hash = RequestBodyHasher.sha256(resource.getBytes(StandardCharsets.UTF_8));
        long lockId = Long.parseUnsignedLong(hash.substring(0, 16), 16);
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", row -> {}, lockId);
    }

    @Override
    public Optional<IdempotencyRecord> find(String key, String endpoint) {
        var rows = jdbcTemplate.query(
                """
                SELECT request_hash, response_status, response_body::text, created_at
                FROM returns.idempotency_keys
                WHERE idempotency_key = ? AND endpoint = ?
                """,
                (row, rowNumber) -> new IdempotencyRecord(
                        row.getString("request_hash"), row.getInt("response_status"),
                        row.getString("response_body"),
                                row.getTimestamp("created_at").toInstant()),
                key,
                endpoint);
        return DataAccessUtils.optionalResult(rows);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String key, String endpoint, IdempotencyRecord record) {
        jdbcTemplate.update(
                """
                INSERT INTO returns.idempotency_keys
                    (idempotency_key, endpoint, request_hash, response_status, response_body, created_at)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
                """,
                key,
                endpoint,
                record.requestHash(),
                record.responseStatus(),
                record.responseBody(),
                Timestamp.from(record.createdAt()));
    }
}
