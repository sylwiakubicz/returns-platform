package dev.rma.returns.adapter.out.persistence;

import dev.rma.returns.application.port.out.EventStore;
import dev.rma.returns.application.exception.ConcurrencyConflictException;
import dev.rma.returns.application.port.out.StoredEvent;
import dev.rma.returns.domain.Rma;
import dev.rma.returns.domain.events.DomainEvent;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.postgresql.util.PSQLException;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public class PostgresEventStore implements EventStore {

    private final JdbcTemplate jdbcTemplate;
    private final EventJsonMapper mapper;

    public PostgresEventStore(JdbcTemplate jdbcTemplate, EventJsonMapper mapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void append(UUID rmaId, long expectedVersion, List<DomainEvent> events) {
        Objects.requireNonNull(rmaId, "RmaId must not be null");
        Objects.requireNonNull(events, "Events must not be null");
        if (events.isEmpty()) {
            throw new IllegalArgumentException("Events must not be empty");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("Expected version must not be negative");
        }

        for (DomainEvent event : events) {
            Objects.requireNonNull(event, "Event must not be null");
            if (!rmaId.equals(event.rmaId())) {
                throw new IllegalArgumentException("Event must belong to the specified RMA");
            }
        }

        String sql = """
                INSERT INTO returns.rma_events
                    (event_id, rma_id, version, type, schema_version, payload, occurred_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """;

        long version = expectedVersion;
        for (DomainEvent event : events) {
            UUID eventId = UUID.randomUUID();
            version++;
            String type = mapper.typeOf(event);
            String payload = mapper.toJson(event);
            int schemaVersion = mapper.schemaVersionOf(event);
            Timestamp occurredAt = Timestamp.from(event.occurredAt());

            try {
                jdbcTemplate.update(sql, eventId, rmaId, (int) version, type, schemaVersion, payload, occurredAt);
            } catch (DataIntegrityViolationException exception) {
                if (isVersionConflict(exception)) {
                    throw new ConcurrencyConflictException(
                            "Concurrent update of RMA " + rmaId + " at expected version " + expectedVersion,
                            exception);
                }
                throw exception;
            }
        }
    }

    private boolean isVersionConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException postgresException
                    && "23505".equals(postgresException.getSQLState())
                    && postgresException.getServerErrorMessage() != null
                    && "unique_rma_id_version".equals(
                            postgresException.getServerErrorMessage().getConstraint())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Rma> load(UUID rmaId) {
        Objects.requireNonNull(rmaId, "RmaId must not be null");

        String sql = """
                SELECT type, schema_version, payload
                FROM returns.rma_events
                WHERE rma_id = ?
                ORDER BY version ASC
                """;

        List<DomainEvent> history = jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> mapper.fromJson(
                        resultSet.getString("type"),
                        resultSet.getInt("schema_version"),
                        resultSet.getString("payload")),
                rmaId);

        if (history.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Rma.fromHistory(history));
    }

    @Override
    public Stream<StoredEvent> readFrom(UUID rmaId, long fromVersion) {
        return Stream.empty();
    }
}
