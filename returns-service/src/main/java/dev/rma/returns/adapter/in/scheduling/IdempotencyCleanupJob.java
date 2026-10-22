package dev.rma.returns.adapter.in.scheduling;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.rma.returns.application.port.out.IdempotencyStore;

@Component
public class IdempotencyCleanupJob {
    private final IdempotencyStore store;
    private final Clock clock;
    private final Duration retention;

    public IdempotencyCleanupJob(
            IdempotencyStore store, Clock clock, @Value("${returns.idempotency.retention:PT24H}") String retention) {
        var duration = Duration.parse(retention);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Idempotency retention must be positive");
        }
        this.store = store;
        this.clock = clock;
        this.retention = duration;
    }

    @Scheduled(fixedDelayString = "${returns.idempotency.cleanup-delay:PT1H}")
    @Transactional
    public int cleanup() {
        return store.deleteExpiredBefore(clock.instant().minus(retention));
    }
}
