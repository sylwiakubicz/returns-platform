package dev.rma.returns.application.port.in;

import dev.rma.returns.application.model.ReturnView;

public interface GetReturnUseCase {
    ReturnView get(String businessNumber);
}
