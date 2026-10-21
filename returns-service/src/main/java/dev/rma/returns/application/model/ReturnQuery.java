package dev.rma.returns.application.model;

import dev.rma.returns.domain.enums.RmaStatus;

/** Strony liczymy od zera; status null oznacza brak filtra. */
public record ReturnQuery(int page, int size, RmaStatus status) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public ReturnQuery {
        if (page < 0) {
            throw new IllegalArgumentException("Page must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_SIZE);
        }
    }

    public long offset() {
        return (long) page * size;
    }
}
