-- Account security: named device sessions and authenticator-app 2FA.

-- Whether the owner knows the account's password is users.has_password, added in
-- V11 (shared with linked accounts, which needs the same fact).

-- A session is one signed-in device: the chain of refresh tokens that rotate into
-- each other. Listing and revoking work on sessions, never on token hashes.
CREATE TABLE auth_sessions (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_name  VARCHAR(100),
    platform     VARCHAR(20),
    created_at   TIMESTAMP    NOT NULL,
    last_used_at TIMESTAMP    NOT NULL,
    -- The live refresh token's expiry, so "active" needs no join.
    expires_at   TIMESTAMP    NOT NULL,
    revoked_at   TIMESTAMP
);
CREATE INDEX ix_auth_sessions_user ON auth_sessions (user_id);

ALTER TABLE refresh_tokens ADD COLUMN session_id UUID REFERENCES auth_sessions (id) ON DELETE CASCADE;
CREATE INDEX ix_refresh_tokens_session ON refresh_tokens (session_id);

-- Every live refresh token becomes its own (unnamed) session, keyed by the token id,
-- so nobody is signed out by this migration.
INSERT INTO auth_sessions (id, user_id, created_at, last_used_at, expires_at)
SELECT id, user_id, created_at, created_at, expires_at
FROM refresh_tokens
WHERE revoked_at IS NULL AND expires_at > now();

UPDATE refresh_tokens SET session_id = id
WHERE revoked_at IS NULL AND expires_at > now();

-- One authenticator per user. The secret has to be readable to check codes, so it
-- is encrypted (AES-GCM, key from configuration) rather than hashed.
CREATE TABLE user_totp (
    user_id          UUID      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    secret_encrypted TEXT      NOT NULL,
    -- NULL while enrolment waits for the first code.
    enabled_at       TIMESTAMP,
    -- The 30-second step of the last accepted code: a code is never accepted twice.
    last_used_step   BIGINT    NOT NULL DEFAULT 0,
    -- Wrong codes since the last right one, across sign-in, enrolment and disabling.
    failed_attempts  INT       NOT NULL DEFAULT 0,
    last_failed_at   TIMESTAMP,
    created_at       TIMESTAMP NOT NULL
);

-- Single-use fallbacks for a lost phone. SHA-256 only: they are random, not chosen.
CREATE TABLE totp_recovery_codes (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP   NOT NULL
);
CREATE INDEX ix_totp_recovery_codes_user ON totp_recovery_codes (user_id);

-- Issued when the first factor passes on a 2FA account; traded for tokens with a code.
CREATE TABLE two_factor_challenges (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    device_id   VARCHAR(128),
    expires_at  TIMESTAMP   NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL
);
CREATE UNIQUE INDEX uq_two_factor_challenges_token ON two_factor_challenges (token_hash);
CREATE INDEX ix_two_factor_challenges_user ON two_factor_challenges (user_id);
