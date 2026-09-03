package dev.rma.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.rma", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule commonMustNotDependOnServices = ArchitectureRules.COMMON_MUST_NOT_DEPEND_ON_SERVICES;

    @ArchTest
    static final ArchRule domainMustBeFrameworkFree = ArchitectureRules.DOMAIN_MUST_BE_FRAMEWORK_FREE;

    @ArchTest
    static final ArchRule inboundMustNotDependOnOutbound = ArchitectureRules.INBOUND_MUST_NOT_DEPEND_ON_OUTBOUND;

    @ArchTest
    static final ArchRule packagesMustBeFreeOfCycles = ArchitectureRules.PACKAGES_MUST_BE_FREE_OF_CYCLES;
}
