package dev.rma.returns.application.port.in;

public interface CancelReturnUseCase {
    void cancel(String businessNumber, String cancelledBy, String reason);
}
