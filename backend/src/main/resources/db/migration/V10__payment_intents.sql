-- Sandbox integration only. Retain financial records; account deletion needs an explicit retention policy.
CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('TOP_UP', 'WITHDRAWAL')),
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'NGN'),
    provider_reference VARCHAR(80) NOT NULL UNIQUE,
    client_request_id VARCHAR(64) NOT NULL,
    destination VARCHAR(254) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'REVERSED')),
    created_at TIMESTAMP NOT NULL,
    settled_at TIMESTAMP,
    UNIQUE(user_id, client_request_id)
);
CREATE INDEX ix_payment_pending ON payment_intents(status, created_at);
-- A stable effect key prevents duplicate financial effects independently of webhook delivery IDs.
CREATE TABLE payment_effects (
    intent_id UUID NOT NULL REFERENCES payment_intents(id),
    effect VARCHAR(16) NOT NULL,
    ledger_entry_id UUID NOT NULL UNIQUE REFERENCES ledger_entries(id),
    PRIMARY KEY(intent_id, effect)
);
