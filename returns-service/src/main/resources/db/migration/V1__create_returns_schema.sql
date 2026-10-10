-- The returns schema is provisioned by the infrastructure and owned by migrator.
CREATE TABLE returns.rma_events (
    event_id UUID PRIMARY KEY,
    rma_id UUID NOT NULL,
    version INTEGER NOT NULL,
    type TEXT NOT NULL,
    payload JSONB NOT NULL,
    schema_version INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT unique_rma_id_version
    UNIQUE (rma_id, version)
);

-- Application access to the event history is append-only.
REVOKE UPDATE, DELETE ON returns.rma_events FROM returns_app;
GRANT SELECT, INSERT ON returns.rma_events TO returns_app;

CREATE TABLE returns.rma_snapshots (
    rma_id UUID NOT NULL,
    version INTEGER NOT NULL,
    state JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (rma_id, version),

    CONSTRAINT fk_snapshots_events
    FOREIGN KEY (rma_id, version)
    REFERENCES returns.rma_events(rma_id, version)
);

CREATE TABLE returns.rma_projection (
    rma_id UUID PRIMARY KEY,
    business_number TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL,
    refund_amount NUMERIC NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- One counter per year; the persistence adapter increments it atomically
-- using INSERT ... ON CONFLICT DO UPDATE ... RETURNING last_value.
-- Reject overflow instead of producing a number wider than RMA-YYYY-NNNNNN.
CREATE TABLE returns.rma_business_number_counters (
    year INTEGER PRIMARY KEY CHECK (year BETWEEN 1 AND 9999),
    last_value INTEGER NOT NULL CHECK (last_value BETWEEN 1 AND 999999)
);

CREATE TABLE returns.idempotency_keys (
    idempotency_key TEXT NOT NULL,
    endpoint TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response_status INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (idempotency_key, endpoint)
);

CREATE INDEX idx_idempotency_keys_created_at
    ON returns.idempotency_keys (created_at);
