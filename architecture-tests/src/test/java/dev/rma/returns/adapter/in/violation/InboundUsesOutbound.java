package dev.rma.returns.adapter.in.violation;

import dev.rma.returns.adapter.out.violation.OutboundCollaborator;

/** Intentional inbound-to-outbound dependency, used by ArchitectureRulesSanityTest. */
public class InboundUsesOutbound {
    private final OutboundCollaborator collaborator = new OutboundCollaborator();

    public Object use() {
        return collaborator;
    }
}
