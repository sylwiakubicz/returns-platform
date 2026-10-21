package dev.rma.returns.application.port.in;

import dev.rma.returns.application.model.CreateReturnCommand;
import dev.rma.returns.application.model.ReturnView;

public interface CreateReturnUseCase {
    ReturnView create(CreateReturnCommand command);
}
