-- Server-side sign-in state of the web app (backend-for-frontend). Secrets are stored as SHA-256 digests only.

-- A sign-in in progress: created by GET /auth/login, consumed once by GET /auth/callback.
CREATE TABLE auth_login (
    state_hash    bytea PRIMARY KEY,
    nonce         text NOT NULL,
    code_verifier text NOT NULL,
    expires_at    timestamptz NOT NULL
);

-- A web session: the browser holds the secret in the __Host-menura-session cookie, the server holds its digest and
-- the Menura access token that the session stands for.
CREATE TABLE auth_session (
    secret_hash  bytea PRIMARY KEY,
    subject      text NOT NULL,
    access_token text NOT NULL,
    expires_at   timestamptz NOT NULL
);

CREATE INDEX auth_session_expires_at_idx ON auth_session (expires_at);
