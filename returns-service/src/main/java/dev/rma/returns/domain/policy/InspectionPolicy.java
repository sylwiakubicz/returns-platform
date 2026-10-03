package dev.rma.returns.domain.policy;
import java.math.BigDecimal;

public record InspectionPolicy(
    BigDecimal supervisorApprovalThreshold
) {
}
