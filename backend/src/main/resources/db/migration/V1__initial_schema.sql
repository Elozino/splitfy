-- Splitfy initial schema.
-- Money is stored as BIGINT minor units plus an ISO-4217 currency code; floating
-- point is never used for amounts.

CREATE TABLE users (
    id               UUID         PRIMARY KEY,
    email            VARCHAR(254) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    display_name     VARCHAR(60)  NOT NULL,
    phone_number     VARCHAR(20),
    avatar_url       TEXT,
    default_currency CHAR(3)      NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);
CREATE UNIQUE INDEX uq_users_email ON users (email);

CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    device_id  VARCHAR(128),
    expires_at TIMESTAMP   NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP   NOT NULL
);
CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE password_resets (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash   VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    consumed_at TIMESTAMP,
    attempts    INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL
);
CREATE INDEX ix_password_resets_user ON password_resets (user_id);

CREATE TABLE groups (
    id              UUID        PRIMARY KEY,
    name            VARCHAR(60) NOT NULL,
    description     TEXT,
    category        VARCHAR(20) NOT NULL,
    currency        CHAR(3)     NOT NULL,
    cover_image_url TEXT,
    created_by      UUID        NOT NULL REFERENCES users (id),
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL,
    archived_at     TIMESTAMP
);

CREATE TABLE group_members (
    group_id  UUID        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role      VARCHAR(10) NOT NULL,
    joined_at TIMESTAMP   NOT NULL,
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX ix_group_members_user ON group_members (user_id);

CREATE TABLE expenses (
    id                UUID         PRIMARY KEY,
    group_id          UUID         NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    description       VARCHAR(140) NOT NULL,
    amount            BIGINT       NOT NULL,
    currency          CHAR(3)      NOT NULL,
    category          VARCHAR(20)  NOT NULL,
    paid_by           UUID         NOT NULL REFERENCES users (id),
    split_method      VARCHAR(12)  NOT NULL,
    receipt_url       TEXT,
    notes             TEXT,
    incurred_at       TIMESTAMP    NOT NULL,
    created_by        UUID         NOT NULL REFERENCES users (id),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    deleted_at        TIMESTAMP,
    client_request_id VARCHAR(64)
);
CREATE INDEX ix_expenses_group_created ON expenses (group_id, created_at);
CREATE UNIQUE INDEX uq_expenses_group_client_request ON expenses (group_id, client_request_id);

CREATE TABLE expense_shares (
    id         UUID   PRIMARY KEY,
    expense_id UUID   NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    user_id    UUID   NOT NULL REFERENCES users (id),
    amount     BIGINT NOT NULL,
    weight     BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_expense_shares_expense_user ON expense_shares (expense_id, user_id);

CREATE TABLE settlements (
    id                UUID        PRIMARY KEY,
    group_id          UUID        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    from_user_id      UUID        NOT NULL REFERENCES users (id),
    to_user_id        UUID        NOT NULL REFERENCES users (id),
    amount            BIGINT      NOT NULL,
    currency          CHAR(3)     NOT NULL,
    status            VARCHAR(12) NOT NULL,
    note              TEXT,
    settled_at        TIMESTAMP   NOT NULL,
    created_by        UUID        NOT NULL REFERENCES users (id),
    created_at        TIMESTAMP   NOT NULL,
    client_request_id VARCHAR(64)
);
CREATE INDEX ix_settlements_group ON settlements (group_id);
CREATE UNIQUE INDEX uq_settlements_group_client_request ON settlements (group_id, client_request_id);

CREATE TABLE activity_log (
    id          UUID         PRIMARY KEY,
    group_id    UUID         REFERENCES groups (id) ON DELETE CASCADE,
    actor_id    UUID         NOT NULL REFERENCES users (id),
    type        VARCHAR(32)  NOT NULL,
    summary     VARCHAR(280) NOT NULL,
    occurred_at TIMESTAMP    NOT NULL
);
CREATE INDEX ix_activity_group_time ON activity_log (group_id, occurred_at);
