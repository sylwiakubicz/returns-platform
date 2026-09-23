package dev.rma.returns.domain.exceptions;

import dev.rma.returns.domain.enums.RmaStatus;

public class InvalidRmaStateTransitionException extends RuntimeException {
    public InvalidRmaStateTransitionException(RmaStatus from, RmaStatus to) {
        super("Invalid RMA state transition from " + from + " to " + to);
    }
}
