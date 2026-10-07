CREATE TABLE IF NOT EXISTS rma_events(
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

CREATE TABLE IF NOT EXISTS processed_messages(
    message_key TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS rma_snapshots(
    rma_id UUID NOT NULL,
    version INTEGER NOT NULL,
    state JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (rma_id, version),

    CONSTRAINT fk_snapshots_events
    FOREIGN KEY (rma_id, version)
    REFERENCES rma_events(rma_id, version)
);

CREATE TABLE IF NOT EXISTS rma_projection(
    rma_id UUID PRIMARY KEY,
    business_number TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL,
    refund_amount NUMERIC NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS outbox(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    topic TEXT NOT NULL,
    partition_key TEXT NOT NULL,
    payload BYTEA NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS sla_timers(
    id BIGINT PRIMARY KEY,
    rma_id UUID NOT NULL,
    timer_type TEXT NOT NULL,
    fire_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key TEXT NOT NULL,
    endpoint TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response_status INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (idempotency_key, endpoint)
);

CREATE INDEX idx_idempotency_keys_created_at
    ON idempotency_keys (created_at);
