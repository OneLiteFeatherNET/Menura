-- Calendar feed tokens and the audit log (change add-access-control). Secrets are stored as SHA-256 hashes only.

-- One row per issued feed token. The token the person sees is "<id>.<secret>"; only the hash of the secret is kept.
-- Rotation and revocation set revoked_at, so a refused token is never found again as active.
CREATE TABLE menura.calendar_feed_token (
    id          uuid        PRIMARY KEY,
    person_id   uuid        NOT NULL,
    secret_hash bytea       NOT NULL,
    created_at  timestamptz NOT NULL,
    revoked_at  timestamptz
);

-- At most one active feed token per person.
CREATE UNIQUE INDEX calendar_feed_token_one_active_per_person
    ON menura.calendar_feed_token (person_id)
    WHERE revoked_at IS NULL;

-- Every write of the application, written in the same transaction as the write itself.
CREATE TABLE menura.audit_log (
    id          uuid        PRIMARY KEY,
    person_id   uuid        NOT NULL,
    action      text        NOT NULL,
    target      text        NOT NULL,
    occurred_at timestamptz NOT NULL,
    channel     text        NOT NULL CHECK (channel IN ('web', 'desktop', 'obs', 'mcp')),
    request_id  text        NOT NULL
);

CREATE INDEX audit_log_person_occurred_idx ON menura.audit_log (person_id, occurred_at);
