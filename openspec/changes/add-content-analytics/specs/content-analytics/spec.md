## Purpose

Content analytics keeps the performance history of every external post, whether imported from a channel or published by Menura, and of the channels they belong to. It links each post to the internal events that explain it where those components exist, and answers aggregate questions fast enough for the dashboard and for AI-assisted optimisation. Metric points live in PostgreSQL, partitioned by month, and aggregates are read from precomputed rollups. This capability defines what is collected, how it is stored and queried, and who can read it.

## ADDED Requirements

### Requirement: Scheduled metric collection
(Must) The scheduled analytics job SHALL collect the performance metrics of every external post from the native analytics of its platform, through the metrics operation of the adapter port: views, engaged views, average view percentage, watch time, retention curve, likes, comments, shares and follower delta, each where the platform provides it.

#### Scenario: Metrics collected for an external post
- **GIVEN** an external post on a channel that provides views and likes
- **WHEN** the scheduled collection runs
- **THEN** the job stores a metric point for that post and channel with the views and likes and the time of collection

#### Scenario: Imported and published posts collected alike
- **GIVEN** one imported external post and one post published by Menura, on channels that both provide views
- **WHEN** the scheduled collection runs
- **THEN** the job stores metric points for both posts through the same operation

#### Scenario: Metric not provided by the channel
- **GIVEN** a channel that does not provide a retention curve
- **WHEN** the scheduled collection runs for an external post on that channel
- **THEN** the job stores no retention value for that post
- **AND** the metric is reported as unavailable, not as zero

#### Scenario: Platform availability shown
- **GIVEN** a platform integration that provides likes and views but no retention curve
- **WHEN** a person reads the analytics of an external post on that channel
- **THEN** the views and likes are shown and the retention curve is shown as not provided by the platform

#### Scenario: Collection failure
- **GIVEN** a channel that cannot be reached during the scheduled collection
- **WHEN** the collection fails
- **THEN** the job records the failure for that channel and keeps the points collected before the failure

### Requirement: Snapshots at fixed offsets
(Must) The hub SHALL record the cumulative metrics of every external post at +1 hour, +2 hours, +4 hours, +8 hours, +24 hours, +48 hours and +7 days after its publication, and daily after that; SHALL take each snapshot in the first collection run at or after its offset and store it with its as-of time; and SHALL keep a taken snapshot unchanged, while the raw points keep every later collected value.

#### Scenario: Snapshot taken at its offset
- **GIVEN** an external post published at 10:00 and a collection run at 11:05, the first run at or after 11:00
- **WHEN** that run collects the post
- **THEN** the +1 hour snapshot holds the cumulative values with the as-of time 11:05

#### Scenario: A taken snapshot stays unchanged
- **GIVEN** a stored +24 hour snapshot with 100 views
- **WHEN** a later collection receives 120 views for the post
- **THEN** the +24 hour snapshot still holds 100 views with its original as-of time, and the raw points keep both collected values

#### Scenario: Daily snapshots after seven days
- **GIVEN** an external post published nine days earlier
- **WHEN** the daily collection runs
- **THEN** the snapshots of day eight and day nine are stored, each with its as-of time

#### Scenario: Metric not provided in a snapshot
- **GIVEN** a channel that does not provide watch time
- **WHEN** the +24 hour snapshot of its post is taken
- **THEN** the watch time of that snapshot is stored as absent and not as zero

### Requirement: Metric points in partitioned PostgreSQL tables
(Must) The hub SHALL store every metric point in PostgreSQL with its external post, channel, metric name, value and collection time, in tables partitioned by month, and SHALL keep all raw points.

#### Scenario: Point history of an external post
- **GIVEN** an external post whose metrics were collected three times
- **WHEN** its history is read
- **THEN** the hub returns three points per metric in collection-time order

#### Scenario: Points land in their month partition
- **GIVEN** a metric point collected on a given day of a month
- **WHEN** the point is stored
- **THEN** it is stored in the partition of that month

### Requirement: Internal events linked to metrics
(Must) Where recording ingest and the content library are available, the hub SHALL link each external post cut from a recording to that recording, its stream title and category and its contributor; and where the overlay system is available, the hub SHALL also link the external post to the overlay state that was current when the post was scheduled.

#### Scenario: Post linked to its source stream
- **GIVEN** an external post cut from a recording of a stream with a given title and category
- **WHEN** the post's analytics are read
- **THEN** the analytics show the source recording, the stream title and category, and the recording's contributor

#### Scenario: Post without a source recording
- **GIVEN** an imported external post that was not cut from any recording in the hub
- **WHEN** the post's analytics are read
- **THEN** no source recording is shown and the analytics are shown without error

### Requirement: Series and layout template dimensions
(Must) The hub SHALL store with each metric point of an external post the series that post belongs to, if any, and the layout template and brand kit version it was rendered with, so that rollups group and filter the points by these values.

