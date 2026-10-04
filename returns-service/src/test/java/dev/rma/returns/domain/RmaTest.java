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
