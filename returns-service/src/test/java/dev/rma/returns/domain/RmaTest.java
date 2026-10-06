package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.*;
import dev.rma.returns.domain.events.*;
import dev.rma.returns.domain.exceptions.InvalidRmaStateTransitionException;
import dev.rma.returns.domain.exceptions.RmaDomainException;
import dev.rma.returns.domain.policy.InspectionPolicy;
import dev.rma.returns.domain.policy.RefundPolicy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;

public class RmaTest {

    private static final RefundPolicy REFUND_POLICY =
        new RefundPolicy(
            0,
            15,
            40,
            new BigDecimal("15.00")
        );

    private static final InspectionPolicy INSPECTION_POLICY =
        new InspectionPolicy(
            new BigDecimal("500.00")
        );

    private static final UUID RMA_ID =
        UUID.fromString("018f0000-0000-7000-8000-000000000001");

    private static final Instant REQUESTED_AT =
        Instant.parse("2026-09-10T12:00:00Z");

    private static final Instant DELIVERED_AT =
        Instant.parse("2026-09-01T12:00:00Z");

    private static final Instant ISSUED_AT =
        Instant.parse("2026-09-10T12:00:00Z");

    private static final LocalDate SCHEDULED_FOR =
        LocalDate.parse("2026-09-15");

    private static final Instant COLLECTED_AT =
        Instant.parse("2026-09-16T13:00:00Z");

    private static final Instant RECEIVED_AT =
        Instant.parse("2026-09-17T13:00:00Z");

    private static final Instant STARTED_AT =
        Instant.parse("2026-09-18T13:00:00Z");

    private static final Instant COMPLETED_AT =
        Instant.parse("2026-09-18T15:00:00Z");

    private static final Instant DECIDED_AT =
        Instant.parse("2026-09-19T15:00:00Z");

    private static final Instant EXECUTED_AT =
        Instant.parse("2026-09-20T15:00:00Z");

    private static final Instant FAILED_AT =
        Instant.parse("2026-09-20T15:00:00Z");

    private static final Instant RETRY_DECIDED_AT =
        Instant.parse("2026-09-21T15:00:00Z");

    private static final String BUSINESS_NUMBER = "RMA-2026-000001";
    private static final String ORDER_ID = "order-1";
    private static final String CUSTOMER_ID = "customer-1";

    private static final String LABEL_ID = "label-1";
    private static final String TRACKING_NUMBER = "tr-1";
    private static final String LABEL_FILE_KEY = "key";

    private static final String PICKUP_ID = "pickup-id-1";
    private static final String WAREHOUSE_ID = "warehouse-1";
    private static final String INSPECTOR_ID = "inspector-1";
    private static final String SUPERVISOR_ID = "supervisor-1";
    private static final String PAYOUT_ID = "payout-1";
    private static final int PAYOUT_ATTEMPT = 1;

    private static final Currency PLN = Currency.getInstance("PLN");
    private static final Currency EUR = Currency.getInstance("EUR");

    private static final int RETURN_WINDOW_DAYS = 14;


    @Nested
    class RequestTests {
        @Test
        void shouldCreateRequestedRma() {
            List<RequestedItem> items = List.of(validRequestedItem());

            RmaRequested event = Rma.request(
                RMA_ID,
                BUSINESS_NUMBER,
                ORDER_ID,
                CUSTOMER_ID,
                DELIVERED_AT,
                RmaReason.CHANGED_MIND,
                "Nie pasuje",
                items,
                PLN,
                REQUESTED_AT,
                RETURN_WINDOW_DAYS);

            assertAll(
                () -> assertThat(event.rmaId()).isEqualTo(RMA_ID),
                () -> assertThat(event.businessNumber()).isEqualTo(BUSINESS_NUMBER),
                () -> assertThat(event.orderId()).isEqualTo(ORDER_ID),
                () -> assertThat(event.customerId()).isEqualTo(CUSTOMER_ID),
                () -> assertThat(event.customerNote()).isEqualTo("Nie pasuje"),
                () -> assertThat(event.orderDeliveredAt()).isEqualTo(DELIVERED_AT),
                () -> assertThat(event.requestedAt()).isEqualTo(REQUESTED_AT),
                () -> assertThat(event.reason()).isEqualTo(RmaReason.CHANGED_MIND),
                () -> assertThat(event.currency()).isEqualTo(PLN),
                () -> assertThat(event.items()).containsExactlyElementsOf(items)
            );
        }

        @Test
        void shouldRebuildRequestedStateFromRmaRequestedEvent() {
            RmaRequested event = validRmaRequested();

            Rma rma = Rma.fromHistory(List.of(event));

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.REQUESTED),
                () -> assertThat(rma.version()).isEqualTo(1),
                () -> assertThat(rma.rmaId()).isEqualTo(event.rmaId()),
                () -> assertThat(rma.businessNumber()).isEqualTo(event.businessNumber()),
                () -> assertThat(rma.orderId()).isEqualTo(event.orderId()),
                () -> assertThat(rma.customerId()).isEqualTo(event.customerId()),
                () -> assertThat(rma.currency()).isEqualTo(event.currency())
            );