#### Scenario: Points grouped by series and layout template
- **GIVEN** two external posts of one series, rendered with the same layout template and brand kit version, and one imported post without a series
- **WHEN** a person groups the views of the last month by series and by layout template
- **THEN** the two posts are summed under their series and the imported post is grouped under no series

#### Scenario: Imported post without series or template
- **GIVEN** an imported external post that belongs to no series and was not rendered by Menura
- **WHEN** its metric points are stored
- **THEN** the series, layout template and brand kit version are stored as absent

#### Scenario: Series reassigned
- **GIVEN** an external post moved to another series after some of its points were collected
- **WHEN** the next collection runs
- **THEN** the new points carry the new series and the earlier points keep the series they were collected with

### Requirement: Dashboard aggregates read rollups within one second
(Must) The hub SHALL answer aggregate analytics queries grouped or filtered by channel, format and posting hour from precomputed rollups within one second on two years of data, and the dashboard SHALL read rollups only. Where content distribution, the content library and the brand kit are available, the same SHALL apply to queries grouped or filtered by layout template, tags or topic, source stream and contributor.

#### Scenario: Two-year aggregate by channel and format
- **GIVEN** two years of metric points across all external posts and their rollups
- **WHEN** a person requests the total views per channel and format for the last quarter
- **THEN** the hub returns the result within one second from the rollups

#### Scenario: Posting-hour comparison
- **GIVEN** two years of metric points and their rollups
- **WHEN** a person requests the posting-hour comparison for one channel
- **THEN** the hub returns the comparison at equal age defined in "Comparison at equal age" within one second from the rollups

### Requirement: Internal metric sources
(Must) The hub SHALL accept metric points from registered internal sources for the installation as a whole, a channel, an external post or a series, each under its own registered metric names; SHALL store, roll up and read these points like platform metrics; SHALL mark every point with its source; and SHALL keep an estimate flag on points that a source reports as estimates and show it on every read.

#### Scenario: First joins per day in total and per stated source
- **GIVEN** a registered internal source that writes the metric "first joins" for the installation on a given day, with the answers to the optional in-game question as stated sources
- **WHEN** the source writes the points
- **THEN** the hub stores a total point and one point per stated source with the metric name, the value and the source, without a channel, the daily rollup of the installation includes them, and each per-platform number is labelled "stated by players" and shown with the answer rate

#### Scenario: Post estimate over the 72 hour window
- **GIVEN** a registered source that reports first joins attributed to external posts as an estimate, splitting each first join evenly over all posts published in the 72 hours before it, restricted to the posts of the answered platform when the answer names a platform
- **WHEN** a person reads the analytics of a post
- **THEN** the value is shown with the estimate flag and the 72 hour attribution window

#### Scenario: Estimates kept in rollups
- **GIVEN** a daily rollup that includes estimated and measured points of the same metric
- **WHEN** a person reads the rollup
- **THEN** the rollup shows the sum and marks that it contains estimates

#### Scenario: Unregistered source or metric refused
- **GIVEN** a source that is not registered, or a registered source that writes a metric name it did not register
- **WHEN** the source writes a point
- **THEN** the hub refuses the point and stores nothing

#### Scenario: Source cannot change platform points
- **GIVEN** a stored platform point of an external post
- **WHEN** an internal source writes a point for the same post
- **THEN** the platform point is unchanged and the internal point is stored with its own source

### Requirement: Comparison at equal age
(Must) The dashboard SHALL compare posts at equal age by publish hour and by weekday in the installation time zone (configured, default Europe/Berlin), using for each post its snapshot at a chosen offset (default +24 hours) and the median over the posts of each group; SHALL exclude every post younger than the chosen offset and report the count of posts used; and SHALL show a metric that a platform does not deliver as unavailable, never as zero.

#### Scenario: Median per publish hour at +24 hours
- **GIVEN** three posts published at 18:00 Europe/Berlin with +24 hour views of 100, 200 and 900
- **WHEN** a person requests the equal-age comparison at +24 hours
- **THEN** the 18:00 group shows a median of 200 views from three posts

#### Scenario: Younger posts excluded
- **GIVEN** a post published 10 hours ago and a chosen offset of +24 hours
- **WHEN** the comparison is computed
- **THEN** the post is excluded and not counted

#### Scenario: Chosen offset used
- **GIVEN** a request for the comparison at +48 hours
- **WHEN** the comparison is computed
- **THEN** only the +48 hour snapshots are used and posts younger than 48 hours are excluded

#### Scenario: Weekday in the installation time zone
- **GIVEN** a post published at 2026-11-08T23:30:00Z, which is Monday 00:30 in Europe/Berlin
- **WHEN** the comparison is grouped by weekday
- **THEN** the post is counted under Monday

