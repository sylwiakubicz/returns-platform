package dev.rma.returns.application.model;

import java.util.List;

/** totalElements i totalPages odnoszą się do wszystkich wyników zgodnych z filtrem. */
public record ReturnPage(
    List<ReturnView> content,
    int page,
    int size,
    long totalElements,
    long totalPages) {
    public ReturnPage {
        content = List.copyOf(content);
    }
}
