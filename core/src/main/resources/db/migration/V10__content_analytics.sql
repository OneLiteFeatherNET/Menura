-- Content analytics (change add-content-analytics). Metric points of external posts, partitioned by the month of
-- collection, the latest value of every post and metric (the rollup the dashboard reads), the snapshots at fixed offsets
-- after publication, and the collection status of each channel. Points keep every collected value; the rollup and the
-- snapshots hold one row per key.

-- One raw point: the cumulative value of one metric of one external post at one collection. A metric the platform does
-- not provide has no row (it is unavailable, never zero). The dimensions are copied at write time, so a later change of
-- the post's series does not rewrite the points collected before it.
CREATE TABLE menura.metric_point (
    id                   uuid        NOT NULL,
    external_post_id     uuid        NOT NULL REFERENCES menura.external_post (id) ON DELETE CASCADE,
    channel_id           uuid        NOT NULL REFERENCES menura.channel (id) ON DELETE CASCADE,
    platform             text        NOT NULL CHECK (char_length(platform) BETWEEN 1 AND 40),
    format               text        NOT NULL CHECK (char_length(format) BETWEEN 1 AND 40),
    publish_hour         smallint    NOT NULL CHECK (publish_hour BETWEEN 0 AND 23),
    publish_weekday      smallint    NOT NULL CHECK (publish_weekday BETWEEN 1 AND 7),
    series_id            uuid        REFERENCES menura.content_series (id) ON DELETE SET NULL,
    -- Written by add-content-distribution, which renders posts; absent for imported posts and until then.
    layout_template      text        CHECK (layout_template IS NULL OR layout_template ~ '^[a-z0-9-]{1,40}$'),
    brand_kit_version    integer     CHECK (brand_kit_version IS NULL OR brand_kit_version >= 1),
    metric               text        NOT NULL CHECK (metric ~ '^[a-z_]{1,40}$'),
    value                bigint      NOT NULL CHECK (value >= 0),
    collected_at         timestamptz NOT NULL,
    PRIMARY KEY (collected_at, id)
) PARTITION BY RANGE (collected_at);

-- Drill-down: the points of one post and metric in collection-time order.
CREATE INDEX metric_point_post_metric_idx ON menura.metric_point (external_post_id, metric, collected_at);

-- The latest reading of every external post and metric, with the dimensions the aggregates group by. Overwritten by each
-- collection; the aggregates of the dashboard read this table only.
CREATE TABLE menura.metric_latest (
    external_post_id  uuid        NOT NULL REFERENCES menura.external_post (id) ON DELETE CASCADE,
    metric            text        NOT NULL CHECK (metric ~ '^[a-z_]{1,40}$'),
    channel_id        uuid        NOT NULL REFERENCES menura.channel (id) ON DELETE CASCADE,
    platform          text        NOT NULL,
    format            text        NOT NULL,
    published_at      timestamptz NOT NULL,
    publish_hour      smallint    NOT NULL CHECK (publish_hour BETWEEN 0 AND 23),
    publish_weekday   smallint    NOT NULL CHECK (publish_weekday BETWEEN 1 AND 7),
    series_id         uuid        REFERENCES menura.content_series (id) ON DELETE SET NULL,
    value             bigint      NOT NULL CHECK (value >= 0),
    collected_at      timestamptz NOT NULL,
    PRIMARY KEY (external_post_id, metric)
);

-- The aggregates group by channel, series and publish time within a period of publication.
CREATE INDEX metric_latest_channel_published_idx ON menura.metric_latest (channel_id, published_at);
CREATE INDEX metric_latest_series_idx ON menura.metric_latest (series_id, published_at);

-- The value of one metric of one post at one offset after publication. The as-of time is when it was taken; the age is
-- the time since publication at that moment. A snapshot with no value means the platform did not provide the metric.
CREATE TABLE menura.metric_snapshot (
    external_post_id  uuid        NOT NULL REFERENCES menura.external_post (id) ON DELETE CASCADE,
    offset_minutes    integer     NOT NULL CHECK (offset_minutes > 0),
    metric            text        NOT NULL CHECK (metric ~ '^[a-z_]{1,40}$'),
    value             bigint      CHECK (value IS NULL OR value >= 0),
    as_of             timestamptz NOT NULL,
    age_minutes       integer     NOT NULL CHECK (age_minutes >= 0),
    PRIMARY KEY (external_post_id, offset_minutes, metric)
);

-- The equal-age comparison reads the snapshots of one offset and metric.
CREATE INDEX metric_snapshot_offset_metric_idx ON menura.metric_snapshot (offset_minutes, metric);

-- The last collection of a channel: its outcome (collected, not_supported or failed), when it last succeeded or failed,
-- and the fixed reason text of the failure.
CREATE TABLE menura.metric_collection_status (
    channel_id      uuid        PRIMARY KEY REFERENCES menura.channel (id) ON DELETE CASCADE,
    outcome         text        NOT NULL CHECK (outcome IN ('collected', 'not_supported', 'failed')),
    last_success_at timestamptz,
    last_failure_at timestamptz,
    failure_reason  text        CHECK (failure_reason IS NULL OR char_length(failure_reason) <= 200)
);
