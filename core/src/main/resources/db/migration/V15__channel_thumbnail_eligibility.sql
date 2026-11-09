-- Custom thumbnail eligibility per channel (change add-youtube-integration, task 4.6). The platform does not report it, so
-- Menura records the answer of the platform to a thumbnail step: allowed after a set, not allowed after a refusal. A channel
-- without a row was never asked, and no warning is shown for it.
CREATE TABLE menura.channel_thumbnail (
    channel_id  uuid        PRIMARY KEY REFERENCES menura.channel (id) ON DELETE CASCADE,
    allowed     boolean     NOT NULL,
    reason      text        CHECK (char_length(reason) <= 500),
    decided_at  timestamptz NOT NULL
);
