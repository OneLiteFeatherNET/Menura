-- Channel connections (change add-channel-connections). Tokens are stored encrypted with AES-256-GCM by the application,
-- never as plain text; the key comes from environment configuration (MENURA_TOKEN_ENCRYPTION_KEY).

-- A channel: a platform account that Menura publishes to. Kept after disconnect for its posts and history; the grant
-- is deleted and the state becomes disconnected. grant_revocation records what the platform did with the grant.
CREATE TABLE menura.channel (
    id                  uuid        PRIMARY KEY,
    platform            text        NOT NULL CHECK (char_length(platform) BETWEEN 1 AND 40),
    platform_account_id text        NOT NULL CHECK (char_length(platform_account_id) BETWEEN 1 AND 200),
    display_name        text        NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 200),
    state               text        NOT NULL CHECK (state IN ('connected', 'needs_reconnection', 'disconnected')),
    state_reason        text,
    features            text[]      NOT NULL DEFAULT '{}',
    granted_scopes      text[]      NOT NULL DEFAULT '{}',
    grant_revocation    text        CHECK (grant_revocation IN ('revoked', 'not_supported', 'failed')),
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    disconnected_at     timestamptz,
    CONSTRAINT channel_disconnected_at_matches_state CHECK ((state = 'disconnected') = (disconnected_at IS NOT NULL))
);

-- One active channel per platform account: a second consent for the same account returns the existing channel.
CREATE UNIQUE INDEX channel_one_active_per_account
    ON menura.channel (platform, platform_account_id)
    WHERE state <> 'disconnected';

-- The tokens of a channel, encrypted by the application. Each column is bound to its channel and column by the
-- additional authenticated data, so a ciphertext moved elsewhere does not decrypt. Deleted on disconnect.
CREATE TABLE menura.channel_grant (
    channel_id        uuid        PRIMARY KEY REFERENCES menura.channel (id) ON DELETE CASCADE,
    access_token      bytea       NOT NULL,
    refresh_token     bytea,
    access_expires_at timestamptz,
    updated_at        timestamptz NOT NULL
);

-- Quota and rate-limit state per channel, as the platform reported it or Menura measured it.
CREATE TABLE menura.channel_quota (
    channel_id         uuid        PRIMARY KEY REFERENCES menura.channel (id) ON DELETE CASCADE,
    quota_used         bigint,
    quota_limit        bigint      CHECK (quota_limit IS NULL OR quota_limit > 0),
    quota_reset_at     timestamptz,
    measured_at        timestamptz,
    rate_limited_until timestamptz,
    rate_limit_reason  text
);

-- A consent in progress. The state is stored only as its SHA-256 digest; the PKCE verifier lives until the flow ends.
CREATE TABLE menura.channel_connection_flow (
    state_hash    bytea       PRIMARY KEY,
    person_id     uuid        NOT NULL,
    platform      text        NOT NULL,
    features      text[]      NOT NULL DEFAULT '{}',
    channel_id    uuid        REFERENCES menura.channel (id) ON DELETE CASCADE,
    code_verifier text        NOT NULL,
    expires_at    timestamptz NOT NULL
);

CREATE INDEX channel_connection_flow_expires_at_idx ON menura.channel_connection_flow (expires_at);

-- The audit status of each platform app, recorded by an administrator with its date and source. A platform without a
-- row is unknown.
CREATE TABLE menura.platform_audit_status (
    platform   text        PRIMARY KEY,
    status     text        NOT NULL CHECK (status IN ('unknown', 'unaudited', 'audited')),
    decided_at timestamptz,
    source     text        CHECK (source IS NULL OR char_length(source) <= 200)
);
