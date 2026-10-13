package dev.rma.returns.adapter.out.persistence;

import java.sql.Timestamp;
import java.util.Objects;

import org.springframework.dao.IncorrectUpdateSemanticsDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.rma.returns.application.port.out.RmaProjectionUpdater;
import dev.rma.returns.domain.enums.RefundDecision;
import dev.rma.returns.domain.enums.RmaStatus;
import dev.rma.returns.domain.events.*;

@Repository
public class PostgresRmaProjectionUpdater implements RmaProjectionUpdater {

    private final JdbcTemplate jdbcTemplate;

    public PostgresRmaProjectionUpdater(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void apply(DomainEvent event) {
        Objects.requireNonNull(event, "Event must not be null");
        Timestamp updatedAt = Timestamp.from(event.occurredAt());

        int affectedRows = switch (event) {
            case RmaRequested requested -> jdbcTemplate.update(
                """
                    INSERT INTO returns.rma_projection
                        (rma_id, business_number, status, refund_amount, updated_at)
                    VALUES (?, ?, ?, NULL, ?)
                    """,
                requested.rmaId(),
                requested.businessNumber(),
                RmaStatus.REQUESTED.name(),
                updatedAt);
            case RefundDecided decided -> jdbcTemplate.update(
                """
                    UPDATE returns.rma_projection
                    SET status = ?, refund_amount = ?, updated_at = ?
                    WHERE rma_id = ?
                    """,
                statusAfter(decided).name(),
                decided.refundAmount().amount(),
                updatedAt,
                event.rmaId());
            case InspectionCompleted inspectionCompleted -> jdbcTemplate.update(
                """
                    UPDATE returns.rma_projection SET updated_at = ? WHERE rma_id = ?
                    """,
                updatedAt,
                event.rmaId());
            default -> jdbcTemplate.update(
                """
                    UPDATE returns.rma_projection SET status = ?, updated_at = ? WHERE rma_id = ?
                    """,
                statusAfter(event).name(),
                updatedAt,
                event.rmaId());
        };

        if (affectedRows != 1) {
            throw new IncorrectUpdateSemanticsDataAccessException(
                    "Expected one projection row for RMA " + event.rmaId() + ", updated " + affectedRows);
        }
    }

    private RmaStatus statusAfter(DomainEvent event) {
        return switch (event) {
            case RmaRequested ignored -> RmaStatus.REQUESTED;
            case LabelIssued ignored -> RmaStatus.LABEL_ISSUED;
            case PickupScheduled ignored -> RmaStatus.PICKUP_SCHEDULED;
            case ParcelInTransit ignored -> RmaStatus.IN_TRANSIT;
            case ParcelReceived ignored -> RmaStatus.RECEIVED;
            case InspectionStarted ignored -> RmaStatus.UNDER_INSPECTION;
            case InspectionCompleted ignored -> throw new IllegalArgumentException(
                    "InspectionCompleted does not change the status");
            case RefundDecided decided -> decided.decision() == RefundDecision.REJECTED
                    ? RmaStatus.REJECTED
                    : RmaStatus.REFUND_PENDING;
            case RefundExecuted ignored -> RmaStatus.REFUNDED;
            case RefundFailed ignored -> RmaStatus.REFUND_FAILED;
            case RmaExpired ignored -> RmaStatus.EXPIRED;
            case RmaLost ignored -> RmaStatus.LOST;
            case RmaCancelled ignored -> RmaStatus.CANCELLED;
            case RmaClosed ignored -> RmaStatus.CLOSED;
        };
    }
}
