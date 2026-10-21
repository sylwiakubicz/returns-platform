-- Walutę znamy już w chwili zgłoszenia, nawet jeśli nie ma jeszcze decyzji o kwocie.
ALTER TABLE returns.rma_projection ADD COLUMN currency TEXT;

-- Uzupełniamy istniejące zwroty na podstawie historii, bez zakładania domyślnej waluty.
UPDATE returns.rma_projection AS projection
SET currency = event.payload ->> 'currency'
FROM returns.rma_events AS event
WHERE event.rma_id = projection.rma_id
  AND event.type = 'RmaRequested';

ALTER TABLE returns.rma_projection
    ALTER COLUMN currency SET NOT NULL,
    ADD CONSTRAINT rma_projection_currency_format CHECK (currency ~ '^[A-Z]{3}$');