            assertThat(rma.items()).hasSize(1);
            RequestedItem requestedItem = event.items().getFirst();
            RmaItem item = rma.items().getFirst();
            assertAll(
                () -> assertThat(item.itemId()).isEqualTo(requestedItem.itemId()),
                () -> assertThat(item.sku()).isEqualTo(requestedItem.sku()),
                () -> assertThat(item.name()).isEqualTo(requestedItem.name()),
                () -> assertThat(item.unitPrice()).isEqualTo(requestedItem.unitPrice()),
                () -> assertThat(item.grade()).isNull(),
                () -> assertThat(item.inspectionNote()).isNull(),
                () -> assertThat(item.photoKeys()).isEmpty(),
                () -> assertThat(item.deductionPercent()).isNull(),
                () -> assertThat(item.refundableAmount()).isNull()
            );
        }

        @Test
        void shouldRejectRequestWhenReturnWindowExpired() {
            Instant deliveredAt = Instant.parse("2026-09-01T12:00:00Z");
            Instant requestedAt = deliveredAt
                .plus(RETURN_WINDOW_DAYS + 1, ChronoUnit.DAYS);
            List<RequestedItem> items = List.of(validRequestedItem());

            assertThatThrownBy(() -> Rma.request(
                RMA_ID,
                BUSINESS_NUMBER,
                ORDER_ID,
                CUSTOMER_ID,
                deliveredAt,
                RmaReason.CHANGED_MIND,
                "Nie pasuje",
                items,
                PLN,
                requestedAt,
                RETURN_WINDOW_DAYS
            ))
                .isInstanceOfSatisfying(
                    RmaDomainException.class,
                    exception -> assertThat(exception.code())
                        .isEqualTo(RmaErrorCode.RETURN_WINDOW_EXPIRED)
                );
        }

        @Test
        void shouldAcceptRequestOnLastDayOfReturnWindow() {
            Instant deliveredAt = Instant.parse("2026-09-01T12:00:00Z");
            Instant requestedAt = deliveredAt
                .plus(RETURN_WINDOW_DAYS, ChronoUnit.DAYS);
            List<RequestedItem> items = List.of(validRequestedItem());

            RmaRequested event = Rma.request(
                RMA_ID,
                BUSINESS_NUMBER,
                ORDER_ID,
                CUSTOMER_ID,
                deliveredAt,
                RmaReason.CHANGED_MIND,
                "Nie pasuje",
                items,
                PLN,
                requestedAt,
                RETURN_WINDOW_DAYS
            );

            assertThat(event).isNotNull();
        }

        @Test
        void shouldRejectRequestWithoutItems() {
            List<RequestedItem> items = List.of();

            assertThatThrownBy(() -> Rma.request(
                RMA_ID,
                BUSINESS_NUMBER,
                ORDER_ID,
                CUSTOMER_ID,
                DELIVERED_AT,
                RmaReason.CHANGED_MIND,
                "Nie pasuje",
                items,
                PLN,
                REQUESTED_AT,
                RETURN_WINDOW_DAYS
            )).isInstanceOfSatisfying(
                RmaDomainException.class,
                exception -> assertThat(exception.code())
                    .isEqualTo(RmaErrorCode.NO_ITEMS)
            );
        }

        @Test
        void shouldRejectRequestWithMixedCurrencies() {
            List<RequestedItem> items = List.of(
                validRequestedItem("1", PLN, "30.00"),
                validRequestedItem("2", EUR, "30.00")
            );

            assertThatThrownBy(() -> Rma.request(
                RMA_ID,
                BUSINESS_NUMBER,
                ORDER_ID,
                CUSTOMER_ID,
                DELIVERED_AT,
                RmaReason.CHANGED_MIND,
                "Nie pasuje",
                items,
                PLN,
                REQUESTED_AT,
                RETURN_WINDOW_DAYS
            ))
                .isInstanceOfSatisfying(
                    RmaDomainException.class,
                    exception -> assertThat(exception.code())
                        .isEqualTo(RmaErrorCode.MIXED_CURRENCIES)
                );
        }
    }

    @Nested
    class LabelTests {
        @Test
        void shouldIssueLabelForRequestedRma() {
            Rma rma = Rma.fromHistory(List.of(validRmaRequested()));

            LabelIssued event = rma.issueLabel(
                LABEL_ID,
                TRACKING_NUMBER,
                LABEL_FILE_KEY,
                ISSUED_AT
            );
            assertAll(
                () -> assertThat(event.labelId()).isEqualTo(LABEL_ID),
                () -> assertThat(event.trackingNumber()).isEqualTo(TRACKING_NUMBER),
                () -> assertThat(event.labelFileKey()).isEqualTo(LABEL_FILE_KEY),
                () -> assertThat(event.issuedAt()).isEqualTo(ISSUED_AT)
            );
        }

        @Test
        void shouldRebuildLabelIssuedStateFromHistory() {
            LabelIssued event = validLabelIssued();
            Rma rma = Rma.fromHistory( List.of(
                validRmaRequested(),
                event
            ));

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.LABEL_ISSUED),
                () -> assertThat(rma.version()).isEqualTo(2),
                () -> assertThat(rma.labelId()).isEqualTo(event.labelId()),
                () -> assertThat(rma.trackingNumber()).isEqualTo(event.trackingNumber())
            );
        }

        @Test
        void shouldRejectLabelIssuingWhenRmaIsNotRequested() {
            Rma rma = Rma.fromHistory( List.of(
                validRmaRequested(),
                validLabelIssued()
            ));

            assertThatThrownBy(() -> rma.issueLabel(
                LABEL_ID,
                TRACKING_NUMBER,
                LABEL_FILE_KEY,
                ISSUED_AT
            ))
                .isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage(
                    "Invalid RMA state transition from LABEL_ISSUED to LABEL_ISSUED"
                );
        }
    }

    @Nested
    class PickupTests {
        @Test
        void shouldSchedulePickupForRmaWithIssuedLabel() {
            Rma rma = Rma.fromHistory( List.of(
                validRmaRequested(),
                validLabelIssued()
            ));

            PickupScheduled event = rma.schedulePickup(
                PICKUP_ID,
                SCHEDULED_FOR
            );

            assertAll(
                () -> assertThat(event.pickupId()).isEqualTo(PICKUP_ID),
                () -> assertThat(event.scheduledFor()).isEqualTo(SCHEDULED_FOR)
            );

        }

        @Test
        void shouldRebuildPickupScheduledStateFromHistory() {
            PickupScheduled event = validPickupScheduled();
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                event
            ));

            assertAll(
                () -> assertThat(rma.version()).isEqualTo(3),
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.PICKUP_SCHEDULED),
                () -> assertThat(rma.pickupScheduledFor()).isEqualTo(event.scheduledFor())
            );
        }

        @Test
        void shouldRejectPickupSchedulingWhenRmaIsNotLabelIssued() {
            Rma rma = Rma.fromHistory( List.of(
                validRmaRequested()
            ));

            assertThatThrownBy(() -> rma.schedulePickup(
                PICKUP_ID,
                SCHEDULED_FOR
            )).isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage( "Invalid RMA state transition from REQUESTED to PICKUP_SCHEDULED");


        }
    }

    @Nested
    class InTransitTests {
        @Test
        void shouldMarkParcelInTransitFromLabelIssued() {
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued()
            ));

            ParcelInTransit event = rma.markParcelInTransit(COLLECTED_AT);

            assertThat(event.collectedAt()).isEqualTo(COLLECTED_AT);
        }

        @Test
        void shouldMarkParcelInTransitFromPickupScheduled() {
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                validPickupScheduled()
            ));

            ParcelInTransit event = rma.markParcelInTransit(COLLECTED_AT);

            assertThat(event.collectedAt()).isEqualTo(COLLECTED_AT);
        }

        @Test
        void shouldRejectParcelInTransitWhenRmaIsInInvalidState() {
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested()
            ));

            assertThatThrownBy(() -> rma.markParcelInTransit(COLLECTED_AT))
                .isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage("Invalid RMA state transition from REQUESTED to IN_TRANSIT");
        }

        @Test
        void shouldRebuildInTransitStateFromHistory() {
            ParcelInTransit event = validParcelInTransit();
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                validPickupScheduled(),
                event
            ));

            assertAll(
                () -> assertThat(rma.version()).isEqualTo(4),
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.IN_TRANSIT)
            );
        }
    }

    @Nested
    class ReceiveTests {
        @Test
        void shouldMarkParcelReceivedFromInTransit() {
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit()
            ));

            ParcelReceived event = rma.receive(RECEIVED_AT, WAREHOUSE_ID);

            assertAll(
                () -> assertThat(event.receivedAt()).isEqualTo(RECEIVED_AT),
                () -> assertThat(event.warehouseId()).isEqualTo(WAREHOUSE_ID)
            );
        }

        @Test
        void shouldRejectParcelReceivedWhenRmaIsNotInTransit() {
            Rma rma = Rma.fromHistory(List.of(validRmaRequested()));

            assertThatThrownBy(() -> rma.receive(RECEIVED_AT, WAREHOUSE_ID))
                .isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage( "Invalid RMA state transition from REQUESTED to RECEIVED");
        }

        @Test
        void shouldRebuildReceivedStateFromHistory() {
            ParcelReceived event = validParcelReceived();
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                event
            ));

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.RECEIVED),
                () -> assertThat(rma.version()).isEqualTo(5),
                () -> assertThat(rma.parcelReceivedAt()).isEqualTo(event.receivedAt())
            );
        }
    }

    @Nested
    class InspectionStartTests {
        @Test
        void shouldStartInspectionFromReceived() {
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived()
            ));

            InspectionStarted event = rma.startInspection(INSPECTOR_ID, STARTED_AT);

            assertAll(
                () -> assertThat(event.inspectorId()).isEqualTo(INSPECTOR_ID),
                () -> assertThat(event.startedAt()).isEqualTo(STARTED_AT)
            );
        }

        @Test
        void shouldRejectInspectionStartingWhenRmaIsNotReceived() {
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested()
            ));

            assertThatThrownBy(() -> rma.startInspection(INSPECTOR_ID, STARTED_AT))
                .isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage( "Invalid RMA state transition from REQUESTED to UNDER_INSPECTION");
        }

        @Test
        void shouldRebuildUnderInspectionStateFromHistory() {
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted()
            ));

            assertAll(
                () -> assertThat(rma.version()).isEqualTo(6),
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.UNDER_INSPECTION)
            );
        }
    }

    @Nested
    class CompleteInspectionTests {
        @Test
        void shouldCompleteInspectionFromUnderInspection() {
            Rma rma = rmaUnderInspection();

            List<InspectionResult> results = List.of(validInspectionResult());

            InspectionCompleted event = rma.completeInspection(
                INSPECTOR_ID,
                COMPLETED_AT,
                results,
                null,
                INSPECTION_POLICY
            );

            assertAll(
                () -> assertThat(event.inspectorId()).isEqualTo(INSPECTOR_ID),
                () -> assertThat(event.completedAt()).isEqualTo(COMPLETED_AT),
                () -> assertThat(event.approvedBy()).isNull(),
                () -> assertThat(event.results()).isEqualTo(results)
            );
        }

        @Test
        void shouldRejectInspectionCompletionWhenRmaIsNotUnderInspection() {
            List<InspectionResult> results = List.of(validInspectionResult());
            Rma rma = Rma.fromHistory(List.of(validRmaRequested()));

            assertThatThrownBy(() -> rma.completeInspection(
                INSPECTOR_ID,
                COMPLETED_AT,
                results,
                null,
                INSPECTION_POLICY
            ))
                .isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage("Invalid RMA state transition from REQUESTED to UNDER_INSPECTION");
        }

        @Test
        void shouldRebuildInspectionCompletedStateFromHistory() {
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted()
            ));

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.UNDER_INSPECTION),
                () -> assertThat(rma.version()).isEqualTo(7),
                () -> assertThat(rma.inspectedAt()).isEqualTo(COMPLETED_AT),
                () -> assertThat(rma.inspectorId()).isEqualTo(INSPECTOR_ID)
            );
        }

        @Test
        void shouldApplyInspectionResultsToRmaItems() {
            InspectionCompleted event = validInspectionCompleted();
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                event
            ));

            RmaItem item = rma.items().getFirst();
            InspectionResult inspectionResult = event.results().getFirst();

            assertAll(
                () -> assertThat(item.grade()).isEqualTo(inspectionResult.grade()),
                () -> assertThat(item.inspectionNote()).isEqualTo(inspectionResult.note()),
                () -> assertThat(item.photoKeys()).isEqualTo(inspectionResult.photoKeys())
            );

        }

        @Test
        void shouldRejectIncompleteInspection() {
            Rma rma = rmaUnderInspection();

            assertThatThrownBy(() -> rma.completeInspection(
                INSPECTOR_ID,
                COMPLETED_AT,
                List.of(new InspectionResult("item-2", ItemGrade.A, "note", List.of())),
                null,
                INSPECTION_POLICY
            ))
                .isInstanceOfSatisfying(
                    RmaDomainException.class,
                    exception -> assertThat(exception.code())
                        .isEqualTo(RmaErrorCode.INCOMPLETE_INSPECTION)
                );
        }

        @Test
        void shouldRequireSupervisorApprovalForExpensiveRejectedItem() {
            List<RequestedItem> items = List.of(validRequestedItem("item-1", PLN, "550.00")) ;

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted()
            ));

            assertThatThrownBy(() -> rma.completeInspection(
                INSPECTOR_ID,
                COMPLETED_AT,
                List.of(validInspectionResult(ItemGrade.D)),
                null,
                INSPECTION_POLICY
            ))
                .isInstanceOfSatisfying(
                    RmaDomainException.class,
                    exception -> assertThat(exception.code())
                        .isEqualTo(RmaErrorCode.SUPERVISOR_APPROVAL_REQUIRED)
                );
        }

        @Test
        void shouldAllowExpensiveRejectedItemWithSupervisorApproval() {
            List<RequestedItem> items = List.of(validRequestedItem("item-1", PLN, "550.00"));
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted()
            ));

            InspectionCompleted event = rma.completeInspection(
                INSPECTOR_ID,
                COMPLETED_AT,
                List.of(validInspectionResult(ItemGrade.D)),
                SUPERVISOR_ID,
                INSPECTION_POLICY
            );

            assertAll(
                () -> assertThat(event.approvedBy()).isEqualTo(SUPERVISOR_ID),
                () -> assertThat(event.results()).hasSize(1),
                () -> assertThat(rma.version()).isEqualTo(7)
            );
        }

        @Test
        void shouldAllowInspectionWithoutPhotos() {
            Rma rma = rmaUnderInspection();

            InspectionCompleted event = rma.completeInspection(
                INSPECTOR_ID,
                COMPLETED_AT,
                List.of(new InspectionResult(
                    "item-1",
                    ItemGrade.A,
                    "note",
                    List.of()
                )),
                null,
                INSPECTION_POLICY
            );

            assertAll(
                () -> assertThat(event.results().getFirst().photoKeys()).isEmpty(),
                () -> assertThat(rma.items().getFirst().photoKeys()).isEmpty()
            );
        }
    }

    @Nested
    class RefundDecisionTests {
        @Test
        void shouldDecideFullRefundWhenNoItemHasGradeD() {
            Rma rma = rmaAfterInspection();
            RefundDecided refundDecided =  rma.decideRefund(DECIDED_AT, REFUND_POLICY);
            assertThat(refundDecided.decision()).isEqualTo(RefundDecision.FULL);
        }

        @Test
        void shouldMoveToRefundPendingAfterFullRefundDecision() {
            Rma rma = rmaAfterInspection();
            rma.decideRefund(DECIDED_AT, REFUND_POLICY);

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.REFUND_PENDING),
                () -> assertThat(rma.version()).isEqualTo(8),
                () -> assertThat(rma.inspectorId()).isEqualTo(INSPECTOR_ID),
                () -> assertThat(rma.inspectedAt()).isEqualTo(COMPLETED_AT)
            );
        }

        @Test
        void shouldRebuildRefundPendingStateFromHistory() {
            RefundDecided event = validFullRefundDecided();
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(),
                event
            ));

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.REFUND_PENDING),
                () -> assertThat(rma.version()).isEqualTo(8),
                () -> assertThat(rma.refundAmount()).isEqualTo(event.refundAmount()),
                () -> assertThat(rma.deductionTotal()).isEqualTo(event.deductionTotal())
            );
        }

        @Test
        void shouldDecidePartialRefundWhenSomeItemsHaveGradeD() {
            List<RequestedItem> items = List.of(
              validRequestedItem("item-1", PLN, "30.00"),
              validRequestedItem("item-2", PLN, "45.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.D),
                validInspectionResult("item-2", ItemGrade.A));

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            RefundDecided event = rma.decideRefund(
                DECIDED_AT,
                REFUND_POLICY
            );

            assertThat(event.decision()).isEqualTo(RefundDecision.PARTIAL);
        }

        @Test
        void shouldMoveToRefundPendingAfterPartialRefundDecision() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "30.00"),
                validRequestedItem("item-2", PLN, "45.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.D),
                validInspectionResult("item-2", ItemGrade.A));

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            rma.decideRefund(DECIDED_AT, REFUND_POLICY);

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.REFUND_PENDING),
                () -> assertThat(rma.version()).isEqualTo(8)
            );
        }

        @Test
        void shouldRejectRefundWhenAllItemsHaveGradeD() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "30.00"),
                validRequestedItem("item-2", PLN, "45.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.D),
                validInspectionResult("item-2", ItemGrade.D));

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            RefundDecided event = rma.decideRefund(
                DECIDED_AT,
                REFUND_POLICY
            );

            assertThat(event.decision())
                .isEqualTo(RefundDecision.REJECTED);
        }

        @Test
        void shouldMoveToRejectedAfterRejectedRefundDecision() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "30.00"),
                validRequestedItem("item-2", PLN, "45.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.D),
                validInspectionResult("item-2", ItemGrade.D));

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            rma.decideRefund(DECIDED_AT, REFUND_POLICY);

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.REJECTED),
                () -> assertThat(rma.version()).isEqualTo(8)
            );
        }

        @Test
        void shouldRebuildRejectedStateFromHistory() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "30.00"),
                validRequestedItem("item-2", PLN, "45.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.D),
                validInspectionResult("item-2", ItemGrade.D)
            );
            RefundDecided event = validRejectedRefundDecided();
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results),
                event
            ));
            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.REJECTED),
                () -> assertThat(rma.version()).isEqualTo(8),
                () -> assertThat(rma.refundAmount()).isEqualTo(event.refundAmount()),
                () -> assertThat(rma.deductionTotal()).isEqualTo(event.deductionTotal())
            );
        }

        @Test
        void shouldRejectRefundDecisionWhenRmaIsNotUnderInspection() {
            Rma rma = Rma.fromHistory(List.of(validRmaRequested()));

            assertThatThrownBy(() -> rma.decideRefund(DECIDED_AT, REFUND_POLICY))
                .isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage(
                    "Invalid RMA state transition from REQUESTED to REFUND_PENDING"
                );
        }

        @Test
        void shouldApplyGradeDeductionForChangedMind() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "30.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.B)
            );

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            RefundDecided event = rma.decideRefund(
                DECIDED_AT,
                REFUND_POLICY
            );

            ItemRefundDecision itemDecision = event.itemDecisions().getFirst();

            assertAll(
                () -> assertThat(itemDecision.deductionPercent()).isEqualTo(15),
                () -> assertThat(itemDecision.refundableAmount())
                    .isEqualTo(new Money(new BigDecimal("25.50"), PLN))
            );
        }

        @Test
        void shouldNotApplyGradeDeductionForReasonOtherThanChangedMind() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "30.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.C)
            );

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.DAMAGED_IN_TRANSIT, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            RefundDecided event = rma.decideRefund(
                DECIDED_AT,
                REFUND_POLICY
            );

            ItemRefundDecision itemDecision = event.itemDecisions().getFirst();

            assertAll(
                () -> assertThat(itemDecision.deductionPercent()).isZero(),
                () -> assertThat(itemDecision.refundableAmount())
                    .isEqualTo(new Money(new BigDecimal("30.00"), PLN))
            );
        }

        @Test
        void shouldApplyReturnShippingFeeForChangedMind() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "30.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.A)
            );

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            RefundDecided event = rma.decideRefund(DECIDED_AT, REFUND_POLICY);

            assertAll(
                () -> assertThat(event.refundAmount().amount())
                    .isEqualByComparingTo("15.00"),
                () -> assertThat(event.deductionTotal().amount())
                    .isEqualByComparingTo("15.00")
            );
        }

        @Test
        void shouldNotApplyReturnShippingFeeForReasonOtherThanChangedMind() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "30.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.A)
            );

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.DAMAGED_IN_TRANSIT, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            RefundDecided event = rma.decideRefund(DECIDED_AT, REFUND_POLICY);

            assertAll(
                () -> assertThat(event.refundAmount().amount())
                    .isEqualByComparingTo("30.00"),
                () -> assertThat(event.deductionTotal().amount())
                    .isEqualByComparingTo("0.00")
            );
        }

        @Test
        void shouldCalculateRefundForMultipleItems() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "100.00"),
                validRequestedItem("item-2", PLN, "50.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.B),
                validInspectionResult("item-2", ItemGrade.C)
            );

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            RefundDecided event = rma.decideRefund(DECIDED_AT, REFUND_POLICY);

            assertAll(
                () -> assertThat(event.refundAmount().amount())
                    .isEqualByComparingTo("100.00"),
                () -> assertThat(event.deductionTotal().amount())
                    .isEqualByComparingTo("50.00")
            );
        }

        @Test
        void shouldRoundEachItemBeforeSumming() {
            RefundPolicy policyWithoutShipping = new RefundPolicy(
                0,
                15,
                40,
                new BigDecimal("0.00")
            );

            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "0.03"),
                validRequestedItem("item-2", PLN, "0.03")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.B),
                validInspectionResult("item-2", ItemGrade.B)
            );

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            RefundDecided event = rma.decideRefund(
                DECIDED_AT,
                policyWithoutShipping
            );

            assertAll(
                () -> assertThat(event.refundAmount().amount())
                    .isEqualByComparingTo("0.06"),
                () -> assertThat(event.deductionTotal().amount())
                    .isEqualByComparingTo("0.00")
            );
        }

        @Test
        void shouldStoreRefundAmountAndDeductionTotalAfterRefundDecision() {
            Rma rma = rmaAfterInspection();

            RefundDecided event = rma.decideRefund(
                DECIDED_AT,
                REFUND_POLICY
            );

            assertAll(
                () -> assertThat(rma.refundAmount())
                    .isEqualTo(event.refundAmount()),
                () -> assertThat(rma.deductionTotal())
                    .isEqualTo(event.deductionTotal())
            );
        }

        @Test
        void shouldApplyRefundDecisionToRmaItems() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "100.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.B)
            );

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            rma.decideRefund(DECIDED_AT, REFUND_POLICY);

            RmaItem item = rma.items().getFirst();

            assertAll(
                () -> assertThat(item.deductionPercent()).isEqualTo(15),
                () -> assertThat(item.refundableAmount())
                    .isEqualTo(new Money(new BigDecimal("85.00"), PLN))
            );
        }

        @Test
        void shouldRejectRefundWhenCalculatedRefundAmountIsZero() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "15.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.A)
            );

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            RefundDecided event = rma.decideRefund(
                DECIDED_AT,
                REFUND_POLICY
            );

            assertAll(
                () -> assertThat(event.refundAmount().amount())
                    .isEqualByComparingTo("0.00"),
                () -> assertThat(event.decision())
                    .isEqualTo(RefundDecision.REJECTED)
            );
        }

        @Test
        void shouldNeverCalculateNegativeRefundAmount() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "10.00"),
                validRequestedItem("item-2", PLN, "100.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.A),
                validInspectionResult("item-2", ItemGrade.D)
            );

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            RefundDecided event = rma.decideRefund(
                DECIDED_AT,
                REFUND_POLICY
            );

            assertThat(event.refundAmount().amount())
                .isEqualByComparingTo("0.00");
        }

        @Test
        void shouldNeverCalculateRefundAmountGreaterThanTotalItemValue() {
            List<RequestedItem> items = List.of(
                validRequestedItem("item-1", PLN, "100.00")
            );

            List<InspectionResult> results = List.of(
                validInspectionResult("item-1", ItemGrade.B)
            );

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(RmaReason.CHANGED_MIND, items),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(results)
            ));

            RefundDecided event = rma.decideRefund(
                DECIDED_AT,
                REFUND_POLICY
            );

            BigDecimal totalItemValue = items.stream()
                .map(item -> item.unitPrice().amount()
                    .multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(event.refundAmount().amount())
                .isLessThanOrEqualTo(totalItemValue);
        }
    }

    @Nested
    class RefundExecutedTests {
        @Test
        void shouldExecuteRefundFromRefundPending() {
            Rma rma = rmaRefundPending();

            RefundExecuted event = rma.executeRefund(
                PAYOUT_ID,
                rma.refundAmount(),
                PAYOUT_ATTEMPT,
                EXECUTED_AT
            );

            assertAll(
                () -> assertThat(event.payoutId()).isEqualTo(PAYOUT_ID),
                () -> assertThat(event.amount()).isEqualTo(rma.refundAmount()),
                () -> assertThat(event.attempt()).isEqualTo(PAYOUT_ATTEMPT),
                () -> assertThat(event.executedAt()).isEqualTo(EXECUTED_AT)
            );
        }

        @Test
        void shouldMoveToRefundedAfterRefundExecution() {
            Rma rma = rmaRefundPending();

            rma.executeRefund(
                PAYOUT_ID,
                rma.refundAmount(),
                PAYOUT_ATTEMPT,
                EXECUTED_AT
            );

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.REFUNDED),
                () -> assertThat(rma.version()).isEqualTo(9)
            );
        }

        @Test
        void shouldRebuildRefundedStateFromHistory() {
            RefundExecuted event = validRefundExecuted();

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(),
                validFullRefundDecided(),
                event
            ));

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.REFUNDED),
                () -> assertThat(rma.version()).isEqualTo(9),
                () -> assertThat(rma.payoutId()).isEqualTo(event.payoutId()),
                () -> assertThat(rma.payoutAttempt()).isEqualTo(event.attempt())
            );
        }

        @Test
        void shouldRejectRefundExecutionWhenRmaIsNotRefundPending() {
            Rma rma = Rma.fromHistory(List.of(validRmaRequested()));

            assertThatThrownBy(() -> rma.executeRefund(
                PAYOUT_ID,
                new Money(new BigDecimal("15.00"), PLN),
                PAYOUT_ATTEMPT,
                EXECUTED_AT
            ))
                .isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage(
                    "Invalid RMA state transition from REQUESTED to REFUNDED"
                );
        }

        @Test
        void shouldRejectRefundExecutionWhenAmountDiffersFromDecision() {
            Rma rma = rmaRefundPending();

            Money differentAmount =
                new Money(new BigDecimal("10.00"), PLN);

            assertThatThrownBy(() -> rma.executeRefund(
                PAYOUT_ID,
                differentAmount,
                PAYOUT_ATTEMPT,
                EXECUTED_AT
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Executed refund amount must match decided refund amount");
        }

        @Test
        void shouldStorePayoutIdAfterRefundExecution() {
            Rma rma = rmaRefundPending();

            rma.executeRefund(
                PAYOUT_ID,
                rma.refundAmount(),
                PAYOUT_ATTEMPT,
                EXECUTED_AT
            );

            assertThat(rma.payoutId()).isEqualTo(PAYOUT_ID);
        }

        @Test
        void shouldStorePayoutAttemptAfterRefundExecution() {
            Rma rma = rmaRefundPending();

            rma.executeRefund(
                PAYOUT_ID,
                rma.refundAmount(),
                PAYOUT_ATTEMPT,
                EXECUTED_AT
            );

            assertThat(rma.payoutAttempt()).isEqualTo(PAYOUT_ATTEMPT);
        }
    }

    @Nested
    class RefundFailedTests {
        @Test
        void shouldFailRefundFromRefundPending() {
            Rma rma = rmaRefundPending();

            RefundFailed event = rma.failRefund(
                FailureCode.INSUFFICIENT_FUNDS,
                "Insufficient funds",
                1,
                false,
                FAILED_AT
            );

            assertAll(
                () -> assertThat(event.failureCode())
                    .isEqualTo(FailureCode.INSUFFICIENT_FUNDS),
                () -> assertThat(event.failureMessage())
                    .isEqualTo("Insufficient funds"),
                () -> assertThat(event.attempt()).isEqualTo(1),
                () -> assertThat(event.retryable()).isFalse(),
                () -> assertThat(event.failedAt()).isEqualTo(FAILED_AT)
            );
        }

        @Test
        void shouldMoveToRefundFailedAfterRefundFailure() {
            Rma rma = rmaRefundPending();

            rma.failRefund(
                FailureCode.INSUFFICIENT_FUNDS,
                "Insufficient funds",
                1,
                false,
                FAILED_AT
            );

            assertAll(
                () -> assertThat(rma.status())
                    .isEqualTo(RmaStatus.REFUND_FAILED),
                () -> assertThat(rma.version()).isEqualTo(9)
            );
        }

        @Test
        void shouldRebuildRefundFailedStateFromHistory() {
            RefundFailed event = validRefundFailed();

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(),
                validFullRefundDecided(),
                event
            ));

            assertAll(
                () -> assertThat(rma.status())
                    .isEqualTo(RmaStatus.REFUND_FAILED),
                () -> assertThat(rma.version()).isEqualTo(9),
                () -> assertThat(rma.failureCode())
                    .isEqualTo(event.failureCode()),
                () -> assertThat(rma.payoutAttempt())
                    .isEqualTo(event.attempt())
            );
        }

        @Test
        void shouldRejectRefundFailureWhenRmaIsNotRefundPending() {
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested()
            ));

            assertThatThrownBy(() -> rma.failRefund(
                FailureCode.INSUFFICIENT_FUNDS,
                "Insufficient funds",
                1,
                false,
                FAILED_AT
            ))
                .isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage(
                    "Invalid RMA state transition from REQUESTED to REFUND_FAILED"
                );
        }

        @Test
        void shouldStoreFailureCodeAfterRefundFailure() {
            Rma rma = rmaRefundPending();

            rma.failRefund(
                FailureCode.INVALID_ACCOUNT,
                "Invalid account",
                1,
                false,
                FAILED_AT
            );

            assertThat(rma.failureCode())
                .isEqualTo(FailureCode.INVALID_ACCOUNT);
        }

        @Test
        void shouldStorePayoutAttemptAfterRefundFailure() {
            Rma rma = rmaRefundPending();

            rma.failRefund(
                FailureCode.TIMEOUT,
                "Provider timeout",
                3,
                true,
                FAILED_AT
            );

            assertThat(rma.payoutAttempt()).isEqualTo(3);
        }

    }

    @Nested
    class RefundRetryTests {
        @Test
        void shouldRetryRefundFromRefundFailed() {
            Rma rma = rmaRefundFailed();

            RefundDecided event = rma.retryRefund(RETRY_DECIDED_AT);

            assertAll(
                () -> assertThat(event.decision())
                    .isEqualTo(RefundDecision.FULL),
                () -> assertThat(event.refundAmount())
                    .isEqualTo(rma.refundAmount()),
                () -> assertThat(event.deductionTotal())
                    .isEqualTo(rma.deductionTotal()),
                () -> assertThat(event.decidedAt())
                    .isEqualTo(RETRY_DECIDED_AT)
            );
        }

        @Test
        void shouldMoveToRefundPendingAfterManualRefundRetry() {
            Rma rma = rmaRefundFailed();

            rma.retryRefund(RETRY_DECIDED_AT);

            assertAll(
                () -> assertThat(rma.status())
                    .isEqualTo(RmaStatus.REFUND_PENDING),
                () -> assertThat(rma.version()).isEqualTo(10)
            );
        }

        @Test
        void shouldIncreasePayoutAttemptAfterManualRefundRetry() {
            Rma rma = rmaRefundFailed();

            assertThat(rma.payoutAttempt()).isEqualTo(1);

            rma.retryRefund(RETRY_DECIDED_AT);

            assertThat(rma.payoutAttempt()).isEqualTo(2);
        }

        @Test
        void shouldPreserveRefundAmountsAfterManualRefundRetry() {
            Rma rma = rmaRefundFailed();

            Money refundAmount = rma.refundAmount();
            Money deductionTotal = rma.deductionTotal();

            rma.retryRefund(RETRY_DECIDED_AT);

            assertAll(
                () -> assertThat(rma.refundAmount())
                    .isEqualTo(refundAmount),
                () -> assertThat(rma.deductionTotal())
                    .isEqualTo(deductionTotal)
            );
        }

        @Test
        void shouldRebuildRefundPendingStateAfterManualRefundRetry() {
            RefundDecided retryEvent = validRetryRefundDecided();

            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(),
                validLabelIssued(),
                validPickupScheduled(),
                validParcelInTransit(),
                validParcelReceived(),
                validInspectionStarted(),
                validInspectionCompleted(),
                validFullRefundDecided(),
                validRefundFailed(),
                retryEvent
            ));

            assertAll(
                () -> assertThat(rma.status())
                    .isEqualTo(RmaStatus.REFUND_PENDING),
                () -> assertThat(rma.version()).isEqualTo(10),
                () -> assertThat(rma.payoutAttempt()).isEqualTo(2),
                () -> assertThat(rma.refundAmount())
                    .isEqualTo(retryEvent.refundAmount())
            );
        }

        @Test
        void shouldRejectManualRefundRetryWhenRmaIsNotRefundFailed() {
            Rma rma = rmaRefundPending();

            assertThatThrownBy(() ->
                rma.retryRefund(RETRY_DECIDED_AT)
            )
                .isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage(
                    "Invalid RMA state transition from REFUND_PENDING to REFUND_PENDING"
                );
        }
    }

    @Nested
    class ExpirationTests {
        private final Instant expiredAt = REQUESTED_AT.plus(14, ChronoUnit.DAYS);

        @Test
        void shouldExpireFromEachStateBeforeShipment() {
            List<DomainEvent> history = List.of(
                validRmaRequested(), validLabelIssued(), validPickupScheduled()
            );

            for (int length = 1; length <= history.size(); length++) {
                Rma rma = Rma.fromHistory(history.subList(0, length));
                List<RmaItem> items = rma.items();

                RmaExpired event = rma.expire(expiredAt, 14);

                assertAll(
                    () -> assertThat(event).isEqualTo(new RmaExpired(expiredAt, 14)),
                    () -> assertThat(rma.status()).isEqualTo(RmaStatus.EXPIRED),
                    () -> assertThat(rma.items()).isEqualTo(items),
                    () -> assertThat(rma.businessNumber()).isEqualTo(BUSINESS_NUMBER),
                    () -> assertThat(rma.closedAt()).isNull(),
                    () -> assertThat(rma.closureReason()).isNull()
                );
                assertThat(rma.version()).isEqualTo(length + 1);
            }
        }

        @Test
        void shouldRebuildExpiredStateFromHistory() {
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(), validLabelIssued(), validPickupScheduled(),
                new RmaExpired(expiredAt, 14)
            ));

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.EXPIRED),
                () -> assertThat(rma.version()).isEqualTo(4),
                () -> assertThat(rma.closedAt()).isNull(),
                () -> assertThat(rma.closureReason()).isNull()
            );
        }

        @Test
        void shouldRejectExpirationAfterShipmentWithoutChangingState() {
            List<DomainEvent> history = List.of(
                validRmaRequested(), validLabelIssued(), validPickupScheduled(),
                validParcelInTransit(), validParcelReceived(), validInspectionStarted(),
                validInspectionCompleted(), validFullRefundDecided()
            );

            for (int length = 4; length <= history.size(); length++) {
                assertExpirationRejected(Rma.fromHistory(history.subList(0, length)));
            }

            assertExpirationRejected(rmaRefundFailed());
            Rma refunded = rmaRefundPending();
            refunded.executeRefund(PAYOUT_ID, refunded.refundAmount(), 1, EXECUTED_AT);
            assertExpirationRejected(refunded);

            Rma rejected = Rma.fromHistory(List.of(
                validRmaRequested(), validLabelIssued(), validPickupScheduled(),
                validParcelInTransit(), validParcelReceived(), validInspectionStarted(),
                validInspectionCompleted(List.of(validInspectionResult("item-1", ItemGrade.D)))
            ));
            rejected.decideRefund(DECIDED_AT, REFUND_POLICY);
            assertExpirationRejected(rejected);
        }

        @Test
        void shouldRejectRepeatedExpiration() {
            Rma rma = Rma.fromHistory(List.of(validRmaRequested()));
            rma.expire(expiredAt, 14);

            assertExpirationRejected(rma);
        }

        private void assertExpirationRejected(Rma rma) {
            RmaStatus status = rma.status();
            int version = rma.version();
            List<RmaItem> items = rma.items();

            assertThatThrownBy(() -> rma.expire(expiredAt, 14))
                .isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage("Invalid RMA state transition from " + status + " to EXPIRED");

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(status),
                () -> assertThat(rma.version()).isEqualTo(version),
                () -> assertThat(rma.items()).isEqualTo(items)
            );
        }
    }

    @Nested
    class LostTests {
        private final Instant declaredLostAt = COLLECTED_AT.plus(10, ChronoUnit.DAYS);
        private final String lastKnownStatus = "IN_TRANSIT";

        @Test
        void shouldDeclareParcelLostFromInTransit() {
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(), validLabelIssued(), validPickupScheduled(),
                validParcelInTransit()
            ));
            List<RmaItem> items = rma.items();

            RmaLost event = rma.declareLost(lastKnownStatus, declaredLostAt);

            assertAll(
                () -> assertThat(event).isEqualTo(new RmaLost(lastKnownStatus, declaredLostAt)),
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.LOST),
                () -> assertThat(rma.version()).isEqualTo(5),
                () -> assertThat(rma.items()).isEqualTo(items),
                () -> assertThat(rma.businessNumber()).isEqualTo(BUSINESS_NUMBER),
                () -> assertThat(rma.closedAt()).isNull(),
                () -> assertThat(rma.closureReason()).isNull()
            );
        }

        @Test
        void shouldRebuildLostStateFromHistory() {
            Rma rma = lostRma();

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.LOST),
                () -> assertThat(rma.version()).isEqualTo(5),
                () -> assertThat(rma.closedAt()).isNull(),
                () -> assertThat(rma.closureReason()).isNull()
            );
        }

        @Test
        void shouldRejectDeclarationOutsideInTransitWithoutChangingState() {
            List<DomainEvent> history = List.of(
                validRmaRequested(), validLabelIssued(), validPickupScheduled(),
                validParcelInTransit(), validParcelReceived(), validInspectionStarted(),
                validInspectionCompleted(), validFullRefundDecided()
            );

            for (int length = 1; length <= history.size(); length++) {
                if (length != 4) {
                    assertDeclarationRejected(Rma.fromHistory(history.subList(0, length)));
                }
            }

            assertDeclarationRejected(rmaRefundFailed());
            Rma refunded = rmaRefundPending();
            refunded.executeRefund(PAYOUT_ID, refunded.refundAmount(), 1, EXECUTED_AT);
            assertDeclarationRejected(refunded);

            Rma rejected = Rma.fromHistory(List.of(
                validRmaRequested(), validLabelIssued(), validPickupScheduled(),
                validParcelInTransit(), validParcelReceived(), validInspectionStarted(),
                validInspectionCompleted(List.of(validInspectionResult("item-1", ItemGrade.D)))
            ));
            rejected.decideRefund(DECIDED_AT, REFUND_POLICY);
            assertDeclarationRejected(rejected);

            Rma expired = Rma.fromHistory(List.of(validRmaRequested()));
            expired.expire(declaredLostAt, 14);
            assertDeclarationRejected(expired);
            assertDeclarationRejected(lostRma());
        }

        @Test
        void shouldRejectLateParcelReceiptAfterDeclaration() {
            Rma rma = lostRma();

            assertThatThrownBy(() -> rma.receive(declaredLostAt, WAREHOUSE_ID))
                .isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage("Invalid RMA state transition from LOST to RECEIVED");

            assertThat(rma.status()).isEqualTo(RmaStatus.LOST);
            assertThat(rma.version()).isEqualTo(5);
            assertThat(rma.parcelReceivedAt()).isNull();
        }

        private Rma lostRma() {
            return Rma.fromHistory(List.of(
                validRmaRequested(), validLabelIssued(), validPickupScheduled(),
                validParcelInTransit(), new RmaLost(lastKnownStatus, declaredLostAt)
            ));
        }

        private void assertDeclarationRejected(Rma rma) {
            RmaStatus status = rma.status();
            int version = rma.version();
            List<RmaItem> items = rma.items();

            assertThatThrownBy(() -> rma.declareLost(lastKnownStatus, declaredLostAt))
                .isInstanceOf(InvalidRmaStateTransitionException.class)
                .hasMessage("Invalid RMA state transition from " + status + " to LOST");

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(status),
                () -> assertThat(rma.version()).isEqualTo(version),
                () -> assertThat(rma.items()).isEqualTo(items)
            );
        }
    }

    @Nested
    class CancellationTests {
        private final Instant cancelledAt = REQUESTED_AT.plus(1, ChronoUnit.DAYS);

        @Test
        void shouldCancelFromEachStateBeforeShipment() {
            List<DomainEvent> history = List.of(
                validRmaRequested(), validLabelIssued(), validPickupScheduled()
            );

            for (int length = 1; length <= history.size(); length++) {
                Rma rma = Rma.fromHistory(history.subList(0, length));
                List<RmaItem> items = rma.items();
                RmaReason originalReason = rma.reason();

                RmaCancelled event = rma.cancel(CUSTOMER_ID, cancelledAt, "Wycofuję zgłoszenie");

                assertAll(
                    () -> assertThat(event).isEqualTo(
                        new RmaCancelled(CUSTOMER_ID, cancelledAt, "Wycofuję zgłoszenie")),
                    () -> assertThat(rma.status()).isEqualTo(RmaStatus.CANCELLED),
                    () -> assertThat(rma.items()).isEqualTo(items),
                    () -> assertThat(rma.reason()).isEqualTo(originalReason),
                    () -> assertThat(rma.businessNumber()).isEqualTo(BUSINESS_NUMBER),
                    () -> assertThat(rma.closedAt()).isNull(),
                    () -> assertThat(rma.closureReason()).isNull()
                );
                assertThat(rma.version()).isEqualTo(length + 1);
            }
        }

        @Test
        void shouldAllowCancellationWithoutNote() {
            Rma rma = Rma.fromHistory(List.of(validRmaRequested()));

            RmaCancelled event = rma.cancel(CUSTOMER_ID, cancelledAt, null);

            assertThat(event.reason()).isNull();
            assertThat(rma.status()).isEqualTo(RmaStatus.CANCELLED);
        }

        @Test
        void shouldRebuildCancelledStateFromHistory() {
            Rma rma = Rma.fromHistory(List.of(
                validRmaRequested(), validLabelIssued(), validPickupScheduled(),
                new RmaCancelled(CUSTOMER_ID, cancelledAt, null)
            ));

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(RmaStatus.CANCELLED),
                () -> assertThat(rma.version()).isEqualTo(4),
                () -> assertThat(rma.closedAt()).isNull(),
                () -> assertThat(rma.closureReason()).isNull()
            );
        }

        @Test
        void shouldRejectCancellationAfterShipmentOrTerminationWithoutChangingState() {
            List<DomainEvent> history = List.of(
                validRmaRequested(), validLabelIssued(), validPickupScheduled(),
                validParcelInTransit(), validParcelReceived(), validInspectionStarted(),
                validInspectionCompleted(), validFullRefundDecided()
            );

            for (int length = 4; length <= history.size(); length++) {
                assertCancellationRejected(Rma.fromHistory(history.subList(0, length)));
            }

            assertCancellationRejected(rmaRefundFailed());
            Rma refunded = rmaRefundPending();
            refunded.executeRefund(PAYOUT_ID, refunded.refundAmount(), 1, EXECUTED_AT);
            assertCancellationRejected(refunded);

            Rma rejected = Rma.fromHistory(List.of(
                validRmaRequested(), validLabelIssued(), validPickupScheduled(),
                validParcelInTransit(), validParcelReceived(), validInspectionStarted(),
                validInspectionCompleted(List.of(validInspectionResult("item-1", ItemGrade.D)))
            ));
            rejected.decideRefund(DECIDED_AT, REFUND_POLICY);
            assertCancellationRejected(rejected);

            Rma expired = Rma.fromHistory(List.of(validRmaRequested()));
            expired.expire(cancelledAt, 14);
            assertCancellationRejected(expired);

            Rma lost = Rma.fromHistory(history.subList(0, 4));
            lost.declareLost("IN_TRANSIT", cancelledAt);
            assertCancellationRejected(lost);

            Rma cancelled = Rma.fromHistory(List.of(validRmaRequested()));
            cancelled.cancel(CUSTOMER_ID, cancelledAt, null);
            assertCancellationRejected(cancelled);
        }

        private void assertCancellationRejected(Rma rma) {
            RmaStatus status = rma.status();
            int version = rma.version();
            List<RmaItem> items = rma.items();

            assertThatThrownBy(() -> rma.cancel(CUSTOMER_ID, cancelledAt, null))
                .isInstanceOfSatisfying(RmaDomainException.class,
                    exception -> assertThat(exception.code())
                        .isEqualTo(RmaErrorCode.CANCELLATION_NOT_ALLOWED));

            assertAll(
                () -> assertThat(rma.status()).isEqualTo(status),
                () -> assertThat(rma.version()).isEqualTo(version),
                () -> assertThat(rma.items()).isEqualTo(items)
            );
        }
    }

    @Nested
    class HistoryTests {
        @Test
        void shouldRejectEmptyHistory() {
            assertThatThrownBy(() -> Rma.fromHistory(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event history must not be empty");
        }

        @Test
        void shouldRequireRmaRequestedAsFirstEvent() {
            DomainEvent wrongFirstEvent = validLabelIssued();
            assertThatThrownBy(() -> Rma.fromHistory(List.of(wrongFirstEvent)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RmaRequested must be the first event");
        }
    }

    private Rma rmaUnderInspection() {
        return Rma.fromHistory(List.of(
            validRmaRequested(),
            validLabelIssued(),
            validPickupScheduled(),
            validParcelInTransit(),
            validParcelReceived(),
            validInspectionStarted()
        ));
    }

    private Rma rmaAfterInspection() {
        return Rma.fromHistory(List.of(
            validRmaRequested(),
            validLabelIssued(),
            validPickupScheduled(),
            validParcelInTransit(),
            validParcelReceived(),
            validInspectionStarted(),
            validInspectionCompleted()
        ));
    }

    private Rma rmaRefundPending() {
        return Rma.fromHistory(List.of(
            validRmaRequested(),
            validLabelIssued(),
            validPickupScheduled(),
            validParcelInTransit(),
            validParcelReceived(),
            validInspectionStarted(),
            validInspectionCompleted(),
            validFullRefundDecided()
        ));
    }

    private Rma rmaRefundFailed() {
        return Rma.fromHistory(List.of(
            validRmaRequested(),
            validLabelIssued(),
            validPickupScheduled(),
            validParcelInTransit(),
            validParcelReceived(),
            validInspectionStarted(),
            validInspectionCompleted(),
            validFullRefundDecided(),
            validRefundFailed()
        ));
    }

    private RmaRequested validRmaRequested(
        RmaReason reason,
        List<RequestedItem> items
    ) {
        return new RmaRequested(
            RMA_ID,
            BUSINESS_NUMBER,
            ORDER_ID,
            CUSTOMER_ID,
            DELIVERED_AT,
            REQUESTED_AT,
            reason,
            "Nie pasuje",
            PLN,
            items
        );
    }

    private RmaRequested validRmaRequested() {
        List<RequestedItem> items = List.of(validRequestedItem());
        return validRmaRequested(RmaReason.CHANGED_MIND, items);
    }

    private PickupScheduled validPickupScheduled() {
        return new PickupScheduled(PICKUP_ID, SCHEDULED_FOR);
    }

    private LabelIssued validLabelIssued() {
        return new LabelIssued(
            LABEL_ID,
            TRACKING_NUMBER,
            LABEL_FILE_KEY,
            ISSUED_AT
        );
    }

    private ParcelInTransit validParcelInTransit() {
        return new ParcelInTransit(COLLECTED_AT);
    }

    private ParcelReceived validParcelReceived() {
        return new ParcelReceived(RECEIVED_AT, WAREHOUSE_ID);
    }

    private InspectionStarted validInspectionStarted() {
        return new InspectionStarted(INSPECTOR_ID, STARTED_AT);
    }

    private InspectionCompleted validInspectionCompleted(
        List<InspectionResult> results
    ) {
        return new InspectionCompleted(
            INSPECTOR_ID,
            COMPLETED_AT,
            results,
            null
        );
    }

    private InspectionCompleted validInspectionCompleted() {
        return validInspectionCompleted(List.of(validInspectionResult()));
    }

    private RefundDecided validFullRefundDecided() {
        return new RefundDecided(
            RefundDecision.FULL,
            new Money(new BigDecimal("15.00"), PLN),
            new Money(new BigDecimal("15.00"), PLN),
            List.of(
                new ItemRefundDecision(
                    "item-1",
                    ItemGrade.A,
                    0,
                    new Money(new BigDecimal("30.00"), PLN)
                )
            ),
            null,
            DECIDED_AT
        );
    }

    private RefundDecided validRejectedRefundDecided() {
        return new RefundDecided(
            RefundDecision.REJECTED,
            new Money(new BigDecimal("0.00"), PLN),
            new Money(new BigDecimal("0.00"), PLN),
            List.of(
                new ItemRefundDecision(
                    "item-1",
                    ItemGrade.D,
                    0,
                    new Money(new BigDecimal("0.00"), PLN)
                ),
                new ItemRefundDecision(
                    "item-2",
                    ItemGrade.D,
                    0,
                    new Money(new BigDecimal("0.00"), PLN)
                )
            ),
            "All items rejected after inspection",
            DECIDED_AT
        );
    }

    private RefundExecuted validRefundExecuted() {
        return new RefundExecuted(
            PAYOUT_ID,
            new Money(new BigDecimal("15.00"), PLN),
            PAYOUT_ATTEMPT,
            EXECUTED_AT
        );
    }

    private RefundFailed validRefundFailed() {
        return new RefundFailed(
            FailureCode.INSUFFICIENT_FUNDS,
            "Insufficient funds",
            1,
            false,
            FAILED_AT
        );
    }

    private RefundDecided validRetryRefundDecided() {
        RefundDecided original = validFullRefundDecided();

        return new RefundDecided(
            original.decision(),
            original.refundAmount(),
            original.deductionTotal(),
            original.itemDecisions(),
            null,
            RETRY_DECIDED_AT
        );
    }

    private RequestedItem validRequestedItem(
        String itemId,
        Currency currency,
        String amount
    ) {
        return new RequestedItem(
            itemId,
            "sku-" + itemId,
            "book",
            1,
            new Money(new BigDecimal(amount), currency)
        );
    }

    private RequestedItem validRequestedItem() {
        return validRequestedItem("item-1", PLN, "30.00");
    }

    private InspectionResult validInspectionResult(
        String itemId,
        ItemGrade grade
    ) {
        return new InspectionResult(
            itemId,
            grade,
            "Item looks good",
            List.of()
        );
    }

    private InspectionResult validInspectionResult(ItemGrade grade) {
        return validInspectionResult("item-1", grade);
    }

    private InspectionResult validInspectionResult() {
        return validInspectionResult("item-1", ItemGrade.A);
    }
}
