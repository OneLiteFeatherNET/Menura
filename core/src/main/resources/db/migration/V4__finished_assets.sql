-- Finished assets, raw material and the presigned multipart upload sessions that create them (change add-asset-upload).
-- Media bytes live in the private Menura bucket of the object storage; these rows hold only metadata and the
-- storage key. A session becomes an asset only after its checksum has been verified, so no half-applied upload is listed.

-- One row per upload: the declaration the editor made, the multipart upload it runs in the storage and its state.
-- A flagged session (a checksum already recorded for the same uploader) has no storage upload until it is confirmed.
CREATE TABLE menura.upload_session (
    id                 uuid        PRIMARY KEY,
    uploader_id        uuid        NOT NULL,
    kind               text        NOT NULL CHECK (kind IN ('video', 'image', 'raw_material')),
    format             text        NOT NULL,
    duration_seconds   integer,
    width              integer     NOT NULL CHECK (width BETWEEN 1 AND 16384),
    height             integer     NOT NULL CHECK (height BETWEEN 1 AND 16384),
    size_bytes         bigint      NOT NULL CHECK (size_bytes > 0),
    checksum           text        NOT NULL,
    part_size_bytes    bigint      NOT NULL CHECK (part_size_bytes BETWEEN 5242880 AND 99614720),
    part_count         integer     NOT NULL CHECK (part_count BETWEEN 1 AND 10000),
    stream_title       text,
    stream_date        date,
    state              text        NOT NULL CHECK (state IN ('flagged', 'uploading', 'available', 'failed', 'cancelled')),
    duplicate_of       uuid,
    storage_key        text        NOT NULL,
    storage_upload_id  text,
    failure_reason     text,
    asset_id           uuid,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    CONSTRAINT upload_session_duration_check CHECK ((kind = 'image') = (duration_seconds IS NULL)),
    CONSTRAINT upload_session_raw_check CHECK ((kind = 'raw_material') = (stream_title IS NOT NULL)
                                               AND (kind = 'raw_material') = (stream_date IS NOT NULL))
);

CREATE INDEX upload_session_uploader_idx ON menura.upload_session (uploader_id);

-- The parts the storage has confirmed for a session, as reported by the storage itself (ListParts).
CREATE TABLE menura.upload_session_part (
    session_id      uuid    NOT NULL REFERENCES menura.upload_session (id) ON DELETE CASCADE,
    part_number     integer NOT NULL CHECK (part_number >= 1),
    size_bytes      bigint  NOT NULL CHECK (size_bytes > 0),
    etag            text    NOT NULL,
    checksum_sha256 text,
    PRIMARY KEY (session_id, part_number)
);

-- A finished asset or raw material item whose upload has been verified and is listed.
CREATE TABLE menura.asset (
    id               uuid        PRIMARY KEY,
    uploader_id      uuid        NOT NULL,
    kind             text        NOT NULL CHECK (kind IN ('video', 'image', 'raw_material')),
    format           text        NOT NULL,
    duration_seconds integer,
    width            integer     NOT NULL CHECK (width BETWEEN 1 AND 16384),
    height           integer     NOT NULL CHECK (height BETWEEN 1 AND 16384),
    size_bytes       bigint      NOT NULL CHECK (size_bytes > 0),
    checksum         text        NOT NULL,
    stream_title     text,
    stream_date      date,
    storage_key      text        NOT NULL UNIQUE,
    created_at       timestamptz NOT NULL,
    CONSTRAINT asset_duration_check CHECK ((kind = 'image') = (duration_seconds IS NULL)),
    CONSTRAINT asset_raw_check CHECK ((kind = 'raw_material') = (stream_title IS NOT NULL)
                                      AND (kind = 'raw_material') = (stream_date IS NOT NULL))
);

ALTER TABLE menura.upload_session
    ADD CONSTRAINT upload_session_asset_fk FOREIGN KEY (asset_id) REFERENCES menura.asset (id);

-- Duplicate detection looks up a checksum for one uploader; the listing pages newest first.
CREATE INDEX asset_uploader_checksum_idx ON menura.asset (uploader_id, checksum);
CREATE INDEX asset_created_idx ON menura.asset (created_at DESC, id DESC);
