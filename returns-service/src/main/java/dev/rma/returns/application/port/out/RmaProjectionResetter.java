package dev.rma.returns.application.port.out;

public interface RmaProjectionResetter {
    /**
     * Wyklucza zapis zdarzeń do końca bieżącej transakcji i usuwa dane projekcji.
     * Wymaga aktywnej transakcji.
     */
    void resetForRebuild();
}
