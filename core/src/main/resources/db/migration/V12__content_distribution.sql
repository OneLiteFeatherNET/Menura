-- Content distribution (change add-content-distribution): posts planned per channel, their approval and revisions, the
-- publication steps and attempts of each post, and the link from an external post to the Menura post it was published
-- from. A post row is changed in place; every change that a person or a job makes is visible in the steps, the
-- attempts and the revisions, so nothing is overwritten silently.

-- One post per channel: the per-channel text, hashtags, cover and scheduled time of one finished asset.
CREATE TABLE menura.post (
    id              uuid        PRIMARY KEY,
    channel_id      uuid        NOT NULL REFERENCES menura.channel (id),
    asset_id        uuid        NOT NULL REFERENCES menura.asset (id),
    cover_asset_id  uuid        REFERENCES menura.asset (id),
    series_id       uuid        REFERENCES menura.content_series (id) ON DELETE SET NULL,
    source_post_id  uuid        REFERENCES menura.post (id),
    author_id       uuid        NOT NULL,
    status          text        NOT NULL CHECK (status IN ('draft', 'in_review', 'approved', 'scheduled', 'published', 'failed')),
    title           text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    description     text        NOT NULL DEFAULT '' CHECK (char_length(description) <= 5000),
    hashtags        text[]      NOT NULL DEFAULT '{}',
    visibility      text        NOT NULL DEFAULT 'private' CHECK (visibility IN ('private', 'unlisted', 'public')),
    playlist_id     text        CHECK (char_length(playlist_id) <= 200),
    scheduled_at    timestamptz,
    approved_by     uuid,
    approved_at     timestamptz,
    handed_over_at  timestamptz,
    external_id     text        CHECK (char_length(external_id) <= 200),
    external_url    text        CHECK (char_length(external_url) <= 2048),
    published_at    timestamptz,
    failure_reason  text        CHECK (char_length(failure_reason) <= 500),
    attempts        integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz,
    lease_owner     text,
    lease_until     timestamptz,
    revision        integer     NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT post_approval_pair_check CHECK ((approved_by IS NULL) = (approved_at IS NULL)),
    CONSTRAINT post_lease_pair_check CHECK ((lease_owner IS NULL) = (lease_until IS NULL)),
    CONSTRAINT post_published_check CHECK (status <> 'published'
                                           OR (external_id IS NOT NULL AND published_at IS NOT NULL))
);

-- The calendar reads a channel's posts by time; the job reads the scheduled posts that are due.
CREATE INDEX post_channel_time_idx ON menura.post (channel_id, scheduled_at);
CREATE INDEX post_series_idx ON menura.post (series_id) WHERE series_id IS NOT NULL;
CREATE INDEX post_due_idx ON menura.post (scheduled_at) WHERE status = 'scheduled';

-- The state of each publication step of a post, written before the platform is called.
CREATE TABLE menura.post_step (
    post_id    uuid        NOT NULL REFERENCES menura.post (id) ON DELETE CASCADE,
    step       text        NOT NULL CHECK (step IN ('upload', 'schedule', 'metadata', 'publish')),
    state      text        NOT NULL CHECK (state IN ('pending', 'running', 'done', 'failed')),
    changed_at timestamptz NOT NULL,
    reason     text        CHECK (char_length(reason) <= 500),
    PRIMARY KEY (post_id, step)
);

-- One row per publication attempt, written before the platform is called and completed with its outcome. An attempt
-- without an outcome is a run that stopped before the platform answered.
CREATE TABLE menura.post_attempt (
    post_id     uuid        NOT NULL REFERENCES menura.post (id) ON DELETE CASCADE,
    attempt     integer     NOT NULL CHECK (attempt >= 1),
    started_at  timestamptz NOT NULL,
    finished_at timestamptz,
    outcome     text        CHECK (outcome IN ('handed_over', 'published', 'retry_scheduled', 'failed', 'unknown')),
    reason      text        CHECK (char_length(reason) <= 500),
    PRIMARY KEY (post_id, attempt)
);

-- A proposed revision of a post. Each changed field is set; a null field is unchanged. The post changes only when a
-- publisher accepts the revision.
CREATE TABLE menura.post_revision (
    id             uuid        PRIMARY KEY,
    post_id        uuid        NOT NULL REFERENCES menura.post (id) ON DELETE CASCADE,
    author_id      uuid        NOT NULL,
    rationale      text        NOT NULL CHECK (char_length(rationale) BETWEEN 1 AND 1000),
    title          text        CHECK (char_length(title) BETWEEN 1 AND 200),
    description    text        CHECK (char_length(description) <= 5000),
    hashtags       text[],
    cover_asset_id uuid        REFERENCES menura.asset (id),
    scheduled_at   timestamptz,
    channel_id     uuid        REFERENCES menura.channel (id),
    status         text        NOT NULL CHECK (status IN ('pending', 'accepted', 'rejected')),
    decided_by     uuid,
    decided_at     timestamptz,
    created_at     timestamptz NOT NULL,
    CONSTRAINT post_revision_decision_check CHECK ((status = 'pending') = (decided_at IS NULL))
);

CREATE INDEX post_revision_post_idx ON menura.post_revision (post_id, created_at);

-- Every external post that Menura published records the Menura post and the accepted revision it came from, so that
-- analytics can group numbers by their source. The external post itself is the same record an import would write.
CREATE TABLE menura.external_post_origin (
    external_post_id uuid PRIMARY KEY REFERENCES menura.external_post (id) ON DELETE CASCADE,
    post_id          uuid NOT NULL REFERENCES menura.post (id),
    revision_id      uuid REFERENCES menura.post_revision (id)
);

CREATE INDEX external_post_origin_post_idx ON menura.external_post_origin (post_id);

-- The calendar feed lists the approvals due only to a token issued while its person held the publisher role.
ALTER TABLE menura.calendar_feed_token ADD COLUMN publisher boolean NOT NULL DEFAULT false;
