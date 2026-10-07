package dev.rma.returns.domain.policy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record InspectionPolicy(
    BigDecimal supervisorApprovalThreshold
) {
    public InspectionPolicy {
        Objects.requireNonNull(supervisorApprovalThreshold,
            "supervisorApprovalThreshold must not be null");
        if (supervisorApprovalThreshold.signum() < 0) {
            throw new IllegalArgumentException("supervisorApprovalThreshold must not be negative");
        }
        supervisorApprovalThreshold = supervisorApprovalThreshold.setScale(2, RoundingMode.UNNECESSARY);
    }
}
