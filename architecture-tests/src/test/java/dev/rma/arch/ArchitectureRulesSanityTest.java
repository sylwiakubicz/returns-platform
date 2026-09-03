package dev.rma.arch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

class ArchitectureRulesSanityTest {

    private static final JavaClasses ALL_CLASSES_INCLUDING_VIOLATIONS =
            new ClassFileImporter().importPackages("dev.rma");

    @Test
    void commonRuleDetectsViolation() {
        assertDetectsViolation(ArchitectureRules.COMMON_MUST_NOT_DEPEND_ON_SERVICES);
    }

    @Test
    void domainRuleDetectsViolation() {
        assertDetectsViolation(ArchitectureRules.DOMAIN_MUST_BE_FRAMEWORK_FREE);
    }

    @Test
    void inboundRuleDetectsViolation() {
        assertDetectsViolation(ArchitectureRules.INBOUND_MUST_NOT_DEPEND_ON_OUTBOUND);
    }

    private void assertDetectsViolation(ArchRule rule) {
        assertThat(rule.evaluate(ALL_CLASSES_INCLUDING_VIOLATIONS).hasViolation())
                .as("rule %s must report the intentional violation fixture", rule.getDescription())
                .isTrue();
    }
}