#### Scenario: Missing metric shown as unavailable
- **GIVEN** a group whose posts all come from a channel that does not provide retention
- **WHEN** the comparison is shown
- **THEN** the retention median of that group is shown as unavailable and the count of posts with a retention value is zero

### Requirement: Series comparison
(Must) The dashboard SHALL show, per series and per week, the engaged views, the average view percentage and the metrics of registered internal sources, read from rollups within the one-second budget of dashboard aggregates; a value the platform does not deliver SHALL be shown as unavailable and not as zero; and series SHALL be ranked by engaged views, not by views.

#### Scenario: Weekly comparison per series
- **GIVEN** a series with posts on two channels in one week
- **WHEN** a person requests the series comparison for that week
- **THEN** the dashboard shows the engaged views and average view percentage of the series for that week, read from rollups

#### Scenario: Average view percentage unavailable
- **GIVEN** a series whose posts all come from a channel that does not deliver average view percentage
- **WHEN** the comparison is shown
- **THEN** the average view percentage is shown as unavailable and the engaged views are still shown

#### Scenario: Internal source metric in the comparison
- **GIVEN** a registered source that reports first joins of a series for a week as an estimate, the sum of the post estimates of the series' posts in that week
- **WHEN** the comparison for that week is shown
- **THEN** the first joins of the series are shown with the estimate flag

#### Scenario: Ranking by engaged views
- **GIVEN** series A with more views than series B but fewer engaged views
- **WHEN** the series are ranked by the comparison
- **THEN** series B ranks above series A

#### Scenario: Comparison within one second
- **GIVEN** two years of metric points with their rollups, and 20 series
- **WHEN** a person requests the comparison of the last 52 weeks
- **THEN** the hub returns it within one second from the rollups

### Requirement: Rollups maintained by a job
(Must) A scheduled job SHALL maintain daily and weekly rollups of the metric points, and SHALL rebuild the rollups of a period when a channel corrects its numbers.

#### Scenario: Rollup equals raw points
- **GIVEN** a daily rollup of views per channel
- **WHEN** the rollup is compared with the raw points of the same day
- **THEN** the rollup value equals the sum of those raw points

#### Scenario: Correction rebuilds the rollup
- **GIVEN** a channel that corrects the views of a day
- **WHEN** the rollup job runs
- **THEN** the daily and weekly rollups of that day match the corrected raw points

#### Scenario: Raw points remain available
- **GIVEN** a rollup for a past period
- **WHEN** a person requests the raw points of that period through the drill-down endpoint
- **THEN** the hub returns the raw points

### Requirement: Monthly partitions are created ahead
(Must) The job SHALL create the partition of each month before the first metric point of that month is collected.

#### Scenario: Partition exists before collection
- **GIVEN** the first day of a new month
- **WHEN** the collection runs for that day
- **THEN** a partition for that month exists and receives the points

### Requirement: Analytics readable through REST
(Must) The hub SHALL expose analytics for reading through REST for the web dashboard, and SHALL NOT allow any analytics read or write that changes an external post, a schedule or an approval.

#### Scenario: Dashboard reads aggregates
- **GIVEN** a person with the viewer role or higher
- **WHEN** the dashboard requests aggregates over REST
- **THEN** the hub returns the aggregates from the rollups

#### Scenario: Analytics cannot change a post
- **GIVEN** a REST analytics call that requests a change of an external post's status
- **WHEN** the call is made
- **THEN** the hub refuses the call and the post stays unchanged

#### Scenario: Post outside the caller's roles
- **GIVEN** an external post whose channel the caller may not see
- **WHEN** the caller requests the analytics of that post
- **THEN** the hub answers 404 as a Problem Details error

### Requirement: Analytics readable through MCP
(Must) Where the hub MCP server is available, the hub SHALL expose the same read-only analytics through MCP, and SHALL NOT allow any analytics MCP call that changes an external post, a schedule or an approval.

#### Scenario: MCP client reads analytics for optimisation
- **GIVEN** an MCP client authorised for analytics reads
- **WHEN** the client requests the best-performing hour per channel
- **THEN** the hub returns the aggregates the client needs to propose revisions

#### Scenario: Analytics cannot change a post through MCP
- **GIVEN** an MCP client that requests a change of an external post's status through an analytics call
- **WHEN** the call is made
- **THEN** the hub refuses the call and the post stays unchanged

### Requirement: Prometheus-compatible export
(Could) Where enabled, the hub SHALL be able to export the aggregated metrics through a Prometheus-compatible endpoint for the existing OLF Grafana.

#### Scenario: Grafana reads the export
- **GIVEN** the export endpoint is enabled
- **WHEN** Grafana scrapes it
- **THEN** it receives the aggregated external post metrics by channel
