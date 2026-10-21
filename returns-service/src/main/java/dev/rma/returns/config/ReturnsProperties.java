package dev.rma.returns.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "returns")
public record ReturnsProperties(BigDecimal returnShippingFee, Deduction deduction) {
    public record Deduction(int gradeAPercent, int gradeBPercent, int gradeCPercent) {}
}
