package dev.rma.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;

public final class ArchitectureRules {

    public static final ArchRule COMMON_MUST_NOT_DEPEND_ON_SERVICES = noClasses()
            .that()
            .resideInAPackage("dev.rma.common..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.rma.returns..",
                    "dev.rma.logistics..",
                    "dev.rma.inspection..",
                    "dev.rma.settlement..",
                    "dev.rma.notification..")
            .because("ADR-0006: common holds technical infrastructure only");

    public static final ArchRule DOMAIN_MUST_BE_FRAMEWORK_FREE = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.apache.kafka..", "com.fasterxml.jackson..")
            .because("the domain model must not be coupled to technology");

    public static final ArchRule INBOUND_MUST_NOT_DEPEND_ON_OUTBOUND = noClasses()
            .that()
            .resideInAPackage("..adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter.out..")
            .because("adapters communicate through the application layer");

    public static final ArchRule PACKAGES_MUST_BE_FREE_OF_CYCLES =
            slices().matching("dev.rma.(*)..").should().beFreeOfCycles();

    private ArchitectureRules() {}
}
