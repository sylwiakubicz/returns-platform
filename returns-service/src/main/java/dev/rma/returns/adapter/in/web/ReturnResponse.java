package dev.rma.returns.adapter.in.web;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

import dev.rma.returns.application.model.ReturnView;
import dev.rma.returns.domain.enums.RmaStatus;

/** Dane odpowiedzi HTTP; kwota jest tekstem, zgodnie z kontraktem OpenAPI. */
public record ReturnResponse(
        String businessNumber,
        RmaStatus status,
        MoneyResponse refundAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt) {
    public static ReturnResponse from(ReturnView view) {
        var money = view.refundAmount();
        var amount = money == null
                ? null
                : new MoneyResponse(
                        money.amount().toPlainString(), money.currency().getCurrencyCode());
        return new ReturnResponse(view.businessNumber(), view.status(), amount, view.updatedAt());
    }

    public record MoneyResponse(String amount, String currency) {}
}
