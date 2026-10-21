package dev.rma.returns.application;

import dev.rma.returns.application.exception.ReturnNotFoundException;
import dev.rma.returns.application.model.ReturnPage;
import dev.rma.returns.application.model.ReturnQuery;
import dev.rma.returns.application.model.ReturnView;
import dev.rma.returns.application.port.in.GetReturnUseCase;
import dev.rma.returns.application.port.in.ListReturnsUseCase;
import dev.rma.returns.application.port.out.RmaProjectionReader;

public class ReturnQueryService implements GetReturnUseCase, ListReturnsUseCase {
    private final RmaProjectionReader reader;

    public ReturnQueryService(RmaProjectionReader reader) {
        this.reader = reader;
    }

    @Override
    public ReturnView get(String businessNumber) {
        return reader.findByBusinessNumber(businessNumber)
                .orElseThrow(() -> new ReturnNotFoundException(businessNumber));
    }

    @Override
    public ReturnPage list(ReturnQuery query) {
        return reader.findAll(query);
    }
}
