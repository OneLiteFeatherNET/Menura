-- Content series (change add-content-series): the catalogue of series, the assignment of external posts to series, the
-- episode numbers, and the playlist state of series posts. Records are changed in place; episode numbers are never
-- reused, so they live in their own tables.

-- One series. Colours are references to tokens of the brand kit, never raw values; the contrast of the badge text
-- against the series colour is checked before a row is written.
CREATE TABLE menura.content_series (
    id              uuid        PRIMARY KEY,
    name            text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 80),
    colour_token    text        NOT NULL CHECK (colour_token ~ '^[a-z][a-z0-9-]{0,63}$'),
    badge_token     text        NOT NULL CHECK (badge_token ~ '^[a-z][a-z0-9-]{0,63}$'),
    opening_formula text        NOT NULL CHECK (char_length(opening_formula) BETWEEN 1 AND 120),
    layout_template text        NOT NULL CHECK (layout_template ~ '^[a-z0-9-]{1,40}$'),
    hashtag         text        NOT NULL UNIQUE CHECK (hashtag ~ '^#[[:alnum:]_]{2,49}$'),
    audience        text        NOT NULL CHECK (audience IN ('players', 'developers', 'mixed')),
    weekday         smallint    NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    posting_slot    smallint    NOT NULL CHECK (posting_slot IN (12, 18, 21)),
    active          boolean     NOT NULL,
    -- The roles that may see the series; every role by default. A series a caller may not see answers 404.
    visible_to      text[]      NOT NULL DEFAULT ARRAY['viewer', 'editor', 'publisher', 'operator', 'admin']::text[]
                                CHECK (cardinality(visible_to) >= 1
                                       AND visible_to <@ ARRAY['viewer', 'editor', 'publisher', 'operator', 'admin']::text[]),
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL
);

-- The badge text of a series per language. A series has at least one language; the language is never substituted.
CREATE TABLE menura.content_series_badge (
    series_id  uuid     NOT NULL REFERENCES menura.content_series (id) ON DELETE CASCADE,
    language   text     NOT NULL CHECK (language ~ '^[a-z]{2}(-[A-Z]{2})?$'),
    badge_text text     NOT NULL CHECK (char_length(badge_text) BETWEEN 1 AND 40),
    PRIMARY KEY (series_id, language)
);

-- The optional playlist reference of a series per channel. Phase 1 reads the playlist; it never writes to it.
CREATE TABLE menura.content_series_playlist (
    series_id     uuid NOT NULL REFERENCES menura.content_series (id) ON DELETE CASCADE,
    channel_id    uuid NOT NULL REFERENCES menura.channel (id) ON DELETE CASCADE,
    playlist_id   text NOT NULL CHECK (char_length(playlist_id) BETWEEN 1 AND 200),
    playlist_name text NOT NULL CHECK (char_length(playlist_name) BETWEEN 1 AND 200),
    PRIMARY KEY (series_id, channel_id)
);

-- The next episode number of a series. Only ever increased, under a row lock taken by the assignment.
CREATE TABLE menura.content_series_counter (
    series_id   uuid    PRIMARY KEY REFERENCES menura.content_series (id) ON DELETE CASCADE,
    last_number integer NOT NULL CHECK (last_number >= 0)
);

-- The episode number a post received in a series. It is kept for the pair, so a post that comes back to a series keeps
-- its number, and no number of a series is ever given to a second post.
CREATE TABLE menura.content_series_episode (
    series_id uuid    NOT NULL REFERENCES menura.content_series (id) ON DELETE CASCADE,
    post_id   uuid    NOT NULL REFERENCES menura.external_post (id) ON DELETE CASCADE,
    number    integer NOT NULL CHECK (number >= 1),
    PRIMARY KEY (series_id, post_id),
    CONSTRAINT content_series_episode_number_key UNIQUE (series_id, number)
);

-- The current assignment of a post: at most one series, or the explicit "no series" mark, or a flag that asks an editor
-- to decide. A post without a row is not covered by any series decision.
CREATE TABLE menura.content_series_assignment (
    post_id            uuid        PRIMARY KEY REFERENCES menura.external_post (id) ON DELETE CASCADE,
    series_id          uuid        REFERENCES menura.content_series (id) ON DELETE SET NULL,
    no_series          boolean     NOT NULL DEFAULT false,
    flag               text        CHECK (flag IN ('two_series')),
    playlist_state     text        CHECK (playlist_state IN ('in_playlist', 'missing', 'not_checked')),
    playlist_checked_at timestamptz,
    assigned_at        timestamptz NOT NULL,
    assigned_by        uuid,
    CONSTRAINT content_series_assignment_one_decision CHECK (
        (series_id IS NOT NULL AND NOT no_series AND flag IS NULL)
        OR (series_id IS NULL AND no_series AND flag IS NULL)
        OR (series_id IS NULL AND NOT no_series AND flag IS NOT NULL)
    )
);

-- The coverage report and the playlist check read the assignments of a series.
CREATE INDEX content_series_assignment_series_idx ON menura.content_series_assignment (series_id);
