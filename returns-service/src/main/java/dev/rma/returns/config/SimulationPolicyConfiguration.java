package dev.rma.returns.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.rma.returns.domain.policy.InspectionPolicy;
import dev.rma.returns.domain.policy.RefundPolicy;

@Configuration
@EnableConfigurationProperties({ReturnsProperties.class, InspectionProperties.class})
public class SimulationPolicyConfiguration {
    @Bean
    public RefundPolicy refundPolicy(ReturnsProperties properties) {
        return new RefundPolicy(
                properties.deduction().gradeAPercent(),
                properties.deduction().gradeBPercent(),
                properties.deduction().gradeCPercent(),
                properties.returnShippingFee());
    }

    @Bean
    public InspectionPolicy inspectionPolicy(InspectionProperties properties) {
        return new InspectionPolicy(properties.supervisorApprovalThreshold());
    }
}
