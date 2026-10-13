-- NULL means that no refund decision has been made yet.
ALTER TABLE returns.rma_projection
    ALTER COLUMN refund_amount DROP NOT NULL;
