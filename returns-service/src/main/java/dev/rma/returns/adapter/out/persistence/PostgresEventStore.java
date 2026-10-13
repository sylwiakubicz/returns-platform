package dev.rma.returns.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.rma.returns.adapter.out.persistence.mappers.EventJsonMapper;
import dev.rma.returns.adapter.out.persistence.mappers.RmaStateJsonMapper;
import dev.rma.returns.application.exception.ConcurrencyConflictException;
import dev.rma.returns.application.port.out.EventStore;
import dev.rma.returns.application.port.out.RmaProjectionUpdater;
import dev.rma.returns.application.port.out.StoredEvent;
import dev.rma.returns.domain.Rma;
import dev.rma.returns.domain.RmaState;
import dev.rma.returns.domain.events.DomainEvent;

@Repository
public class PostgresEventStore implements EventStore {

    private final JdbcTemplate jdbcTemplate;
    private final EventJsonMapper mapper;
    private final RmaStateJsonMapper stateMapper;
    private final RmaProjectionUpdater projectionUpdater;
    private final int snapshotEveryNEvents;

    public PostgresEventStore(
            JdbcTemplate jdbcTemplate,
            EventJsonMapper mapper,
            RmaStateJsonMapper stateMapper,
            RmaProjectionUpdater projectionUpdater,
            @Value("${returns.snapshot-every-n-events:50}") int snapshotEveryNEvents) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = mapper;
        this.stateMapper = stateMapper;
        this.projectionUpdater = projectionUpdater;
        if (snapshotEveryNEvents < 1) {
            throw new IllegalArgumentException("Snapshot interval must be positive");
        }
        this.snapshotEveryNEvents = snapshotEveryNEvents;
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
        if (expectedVersion > Integer.MAX_VALUE - (long) events.size()) {
            throw new IllegalArgumentException("Event versions exceed the database INTEGER range");
        }

        for (DomainEvent event : events) {
            Objects.requireNonNull(event, "Event must not be null");
            if (!rmaId.equals(event.rmaId())) {
                throw new IllegalArgumentException("Event must belong to the specified RMA");
            }
        }

        String sql =
                """
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
                            "Concurrent update of RMA " + rmaId + " at expected version " + expectedVersion, exception);
                }
                throw exception;
            }
        }

        for (DomainEvent event : events) {
            projectionUpdater.apply(event);
        }

        if (expectedVersion / snapshotEveryNEvents < version / snapshotEveryNEvents) {
            saveSnapshot(rmaId, version);
        }
    }

    private void saveSnapshot(UUID rmaId, long version) {
        List<DomainEvent> history = jdbcTemplate.query(
                """
                SELECT type, schema_version, payload
                FROM returns.rma_events
                WHERE rma_id = ? AND version <= ?
                ORDER BY version ASC
                """,
                (resultSet, rowNum) -> mapper.fromJson(
                        resultSet.getString("type"),
                        resultSet.getInt("schema_version"),
                        resultSet.getString("payload")),
                rmaId,
                version);
        Rma rma = Rma.fromHistory(history);
        if (rma.version() != version) {
            throw new IllegalStateException("Event history is incomplete for RMA " + rmaId);
        }
        String state = stateMapper.toJson(rma.state());
        jdbcTemplate.update(
                """
                INSERT INTO returns.rma_snapshots (rma_id, version, state, created_at)
                VALUES (?, ?, CAST(? AS jsonb), ?)
                """,
                rmaId,
                (int) version,
                state,
                Timestamp.from(Instant.now()));
    }

    private boolean isVersionConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException postgresException
                    && "23505".equals(postgresException.getSQLState())
                    && postgresException.getServerErrorMessage() != null
                    && "unique_rma_id_version"
                            .equals(postgresException.getServerErrorMessage().getConstraint())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Rma> load(UUID rmaId) {
        Objects.requireNonNull(rmaId, "RmaId must not be null");

        String sqlSnapshot =
                """
            SELECT state FROM returns.rma_snapshots
            WHERE rma_id = ? ORDER BY version DESC LIMIT 1
            """;

        List<RmaState> state = jdbcTemplate.query(
                sqlSnapshot, (resultSet, rowNum) -> stateMapper.fromJson(resultSet.getString("state")), rmaId);

        if (!state.isEmpty()) {
            List<DomainEvent> history =
                    loadHistoryEvents(rmaId, state.getFirst().version());
            return Optional.of(Rma.fromState(state.getFirst(), history));
        } else {
            List<DomainEvent> history = loadHistoryEvents(rmaId, 0);

            if (history.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(Rma.fromHistory(history));
        }
    }

    private List<DomainEvent> loadHistoryEvents(UUID rmaId, int afterVersion) {
        String sql =
                """
                SELECT type, schema_version, payload
                FROM returns.rma_events
                WHERE rma_id = ? AND version > ?
                ORDER BY version ASC
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> mapper.fromJson(
                        resultSet.getString("type"),
                        resultSet.getInt("schema_version"),
                        resultSet.getString("payload")),
                rmaId,
                afterVersion);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Stream<StoredEvent> readFrom(UUID rmaId, long fromVersion) {
        Objects.requireNonNull(rmaId, "RmaId must not be null");
        if (fromVersion < 1) {
            throw new IllegalArgumentException("From version must be at least 1");
        }

        String sql =
                """
                SELECT event_id, rma_id, version, type, schema_version, payload, occurred_at
                FROM returns.rma_events
                WHERE rma_id = ? AND version >= ?
                ORDER BY version ASC
                """;

        return jdbcTemplate.queryForStream(
                connection -> {
                    var statement = connection.prepareStatement(sql);
                    statement.setObject(1, rmaId);
                    statement.setLong(2, fromVersion);
                    // PostgreSQL pobiera wiersze partiami wewnątrz transakcji.
                    statement.setFetchSize(100);
                    return statement;
                },
                this::mapStoredEvent);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Stream<StoredEvent> readAll() {
        String sql =
                """
                SELECT event_id, rma_id, version, type, schema_version, payload, occurred_at
                FROM returns.rma_events
                ORDER BY rma_id ASC, version ASC
                """;

        return jdbcTemplate.queryForStream(
                connection -> {
                    var statement = connection.prepareStatement(sql);
                    statement.setFetchSize(100);
                    return statement;
                },
                this::mapStoredEvent);
    }

    private StoredEvent mapStoredEvent(ResultSet resultSet, int rowNum) throws SQLException {
        return new StoredEvent(
                resultSet.getObject("event_id", UUID.class),
                resultSet.getObject("rma_id", UUID.class),
                resultSet.getLong("version"),
                resultSet.getString("type"),
                resultSet.getInt("schema_version"),
                resultSet.getString("payload"),
                resultSet.getTimestamp("occurred_at").toInstant());
    }
}
