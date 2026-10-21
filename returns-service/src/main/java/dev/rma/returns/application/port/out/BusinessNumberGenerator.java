package dev.rma.returns.application.port.out;

import java.time.Instant;

/** RMA-RRRR-NNNNNN. */
public interface BusinessNumberGenerator {
    String nextNumber(Instant requestedAt);
}
