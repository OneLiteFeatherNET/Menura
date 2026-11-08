-- External posts (change add-external-post-import). A post or video that exists on a connected channel, imported from
-- the channel's history or published by Menura later. Only metadata and the platform URL are stored: no media is copied.
-- Records are never deleted: a post the platform removes gets removed_at, and its record and metric history stay.

CREATE TABLE menura.external_post (
    id               uuid        PRIMARY KEY,
    channel_id       uuid        NOT NULL REFERENCES menura.channel (id) ON DELETE CASCADE,
    platform         text        NOT NULL CHECK (char_length(platform) BETWEEN 1 AND 40),
    platform_item_id text        NOT NULL CHECK (char_length(platform_item_id) BETWEEN 1 AND 200),
    url              text        NOT NULL CHECK (char_length(url) BETWEEN 1 AND 2048),
    title            text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 500),
    format           text        NOT NULL CHECK (char_length(format) BETWEEN 1 AND 40),
    published_at     timestamptz NOT NULL,
    removed_at       timestamptz,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    -- One record per channel and platform identifier: a title or URL change updates the record, it never adds one.
    CONSTRAINT external_post_channel_item_key UNIQUE (channel_id, platform_item_id)
);

-- The overview reads a channel's posts newest first.
CREATE INDEX external_post_channel_published_idx ON menura.external_post (channel_id, published_at DESC, id DESC);

-- The last re-sync of a channel: when it last completed and when it last failed, with a fixed reason text.
CREATE TABLE menura.channel_sync (
    channel_id      uuid        PRIMARY KEY REFERENCES menura.channel (id) ON DELETE CASCADE,
    last_success_at timestamptz,
    last_failure_at timestamptz,
    failure_reason  text        CHECK (failure_reason IS NULL OR char_length(failure_reason) <= 200)
);
