package dev.rma.returns.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

public class Rma {
    private final UUID rmaId;
    private String businessNumber;
    private String orderId;
    private String customerId;
    private Instant orderDeliveredAT;
    private RmaReason reason;
    private String customerNote;
    private RmaStatus status;
    private Integer version;
    private List<RmaItem> items;
    private Currency currency;
    private String labelId;
    private String trackingNumber;
    private LocalDate pickupScheduledFor;
    private Instant parcelReceivedAt;
    private Instant inspectedAt;
    private String inspectorId;
    private Money refundAmount;
    private Money deductionTotal;
    private Integer payoutAttempt;
    private String payoutId;
    private FailureCode failureCode;
    private Instant closedAt;
    private ClosureReason closureReason;


    public Rma(UUID rmaId) {
        this.rmaId = rmaId;
    }


}
