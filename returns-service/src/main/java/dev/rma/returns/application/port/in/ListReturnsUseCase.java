package dev.rma.returns.application.port.in;

import dev.rma.returns.application.model.ReturnPage;
import dev.rma.returns.application.model.ReturnQuery;

public interface ListReturnsUseCase {
    ReturnPage list(ReturnQuery query);
}
