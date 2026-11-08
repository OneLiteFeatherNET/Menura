-- Content library: the shared pool of raw material, clips, transcripts, timelines and lineage (change add-content-library).
-- A raw item is a row of menura.asset of kind raw_material; a finished asset enters the pool once it is registered
-- here. Media bytes stay in the object storage: these rows hold metadata, transcript segments and lineage edges.

-- Retention moves raw media to cold storage or marks it removed; the raw item row always stays.
ALTER TABLE menura.asset
    ADD COLUMN media_state text NOT NULL DEFAULT 'available'
        CHECK (media_state IN ('available', 'cold', 'removed'));

-- A clip is a time range of one raw item. Its stage is cut until a finished asset made from it is registered.
CREATE TABLE menura.clip (
    id            uuid        PRIMARY KEY,
    raw_item_id   uuid        NOT NULL REFERENCES menura.asset (id),
    uploader_id   uuid        NOT NULL,
    title         text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    start_seconds integer     NOT NULL CHECK (start_seconds >= 0),
    end_seconds   integer     NOT NULL,
    status        text        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suggested')),
    stage         text        NOT NULL DEFAULT 'cut' CHECK (stage IN ('cut', 'rendered')),
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    CONSTRAINT clip_range_check CHECK (end_seconds > start_seconds)
);

CREATE INDEX clip_raw_item_idx ON menura.clip (raw_item_id);

-- Free tags and person tags of raw items and clips, keyed by the item's identifier.
CREATE TABLE menura.item_tag (
    item_id uuid NOT NULL,
    tag     text NOT NULL CHECK (tag ~ '^[a-z0-9-]{2,30}$'),
    PRIMARY KEY (item_id, tag)
);

CREATE INDEX item_tag_tag_idx ON menura.item_tag (tag, item_id);

CREATE TABLE menura.item_person_tag (
    item_id   uuid NOT NULL,
    person_id uuid NOT NULL REFERENCES person (id),
    PRIMARY KEY (item_id, person_id)
);

CREATE INDEX item_person_tag_person_idx ON menura.item_person_tag (person_id, item_id);

-- The transcript of a raw item: at most one; replacing it replaces all of its segments.
CREATE TABLE menura.transcript (
    raw_item_id uuid        PRIMARY KEY REFERENCES menura.asset (id),
    format      text        NOT NULL CHECK (format IN ('webvtt', 'srt', 'json')),
    uploader_id uuid        NOT NULL,
    uploaded_at timestamptz NOT NULL
);

CREATE TABLE menura.transcript_segment (
    id          bigint   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    raw_item_id uuid     NOT NULL REFERENCES menura.asset (id) ON DELETE CASCADE,
    start_ms    bigint   NOT NULL CHECK (start_ms >= 0),
    end_ms      bigint   NOT NULL,
    speaker     text,
    text        text     NOT NULL,
    search      tsvector GENERATED ALWAYS AS (to_tsvector('simple', text)) STORED,
    CONSTRAINT transcript_segment_range_check CHECK (end_ms > start_ms)
);

-- Full-text search over the segments; the simple configuration keeps German and English words unstemmed.
CREATE INDEX transcript_segment_search_idx ON menura.transcript_segment USING gin (search);
CREATE INDEX transcript_segment_item_idx ON menura.transcript_segment (raw_item_id, start_ms);

-- A timeline is one document: ordered source ranges (clips), overlays, captions and chapter marks.
CREATE TABLE menura.timeline (
    id          uuid        PRIMARY KEY,
    uploader_id uuid        NOT NULL,
    title       text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    document    jsonb       NOT NULL,
    created_at  timestamptz NOT NULL
);

-- Lineage edges: a finished asset to its timeline, a timeline to each clip it uses. A clip's raw item is its
-- raw_item_id, so "all assets made from a stream" is an indexed walk over these edges.
CREATE TABLE menura.lineage_edge (
    child_kind  text NOT NULL CHECK (child_kind IN ('asset', 'timeline')),
    child_id    uuid NOT NULL,
    parent_kind text NOT NULL CHECK (parent_kind IN ('timeline', 'clip')),
    parent_id   uuid NOT NULL,
    PRIMARY KEY (child_kind, child_id, parent_kind, parent_id),
    CONSTRAINT lineage_edge_kinds_check CHECK ((child_kind = 'asset' AND parent_kind = 'timeline')
                                               OR (child_kind = 'timeline' AND parent_kind = 'clip'))
);

CREATE INDEX lineage_edge_parent_idx ON menura.lineage_edge (parent_kind, parent_id);

-- A finished asset (video or image with a verified upload) registered in the pool, with the timeline it was made from.
CREATE TABLE menura.registered_asset (
    asset_id      uuid        PRIMARY KEY REFERENCES menura.asset (id) ON DELETE CASCADE,
    uploader_id   uuid        NOT NULL,
    title         text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    timeline_id   uuid        NOT NULL REFERENCES menura.timeline (id),
    registered_at timestamptz NOT NULL
);

CREATE INDEX registered_asset_created_idx ON menura.registered_asset (registered_at DESC, asset_id DESC);
