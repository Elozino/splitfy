-- Every object key this server has signed an upload for. An expense receipt or a
-- group cover may only point at a key recorded here for that same group and
-- purpose, so a client can never attach an arbitrary URL or another group's file.
CREATE TABLE uploads (
    object_key   VARCHAR(255) PRIMARY KEY,
    group_id     UUID         NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    purpose      VARCHAR(16)  NOT NULL,
    content_type VARCHAR(64)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    uploaded_by  UUID         NOT NULL REFERENCES users (id),
    created_at   TIMESTAMP    NOT NULL
);
CREATE INDEX ix_uploads_group ON uploads (group_id);
