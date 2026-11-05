# Proposal

Conventional Commits: `feat(analytics)` (code PR). Phase: 1 (Platforms & Statistics). Depends on: `add-access-control`, `add-channel-connections`, `add-external-post-import`.

Strand: content.

## Why

Optimisation decisions (which hook, length, layout, posting hour or channel works best) need the performance of every post over time. Today those numbers are visible only inside each platform, so the team cannot compare channels, and nothing can be fed back to Claude when it proposes revisions. Phase 1 makes the numbers of every external post, whether imported from a channel or published by Menura, available in one place.

## What Changes

- Performance metrics are collected on a schedule for every external post from the native analytics of its platform, through the metrics operation of the adapter port. The metrics a platform provides differ, and availability is shown per channel: views, watch time, retention curve, likes, comments, shares and follower delta where provided; anything else is recorded as unavailable.
- Metric points are stored in PostgreSQL, the existing OLF database, in monthly range-partitioned tables. No additional database or extension is introduced.
- A scheduled job maintains daily and weekly rollups. The dashboard reads rollups only, so aggregates by channel, format and posting hour answer in under one second on two years of data. Raw points are kept for drill-down.
- Where the components exist, aggregates also group by layout template, tags or topic, source stream and contributor, and internal events are linked to the metrics: the recording a post was cut from, stream metadata, and the overlay state at scheduling time. These are Where-clauses; their components arrive in later phases.
- Analytics are readable through REST for the web dashboard. Where the hub MCP server is available, the same reads are offered through MCP so Claude can base optimisation proposals on them.
- Comparisons of metrics before and after an accepted revision, and of recycled posts with their originals, are specified and built in `add-content-distribution` (phase 2).
- Could: export to a Prometheus-compatible endpoint for the existing OLF Grafana.

## Capabilities

### New Capabilities
- `content-analytics`: scheduled collection of external post metrics, partitioned storage, rollup maintenance, linking of internal events where available, the aggregate query model with its performance target, and read access through REST (and MCP where available).

### Modified Capabilities
- None.

## Impact

- PostgreSQL (CloudNativePG, PostgreSQL 17, standard image): new partitioned metric tables and rollup tables; partitions are created ahead of each month.
- Jobs component: the collection job and the rollup job run as Kubernetes CronJobs from the same codebase as the server.
- Platform integrations: each provides its native metrics through the metrics operation of the adapter port. The phase 1 insights changes (`add-youtube-insights`, `add-twitch-insights`, `add-tiktok-insights`, `add-instagram-insights`) implement it; no third-party analytics service is used.
- Web: a dashboard that reads rollups.
- MCP: read-only analytics tools are added by `add-hub-mcp-server` when that change exists. Its tasks are not part of this change.
- Storage growth: two years of raw points per external post and channel; partition and rollup sizing is in design.md.
