-- Push notifications: device tokens, per-user push preferences, and the
-- bookkeeping that keeps the daily payment reminder from double-sending.

-- One row per FCM registration token. A token belongs to one install, so signing
-- in as someone else on the same phone moves the row rather than duplicating it.
CREATE TABLE device_tokens (
    token      VARCHAR(512) PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform   VARCHAR(10)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);
CREATE INDEX ix_device_tokens_user ON device_tokens (user_id);

-- Absent row = everything on. Only the push is gated; in-app notifications are kept.
CREATE TABLE notification_preferences (
    user_id           UUID      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    new_expenses      BOOLEAN   NOT NULL DEFAULT TRUE,
    settlements       BOOLEAN   NOT NULL DEFAULT TRUE,
    group_activity    BOOLEAN   NOT NULL DEFAULT TRUE,
    payment_reminders BOOLEAN   NOT NULL DEFAULT TRUE,
    updated_at        TIMESTAMP NOT NULL
);

-- A scheduled job claims its day here before running. The primary key is the
-- guard: when several API instances wake at the same time, one insert wins.
CREATE TABLE job_runs (
    job        VARCHAR(40) NOT NULL,
    run_on     DATE        NOT NULL,
    claimed_at TIMESTAMP   NOT NULL,
    PRIMARY KEY (job, run_on)
);

-- When each debtor was last reminded about a group, so a debt that stays open
-- is nudged every few days rather than every morning.
CREATE TABLE payment_reminders (
    user_id      UUID      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    group_id     UUID      NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    last_sent_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, group_id)
);
