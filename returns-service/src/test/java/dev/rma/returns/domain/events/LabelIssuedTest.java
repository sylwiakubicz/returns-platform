package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class LabelIssuedTest {

    private static final String LABEL_ID = "label-1";
    private static final String TRACKING_NUMBER = "tr-1";
    private static final String LABEL_FILE_KEY = "key";
    private static final Instant ISSUED_AT = Instant.parse("2026-09-10T12:00:00Z");

    @Test
    void shouldRejectNullLabelId() {
        assertThatThrownBy(() -> new LabelIssued(null, TRACKING_NUMBER, LABEL_FILE_KEY, ISSUED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("labelId must not be null");
    }

    @Test
    void shouldRejectNullTrackingNumber() {
        assertThatThrownBy(() -> new LabelIssued(LABEL_ID, null, LABEL_FILE_KEY, ISSUED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("trackingNumber must not be null");
    }

    @Test
    void shouldRejectNullLabelFileKey() {
        assertThatThrownBy(() -> new LabelIssued(LABEL_ID, TRACKING_NUMBER, null, ISSUED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("labelFileKey must not be null");
    }

    @Test
    void shouldRejectNullIssuedAt() {
        assertThatThrownBy(() -> new LabelIssued(LABEL_ID, TRACKING_NUMBER, LABEL_FILE_KEY, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("issuedAt must not be null");
    }
}
