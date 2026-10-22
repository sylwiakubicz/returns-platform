package dev.rma.returns.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inspection")
public record InspectionProperties(BigDecimal supervisorApprovalThreshold) {}
