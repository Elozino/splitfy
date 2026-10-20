-- Sign-in providers (Google, Apple) linked to an account. The provider's subject
-- id is the stable key; the email in the token can change, the subject cannot.
CREATE TABLE user_identities (
    provider   VARCHAR(20)  NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email      VARCHAR(254),
    created_at TIMESTAMP    NOT NULL,
    PRIMARY KEY (provider, subject)
);
CREATE INDEX ix_user_identities_user ON user_identities (user_id);
