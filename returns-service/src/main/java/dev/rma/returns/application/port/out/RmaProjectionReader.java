package dev.rma.returns.application.port.out;

import java.util.Optional;

import dev.rma.returns.application.model.ReturnPage;
import dev.rma.returns.application.model.ReturnQuery;
import dev.rma.returns.application.model.ReturnView;

/** Odczyt z projekcji, bez odtwarzania agregatu i jego historii. */
public interface RmaProjectionReader {
    Optional<ReturnView> findByBusinessNumber(String businessNumber);

    /** Zwraca stronę zgodną z filtrem, uporządkowaną po numerze biznesowym malejąco. */
    ReturnPage findAll(ReturnQuery query);
}
