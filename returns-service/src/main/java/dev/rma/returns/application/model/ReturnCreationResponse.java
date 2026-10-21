package dev.rma.returns.application.model;

/** Odpowiedź gotowa do zwrócenia po pierwszym żądaniu lub jego ponowieniu. */
public record ReturnCreationResponse(int status, String body, String location) {}
