package dev.rma.common.idempotency;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class RequestBodyHasherTest {
    @Test
    void matchesKnownSha256ForAbc() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                RequestBodyHasher.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void matchesKnownSha256ForEmptyBody() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                RequestBodyHasher.sha256(new byte[0]));
    }

    @Test
    void distinguishesJsonFormattingAndChangedValues() {
        String original = RequestBodyHasher.sha256("{\"orderId\":\"1\"}".getBytes(StandardCharsets.UTF_8));
        assertNotEquals(original, RequestBodyHasher.sha256("{ \"orderId\": \"1\" }".getBytes(StandardCharsets.UTF_8)));
        assertNotEquals(original, RequestBodyHasher.sha256("{\"orderId\":\"2\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void repeatedCallsPreserveInputAndProduceSameHash() {
        byte[] body = "{\"note\":\"Zażółć gęślą jaźń\"}".getBytes(StandardCharsets.UTF_8);
        byte[] original = body.clone();
        String first = RequestBodyHasher.sha256(body);
        assertEquals(first, RequestBodyHasher.sha256(original));
        assertEquals(first, RequestBodyHasher.sha256(body));
        assertArrayEquals(original, body);
    }

    @Test
    void rejectsMissingBodyRatherThanTreatingItAsEmpty() {
        assertThrows(NullPointerException.class, () -> RequestBodyHasher.sha256(null));
    }
}
