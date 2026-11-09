-- Join attribution (change add-join-attribution). The installation's join domain and attribution window, the first-join
-- events the network proxy reports, the stated source of a first join, and the daily join counts the analytics read from.
-- No row holds a player UUID, a player name or an IP address: the player is only a pseudonym computed at the proxy.

-- The one settings row of the installation. Without a row the join domain is unset and the window is 72 hours.
CREATE TABLE menura.join_settings (
    id                       smallint    PRIMARY KEY CHECK (id = 1),
    join_domain              text        CHECK (char_length(join_domain) <= 253
                                             AND join_domain ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$'),
    attribution_window_hours integer     NOT NULL DEFAULT 72 CHECK (attribution_window_hours BETWEEN 1 AND 720),
    updated_at               timestamptz NOT NULL,
    updated_by               uuid
);

-- One join of a player as the network proxy reported it. The event id is the proxy's key, so a repeated delivery is the
-- same row. The table is not partitioned: the event id must be unique on its own for the idempotent insert.
CREATE TABLE menura.join_event (
    event_id    uuid        PRIMARY KEY,
    first_join  boolean     NOT NULL,
    joined_at   timestamptz NOT NULL,
    pseudonym   text        NOT NULL CHECK (pseudonym ~ '^[0-9a-f]{64}$'),
    received_at timestamptz NOT NULL
);

-- The attribution and the reports read the first joins of a period.
CREATE INDEX join_event_first_joined_idx ON menura.join_event (joined_at) WHERE first_join;

-- The source a player named for a first join: at most one per join, one of the fixed choices, never free text.
CREATE TABLE menura.join_source_answer (
    event_id    uuid        PRIMARY KEY REFERENCES menura.join_event (event_id) ON DELETE CASCADE,
    source      text        NOT NULL CHECK (source IN ('youtube', 'tiktok', 'instagram', 'twitch', 'friends', 'other')),
    answered_at timestamptz NOT NULL
);

-- The daily rollup of first joins, per calendar day of the installation's time zone. Source 'total' counts every first
-- join of the day; a stated source counts the first joins answered with it. Unanswered joins are never stored: they are
-- the total minus the stated sources.
CREATE TABLE menura.join_count_day (
    day     date   NOT NULL,
    source  text   NOT NULL CHECK (source IN ('total', 'youtube', 'tiktok', 'instagram', 'twitch', 'friends', 'other')),
    joins   bigint NOT NULL CHECK (joins >= 0),
    PRIMARY KEY (day, source)
);
