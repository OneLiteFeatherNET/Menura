-- Content analytics rollups (change add-content-analytics, tasks 2 to 8). The daily and weekly rollups, the per-offset
-- groups of the equal-age comparison and the dirty days the rollup job rebuilds. Internal metric sources write into the
-- metric points too (a source name, an estimate flag and the day the value refers to), so their points are installation,
-- channel, post or series scoped and the platform columns are absent for them.

-- Internal sources: platform columns are required for platform points only.
ALTER TABLE menura.metric_point ALTER COLUMN external_post_id DROP NOT NULL;
ALTER TABLE menura.metric_point ALTER COLUMN channel_id DROP NOT NULL;
ALTER TABLE menura.metric_point ALTER COLUMN platform DROP NOT NULL;
ALTER TABLE menura.metric_point ALTER COLUMN format DROP NOT NULL;
ALTER TABLE menura.metric_point ALTER COLUMN publish_hour DROP NOT NULL;
ALTER TABLE menura.metric_point ALTER COLUMN publish_weekday DROP NOT NULL;
ALTER TABLE menura.metric_point ADD COLUMN source text NOT NULL DEFAULT 'platform' CHECK (source ~ '^[a-z_]{1,40}$');
ALTER TABLE menura.metric_point ADD COLUMN estimate boolean NOT NULL DEFAULT false;
ALTER TABLE menura.metric_point ADD COLUMN day date;
ALTER TABLE menura.metric_point ADD CONSTRAINT metric_point_platform_dimensions_check CHECK (
    source <> 'platform'
    OR (external_post_id IS NOT NULL AND channel_id IS NOT NULL AND platform IS NOT NULL AND format IS NOT NULL
        AND publish_hour IS NOT NULL AND publish_weekday IS NOT NULL));
ALTER TABLE menura.metric_point ADD CONSTRAINT metric_point_internal_day_check CHECK (source = 'platform' OR day IS NOT NULL);
ALTER TABLE menura.metric_point ADD CONSTRAINT metric_point_estimate_check CHECK (NOT estimate OR source <> 'platform');

-- The rollup job reads the internal points of one day.
CREATE INDEX metric_point_internal_day_idx ON menura.metric_point (day) WHERE source <> 'platform';

-- The render dimensions of the latest value, copied from the points: the layout template and brand kit version a post was
-- rendered with, absent for imported posts.
ALTER TABLE menura.metric_latest ADD COLUMN layout_template text
    CHECK (layout_template IS NULL OR layout_template ~ '^[a-z0-9-]{1,40}$');
ALTER TABLE menura.metric_latest ADD COLUMN brand_kit_version integer CHECK (brand_kit_version IS NULL OR brand_kit_version >= 1);

-- The daily rollup: one row per day, grain and metric. The day of a platform value is the day of publication; the day of an
-- internal value is the day it refers to. A platform grain has a row for every metric, so the post count is exact on each.
-- A null dimension means the grain does not have it (an internal installation value has no channel, a platform value has
-- no series). The rollup job rebuilds a whole day, so no unique key is needed.
CREATE TABLE menura.metric_rollup_day (
    day               date     NOT NULL,
    channel_id        uuid,
    format            text,
    publish_hour      smallint CHECK (publish_hour IS NULL OR publish_hour BETWEEN 0 AND 23),
    series_id         uuid,
    layout_template   text,
    brand_kit_version integer,
    source            text     NOT NULL CHECK (source ~ '^[a-z_]{1,40}$'),
    estimate          boolean  NOT NULL,
    metric            text     NOT NULL CHECK (metric ~ '^[a-z_]{1,40}$'),
    posts             integer  NOT NULL CHECK (posts >= 0),
    values_count      integer  NOT NULL CHECK (values_count >= 0),
    total             bigint   NOT NULL
);

CREATE INDEX metric_rollup_day_day_idx ON menura.metric_rollup_day (day);
CREATE INDEX metric_rollup_day_series_idx ON menura.metric_rollup_day (series_id, day) WHERE series_id IS NOT NULL;

-- The weekly rollup: the same grains per ISO week (week_start is the Monday in the installation time zone), summed from the
-- daily rows. The series comparison reads this table.
CREATE TABLE menura.metric_rollup_week (
    week_start        date     NOT NULL,
    channel_id        uuid,
    format            text,
    publish_hour      smallint CHECK (publish_hour IS NULL OR publish_hour BETWEEN 0 AND 23),
    series_id         uuid,
    layout_template   text,
    brand_kit_version integer,
    source            text     NOT NULL CHECK (source ~ '^[a-z_]{1,40}$'),
    estimate          boolean  NOT NULL,
    metric            text     NOT NULL CHECK (metric ~ '^[a-z_]{1,40}$'),
    posts             integer  NOT NULL CHECK (posts >= 0),
    values_count      integer  NOT NULL CHECK (values_count >= 0),
    total             bigint   NOT NULL
);

CREATE INDEX metric_rollup_week_week_idx ON menura.metric_rollup_week (week_start);
CREATE INDEX metric_rollup_week_series_idx ON menura.metric_rollup_week (series_id, week_start) WHERE series_id IS NOT NULL;

-- The equal-age groups: the snapshot values of the posts of one channel published on one day at one hour, per offset and
-- metric. A median cannot be summed, so the values are kept; the comparison takes the median over the groups it reads.
-- A post younger than the offset has no snapshot and so no value in the group.
CREATE TABLE menura.metric_offset_day (
    day              date      NOT NULL,
    channel_id       uuid      NOT NULL REFERENCES menura.channel (id) ON DELETE CASCADE,
    publish_hour     smallint  NOT NULL CHECK (publish_hour BETWEEN 0 AND 23),
    publish_weekday  smallint  NOT NULL CHECK (publish_weekday BETWEEN 1 AND 7),
    offset_minutes   integer   NOT NULL CHECK (offset_minutes > 0),
    metric           text      NOT NULL CHECK (metric ~ '^[a-z_]{1,40}$'),
    snapshot_values  bigint[]  NOT NULL,
    PRIMARY KEY (day, channel_id, publish_hour, offset_minutes, metric)
);

-- The comparison reads one offset and metric, optionally of one channel.
CREATE INDEX metric_offset_day_offset_idx ON menura.metric_offset_day (offset_minutes, metric, publish_hour, channel_id);

-- The days whose rollups must be rebuilt. A collection marks the day of every post it stores a value for; the rollup job
-- locks the row, rebuilds the day and removes the mark in one transaction.
CREATE TABLE menura.metric_rollup_dirty (
    day date PRIMARY KEY
);
