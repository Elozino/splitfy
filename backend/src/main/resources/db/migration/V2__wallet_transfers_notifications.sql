-- Wallet, funding sources, peer transfers, notifications and contact favourites.
-- As in V1, money is BIGINT minor units plus an ISO-4217 code.

CREATE TABLE wallets (
    user_id    UUID      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    currency   CHAR(3)   NOT NULL,
    balance    BIGINT    NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL,
    -- A wallet must never go negative; enforced here as well as in the service so a
    -- future code path cannot bypass it.
    CONSTRAINT ck_wallets_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE payment_methods (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type           VARCHAR(16)  NOT NULL,
    label          VARCHAR(80)  NOT NULL,
    brand          VARCHAR(32),
    last4          CHAR(4),
    -- Opaque handle from the payment provider. Raw card numbers are never stored.
    provider_token VARCHAR(255) NOT NULL,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL
);
CREATE INDEX ix_payment_methods_user ON payment_methods (user_id);

CREATE TABLE transfers (
    id                UUID        PRIMARY KEY,
    kind              VARCHAR(8)  NOT NULL,
    status            VARCHAR(10) NOT NULL,
    from_user_id      UUID        NOT NULL REFERENCES users (id),
    to_user_id        UUID        NOT NULL REFERENCES users (id),
    amount            BIGINT      NOT NULL,
    currency          CHAR(3)     NOT NULL,
    note              TEXT,
    group_id          UUID        REFERENCES groups (id) ON DELETE SET NULL,
    created_by        UUID        NOT NULL REFERENCES users (id),
    created_at        TIMESTAMP   NOT NULL,
    completed_at      TIMESTAMP,
    client_request_id VARCHAR(64)
);
CREATE INDEX ix_transfers_from ON transfers (from_user_id, created_at);
CREATE INDEX ix_transfers_to ON transfers (to_user_id, created_at);
CREATE UNIQUE INDEX uq_transfers_creator_client_request ON transfers (created_by, client_request_id);

CREATE TABLE ledger_entries (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type              VARCHAR(20)  NOT NULL,
    amount            BIGINT       NOT NULL,
    currency          CHAR(3)      NOT NULL,
    -- The balance as it stood after this entry, so a statement is stable forever.
    balance_after     BIGINT       NOT NULL,
    description       VARCHAR(140) NOT NULL,
    counterparty_id   UUID         REFERENCES users (id),
    payment_method_id UUID         REFERENCES payment_methods (id) ON DELETE SET NULL,
    transfer_id       UUID         REFERENCES transfers (id) ON DELETE SET NULL,
    note              TEXT,
    occurred_at       TIMESTAMP    NOT NULL,
    client_request_id VARCHAR(64)
);
CREATE INDEX ix_ledger_user_time ON ledger_entries (user_id, occurred_at);
CREATE UNIQUE INDEX uq_ledger_user_client_request ON ledger_entries (user_id, client_request_id);

CREATE TABLE notifications (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category   VARCHAR(16)  NOT NULL,
    title      VARCHAR(120) NOT NULL,
    message    VARCHAR(400) NOT NULL,
    read_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL
);
CREATE INDEX ix_notifications_user_time ON notifications (user_id, created_at);

CREATE TABLE contact_favorites (
    user_id         UUID      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    contact_user_id UUID      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at      TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, contact_user_id)
);
