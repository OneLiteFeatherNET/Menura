# Proposal

Conventional Commits: `feat(analytics)` (code PR). Phase: 1 (Pool, Calendar & Analytics). Depends on: `add-access-control`, `add-channel-connections`, `add-external-post-import`, `add-content-series`, `add-brand-kit`.

Strand: content.

## Why

Optimisation decisions (which hook, length, layout, posting hour or channel works best) need the performance of every post over time. Today those numbers are visible only inside each platform, so the team cannot compare channels, and nothing can be fed back to Claude when it proposes revisions. Phase 1 makes the numbers of every external post, whether imported from a channel or published by Menura, available in one place. Phase 1 also makes it measurable which series of shorts, and which layout template and brand kit version, bring new players: shorts are compared by engaged views and average view percentage, not by views.

## What Changes

- Performance metrics are collected on a schedule for every external post from the native analytics of its platform, through the metrics operation of the adapter port. The metrics a platform provides differ, and availability is shown per channel: views, watch time, retention curve, likes, comments, shares and follower delta where provided; anything else is recorded as unavailable.
- Metric points are stored in PostgreSQL, the existing OLF database, in monthly range-partitioned tables. No additional database or extension is introduced.
- A scheduled job maintains daily and weekly rollups. The dashboard reads rollups only, so aggregates by channel, format and posting hour answer in under one second on two years of data. Raw points are kept for drill-down.
- Snapshots at fixed offsets: the hub records the cumulative metrics of every external post at +1 hour, +2 hours, +4 hours, +8 hours, +24 hours, +48 hours and +7 days after its publication, and daily afterwards, each with its as-of time; a taken snapshot stays unchanged, and later values are kept as raw points.
- Comparison at equal age: posts are compared by publish hour and weekday in the installation time zone (default Europe/Berlin), each at the same age, using its snapshot at a chosen offset (default +24 hours) and the median over posts; posts younger than the offset are excluded.
- Metric points carry the series (from `add-content-series`) and the layout template and brand kit version (from `add-brand-kit`) a post was rendered with, so aggregates group and filter by them from rollups. Where the components exist, aggregates also group by tags or topic, source stream and contributor, and internal events are linked to the metrics: the recording a post was cut from, stream metadata, and the overlay state at scheduling time. These are Where-clauses; their components arrive in later phases.
- Internal metric sources, such as join attribution from `add-join-attribution`, may write metric points for a channel, a post or a series under their own registered metric names. They are stored, rolled up and read like platform metrics, marked with their source, and estimates are flagged.
- A series comparison per series and week shows engaged views, average view percentage (or unavailable) and the metrics of registered internal sources, read from rollups.
- Analytics are readable through REST for the web dashboard. Where the hub MCP server is available, the same reads are offered through MCP so Claude can base optimisation proposals on them.
- Comparisons of metrics before and after an accepted revision, and of recycled posts with their originals, are specified and built in `add-content-distribution` (phase 1).
- Could: export to a Prometheus-compatible endpoint for the existing OLF Grafana.

## Capabilities

### New Capabilities
- `content-analytics`: scheduled collection of external post metrics, partitioned storage, rollup maintenance, linking of internal events where available, series and layout template dimensions, internal metric sources, the series comparison, snapshots at fixed offsets, the comparison at equal age, the aggregate query model with its performance target, and read access through REST (and MCP where available).

### Modified Capabilities
- None.

## Impact

- PostgreSQL (CloudNativePG, PostgreSQL 17, standard image): new partitioned metric tables and rollup tables; partitions are created ahead of each month.
- Jobs component: the collection job and the rollup job run as Kubernetes CronJobs from the same codebase as the server.
- Platform integrations: each provides its native metrics through the metrics operation of the adapter port. The insights changes implement it: `add-youtube-insights` in phase 1, `add-twitch-insights`, `add-tiktok-insights` and `add-instagram-insights` in phase 2; no third-party analytics service is used.
- Web: a dashboard that reads rollups, with the series comparison and the comparison at equal age.
- MCP: read-only analytics tools are added by `add-hub-mcp-server` when that change exists. Its tasks are not part of this change.
- Storage growth: two years of raw points per external post and channel; partition and rollup sizing is in design.md.
