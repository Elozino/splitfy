-- Shareable invite links. One active link per group; resetting it revokes the old
-- one. The token is kept in plain text so members can share the same link again;
-- it only grants joining that one group.
CREATE TABLE group_invites (
    id         UUID        PRIMARY KEY,
    group_id   UUID        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    token      VARCHAR(64) NOT NULL,
    created_by UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMP   NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    revoked_at TIMESTAMP,
    use_count  INT         NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_group_invites_token ON group_invites (token);
CREATE INDEX ix_group_invites_group ON group_invites (group_id);
