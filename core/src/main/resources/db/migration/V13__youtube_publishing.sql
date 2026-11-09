-- YouTube publishing (change add-youtube-integration): the steps of a publication gain playlist and thumbnail, every
-- YouTube post carries its explicit declarations, the resumable upload of a post is recorded byte by byte, and the quota
-- each channel uses per Pacific day is accounted. Rows are changed in place; the session location is stored sealed.

-- The publication steps: the step-wise YouTube publication adds the playlist and the thumbnail step.
ALTER TABLE menura.post_step DROP CONSTRAINT post_step_step_check;
ALTER TABLE menura.post_step ADD CONSTRAINT post_step_step_check
    CHECK (step IN ('upload', 'schedule', 'metadata', 'publish', 'playlist', 'thumbnail'));

-- The explicit declarations of a post on YouTube: the visibility a person chose and the made-for-kids answer. A post
-- without them is refused for scheduling; no platform default is used.
CREATE TABLE menura.youtube_post_declaration (
    post_id              uuid    PRIMARY KEY REFERENCES menura.post (id) ON DELETE CASCADE,
    visibility_declared  boolean NOT NULL DEFAULT false,
    made_for_kids        boolean,
    planned_short        boolean NOT NULL DEFAULT false
);

-- The resumable upload of a post. The session location is sealed with the token key (it lets anyone add bytes), the
-- confirmed byte count is what the platform reported, and the applied visibility is the one read back after upload.
CREATE TABLE menura.post_upload (
    post_id            uuid        PRIMARY KEY REFERENCES menura.post (id) ON DELETE CASCADE,
    session_sealed     bytea,
    total_bytes        bigint      NOT NULL CHECK (total_bytes > 0),
    confirmed_bytes    bigint      NOT NULL DEFAULT 0 CHECK (confirmed_bytes >= 0),
    applied_visibility text        CHECK (applied_visibility IN ('private', 'unlisted', 'public')),
    visibility_problem text        CHECK (char_length(visibility_problem) <= 500),
    updated_at         timestamptz NOT NULL,
    CONSTRAINT post_upload_confirmed_check CHECK (confirmed_bytes <= total_bytes)
);

-- The quota a channel has used on one Pacific day: units of all methods and the number of videos.insert calls.
CREATE TABLE menura.youtube_quota_usage (
    channel_id uuid    NOT NULL REFERENCES menura.channel (id) ON DELETE CASCADE,
    quota_day  date    NOT NULL,
    units      integer NOT NULL DEFAULT 0 CHECK (units >= 0),
    uploads    integer NOT NULL DEFAULT 0 CHECK (uploads >= 0),
    PRIMARY KEY (channel_id, quota_day)
);
