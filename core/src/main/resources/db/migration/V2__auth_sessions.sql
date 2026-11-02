-- Identity and server-side sign-in state of the web app (backend-for-frontend, change add-access-control).
-- Secrets are stored as SHA-256 digests only. No Menura access token is ever stored: a session stands for the
-- person and roles it was created with, and any token that a later device or MCP flow needs is issued on demand.

-- A person known to Menura: the Entra ID identity (issuer and subject, unique together) mapped to a random
-- version 4 UUID that is the only person identifier the rest of the application sees.
CREATE TABLE person (
    id           uuid        PRIMARY KEY,
    issuer       text        NOT NULL,
    subject      text        NOT NULL,
    display_name text        NOT NULL,
    created_at   timestamptz NOT NULL,
    CONSTRAINT person_issuer_subject_key UNIQUE (issuer, subject)
);

-- A sign-in in progress: created by GET /auth/login, consumed once by GET /auth/callback.
CREATE TABLE auth_login (
    state_hash    bytea PRIMARY KEY,
    nonce         text NOT NULL,
    code_verifier text NOT NULL,
    expires_at    timestamptz NOT NULL
);

-- A web session: the browser holds the secret in the __Host-menura-session cookie, the server holds only its digest,
-- the person and the roles granted at sign-in. last_seen_at is refreshed on each use of the session.
CREATE TABLE auth_session (
    secret_hash  bytea PRIMARY KEY,
    person_id    uuid        NOT NULL REFERENCES person (id) ON DELETE CASCADE,
    roles        text[]      NOT NULL,
    created_at   timestamptz NOT NULL,
    expires_at   timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL
);

CREATE INDEX auth_session_expires_at_idx ON auth_session (expires_at);
CREATE INDEX auth_session_person_id_idx ON auth_session (person_id);
