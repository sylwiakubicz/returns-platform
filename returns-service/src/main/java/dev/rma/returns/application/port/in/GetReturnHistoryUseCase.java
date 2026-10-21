package dev.rma.returns.application.port.in;

import java.util.List;

import dev.rma.returns.application.model.HistoryEntry;

public interface GetReturnHistoryUseCase {
    List<HistoryEntry> history(String businessNumber);
}
