-- Whether the account has a password its owner knows. Accounts created through
-- Google or Apple get a random hash nobody knows, so for them the identity is the
-- only way in and the last one cannot be unlinked.
ALTER TABLE users ADD COLUMN has_password BOOLEAN NOT NULL DEFAULT TRUE;

-- Backfill: an identity linked within a minute of the account's creation means the
-- account was created by that sign-in. (An unverified account later claimed through
-- Google keeps TRUE; its owner can still reset the password by email.)
UPDATE users u SET has_password = FALSE
WHERE EXISTS (
    SELECT 1 FROM user_identities i
    WHERE i.user_id = u.id AND i.created_at < u.created_at + INTERVAL '1 minute'
);

-- Profile photos belong to a user, not a group.
ALTER TABLE uploads ALTER COLUMN group_id DROP NOT NULL;

-- Pending email changes: the code is sent to new_email and proves the user owns it.
CREATE TABLE email_changes (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    new_email   VARCHAR(254) NOT NULL,
    code_hash   VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    consumed_at TIMESTAMP,
    attempts    INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL
);
CREATE INDEX ix_email_changes_user ON email_changes (user_id);
