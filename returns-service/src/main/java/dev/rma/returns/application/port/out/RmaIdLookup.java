package dev.rma.returns.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Odnajduje techniczny identyfikator zwrotu po jego numerze biznesowym. */
public interface RmaIdLookup {
    Optional<UUID> findId(String businessNumber);
}
