package dev.rma.common.violation;

import dev.rma.returns.ReturnsApplication;

/** Intentional ADR-0006 violation, used by ArchitectureRulesSanityTest. */
public class CommonDependsOnService {
    public Class<?> leak() {
        return ReturnsApplication.class;
    }
}
