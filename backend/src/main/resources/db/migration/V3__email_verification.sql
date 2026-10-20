-- Email ownership. Adding someone to a group by email trusts that the account
-- holder owns the address, so it has to be proven before others can find them.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP;

-- Accounts from before verification existed (pre-launch test data) keep working.
UPDATE users SET email_verified_at = created_at;

-- Same shape as password_resets: a hashed one-time code with expiry and a guess count.
CREATE TABLE email_verifications (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash   VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    consumed_at TIMESTAMP,
    attempts    INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL
);
CREATE INDEX ix_email_verifications_user ON email_verifications (user_id);
