# Design

## Context

External posts and their channels live in PostgreSQL (see `add-external-post-import`); channels, platform connections and the metrics operation of the adapter port come from `add-channel-connections`. Metric points are a time series: many small rows per post, channel and metric, collected repeatedly for two years. Requirements are in `specs/content-analytics`; motivation is in proposal.md. Principle 1 keeps metadata and analytics metric points in the OLF CloudNativePG cluster (PostgreSQL 17, standard image, no extensions beyond it), with no further database. Principle 2 applies: the cluster runs no media work. The collection and rollup jobs run as Kubernetes CronJobs from the jobs component.

Recordings, the content library, content distribution, the brand kit, the overlay and the hub MCP server belong to later phases. Every requirement that needs one of them is written as a "Where ... is available" clause in the spec, and its tasks are not part of this change; the change that provides the component implements it.

## Goals / Non-Goals

**Goals:**
- Aggregate reads over two years of points in under one second, served from rollups.
- Raw points kept; daily and weekly rollups maintained by a job.
- Partitions that keep each month's table small and let old months be maintained without touching recent ones.
- A single read path for the dashboard (REST), and for MCP once `add-hub-mcp-server` exists.
- Imported and published external posts analysed in the same way.

**Non-Goals:**
- No write path from analytics into posts, schedules or approvals.
- No prediction model in this change; optimisation proposals remain with the AI client.
- No retention or deletion policy for raw points is defined here; it is an open question for the operator.
- No additional database, column store or PostgreSQL extension.
- No tasks for the Where-clause features (internal event links, distribution and library dimensions, comparisons, MCP reads). They are implemented by the changes that provide their components.

## Decisions

**Monthly range-partitioned tables in the existing PostgreSQL.** Metric points go into a table partitioned by collection month; the job creates the next month's partition before its first point arrives. Partitioning keeps each query's scan to the months it covers and keeps maintenance of a single month local, using only the standard image. Alternatives considered:
- ClickHouse (rejected: an extra stateful system to operate on the cluster, with worse cold-start recovery; the aggregate speed it offers is met here by rollups over partitions).
- TimescaleDB (rejected: not in the standard OLF PostgreSQL image, so it would need a custom image and extension upgrades).
- Plain, unpartitioned PostgreSQL tables (rejected: the table grows for two years without pruning or per-month maintenance, and every aggregate scans it).

**Rollups precomputed by a job, read only by the dashboard.** Daily rollups aggregate the raw points of a day per channel, format and posting hour; weekly rollups aggregate daily rollups. Where the distribution, library and brand kit components exist, their dimensions (layout template, tags or topic, source stream, contributor) are added to the rollups by the change that provides them. The dashboard and MCP aggregate reads use rollups only, which is what keeps them under one second. Alternative: aggregating raw points at query time (rejected: scans two years of rows per request and fails the requirement).

**Dimensions stored with each point.** Each metric point carries its external post, channel, platform, format and posting hour as stored values, so that rollups do not join back into the post table at build time. Where the other components exist, their dimension values are stored with the point too. Alternative: joining at rollup time (rejected: the rollup job would depend on the state of posts that were changed after collection).

**Unavailable is not zero.** A metric a channel does not provide is stored as absent; aggregates skip absent values and report the count of points used. Alternative: storing zero (rejected: it distorts averages and comparisons).

**Native platform metrics through the adapter port.** Each platform integration provides its metrics through the metrics operation of the adapter port (`add-channel-connections`). The collection job calls that operation for each external post and stores what it returns, with the availability each platform declares.
- Rejected: a third-party analytics aggregator. Principle 13 forbids a service between Menura and a platform, and it would add a second source of truth for the same numbers.
- Rejected: scraping public pages. It is not an official interface and breaks the minimal-scope rule of principle 13.

**Read-only MCP analytics, once the MCP server exists.** The analytics tools of `add-hub-mcp-server` call the same rollup endpoints as REST; no analytics call can write. Alternative: a separate analytics MCP server (rejected: one hub MCP endpoint is the standard for the team). This decision is recorded here and implemented there.

## Platform availability (verified 2026-11-02, source URL per item)

- TikTok: views, likes, comments and shares for public posts through the video list (`POST /v2/video/list/`, scope `video.list`); private posts are not in the public list, so their metrics are unavailable. Watch time, retention and follower delta were not found in the pages read. Source: https://developers.tiktok.com/doc/tiktok-api-v2-video-list and https://developers.tiktok.com/doc/tiktok-api-v2-video-object
- Instagram: Reel insights for views, reach, likes, comments, shares, saves, total interactions, average watch time and skip rate; data may be delayed up to 48 hours; follower delta is not among the Reel insights read. Source: https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/instagram-media/insights
- YouTube and Twitch: availability is stated in `add-youtube-insights` and `add-twitch-insights`, each verified in its own change.

## Risks / Trade-offs

- [Risk] A month's partition is missing when collection runs, and points are lost or fail. → Mitigation: the job creates the next month's partition ahead of time and a test covers the first day of a month.
- [Risk] Rollups drift from raw points after a late correction from a channel. → Mitigation: the rollup job rebuilds the affected day and week on correction; a test compares rollup and raw sums.
- [Risk] A platform's API limits or delayed figures cause gaps in collection. → Mitigation: gaps are recorded as failures per channel and are visible in the dashboard; no value is invented, and figures a platform reports late are collected again on later runs.
- [Risk] A platform reports public posts only, so private posts have no metrics. → Mitigation: those metrics are stored as unavailable and the dashboard says why.
- [Risk] Rollup job runs long on two years of partitions. → Mitigation: rollups are built per day and week, so a rebuild touches only the affected period.
- [Trade-off] Rollups lag raw points by the job interval. Accepted: analytics are for decisions made over days, not for live views.

## Open Questions

- How long raw metric points are kept. This does not change the specs (raw points are kept, and no retention is specified), so it is deferred to the operator.
