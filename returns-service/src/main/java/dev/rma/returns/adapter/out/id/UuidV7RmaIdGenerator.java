package dev.rma.returns.adapter.out.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.rma.returns.application.port.out.RmaIdGenerator;

/** UUIDv7 według RFC 9562, sekcja 5.7; bez gwarancji kolejności w tej samej milisekundzie. */
@Component
public class UuidV7RmaIdGenerator implements RmaIdGenerator {
    private static final long MAX_TIMESTAMP = 0xFFFF_FFFF_FFFFL;
    private static final long VERSION_7 = 0x7000L;
    private static final long RANDOM_A_MASK = 0x0FFFL;
    private static final long IETF_VARIANT = 0x8000_0000_0000_0000L;
    private static final long RANDOM_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UuidV7RmaIdGenerator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public UUID nextId() {
        long timestamp = clock.millis();
        if (timestamp < 0 || timestamp > MAX_TIMESTAMP) {
            throw new IllegalStateException("Timestamp outside the UUIDv7 48-bit range");
        }
        // Pierwsze 64 bity: 48 bitów czasu, 4 bity wersji, 12 bitów losowych.
        long mostSignificantBits = (timestamp << 16) | VERSION_7 | (random.nextLong() & RANDOM_A_MASK);
        // Drugie 64 bity: 2 bity wariantu (10), 62 bity losowe.
        long leastSignificantBits = IETF_VARIANT | (random.nextLong() & RANDOM_B_MASK);
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
