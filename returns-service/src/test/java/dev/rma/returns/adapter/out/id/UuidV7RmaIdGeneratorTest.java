package dev.rma.returns.adapter.out.id;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UuidV7RmaIdGeneratorTest {
    @Test
    void encodesVersionVariantAndTimeFromInjectedClock() {
        var now = Instant.parse("2026-10-21T10:00:00Z");
        var generator = new UuidV7RmaIdGenerator(Clock.fixed(now, ZoneOffset.UTC));

        var id = generator.nextId();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(now.toEpochMilli());
    }

    @Test
    void generatesDifferentIdsEvenWhenClockIsFixed() {
        var generator = new UuidV7RmaIdGenerator(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertThat(generator.nextId()).isNotEqualTo(generator.nextId());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 281474976710655L})
    void acceptsTimestampRangeBoundaries(long timestamp) {
        var generator = new UuidV7RmaIdGenerator(Clock.fixed(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
        var id = generator.nextId();
        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(timestamp);
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 281474976710656L})
    void rejectsTimestampsOutside48BitRange(long timestamp) {
        var generator = new UuidV7RmaIdGenerator(Clock.fixed(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
        assertThatIllegalStateException().isThrownBy(generator::nextId);
    }
}
