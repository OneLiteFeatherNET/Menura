# Design

## Context

YouTube channels are connected through `add-channel-connections`, which defines the consent, token and adapter port. Imports and removal tracking are defined in `add-external-post-import`; metric points in `add-content-analytics`. This change adds the read side of the YouTube adapter only. Requirements are in `specs/youtube-insights`; motivation is in proposal.md. Principle 13 applies: the official YouTube APIs, one adapter, minimal scopes. Principle 1 applies: references and metrics only, no media.

## Goals / Non-Goals

**Goals:**
- Every existing YouTube video and Short of a connected channel appears as an external post with its format.
- Native statistics reach content analytics with absent metrics marked unavailable.
- The consent for this feature asks for read access only.

**Non-Goals:**
- No upload, edit, delete, playlist, thumbnail or scheduling calls (phase 1, `add-youtube-integration`).
- No download of video files, no copy of media into Menura's storage.
- No scraping of YouTube Studio or public pages, and no third-party analytics service (principle 13).
- No live stream control and no comment moderation.

## Decisions

**Listing through the official Data API read operations.** The adapter's listing operation reads the channel's own uploads with the YouTube Data API. The exact read operation and its fields are recorded in the Platform constraints below from the official reference, before the implementation (task 1.1). Rejected: public feeds or page scraping. They carry no statistics for the channel owner, and they are not the official API that principle 13 requires.

**Import scope: public and unlisted videos.** The import records public and unlisted videos of the connected channel. Private videos are excluded, so the archive shows what viewers can reach. Whether unlisted videos belong in the archive is an open question for the product owner; the rule is one line in the adapter and can change without a spec change.

**Format from the platform's data.** A video is recorded as Short only when the platform's data marks it so; otherwise it is recorded as video. The Shorts classification rule is not yet verified (Platform constraints), so task 2.1 pins it to the verified rule before the format is trusted.

**Statistics split between two read APIs.** Views, likes and comments per video come from the statistics the Data API returns for each video. Watch time and other analytics metrics come from the YouTube Analytics API, whose metric names and scope are not yet verified. Rejected: Data API statistics only. They do not provide watch time, which the analytics model needs.

**Metric availability declared, not assumed.** Each metric the adapter returns is recorded with the platform's availability; a metric the channel's API does not return is stored as unavailable and not as zero (`add-content-analytics`).

## Platform constraints (verified 2026-11-02)

Read-side facts copied from `openspec/changes/add-youtube-integration/design.md`, which holds the full verification. Upload, visibility and scheduling facts there are write-side and are not used here.

- **Quota per project:** default 10,000 units per day for endpoints without their own allocation; quota resets at midnight Pacific Time. Source: https://developers.google.com/youtube/v3/determine_quota_cost (checked 2026-11-02).
- **Read method cost:** the videos resource page states "A call to this method has a quota cost of 1 unit" for `videos.list`. Source: https://developers.google.com/youtube/v3/docs/videos (checked 2026-11-02).
- **Audit and quota extension:** more quota requires an audit through the "YouTube API Services - Audit and Quota Extension Form"; a completed compliance audit allows an extension request within 12 months. Source: https://developers.google.com/youtube/v3/guides/quota_and_compliance_audits (checked 2026-11-02).
- **Open (not verified in this round):** the YouTube Analytics metric names and the scope for the Analytics API; the Shorts duration and format rules; the read scope for listing a channel's videos (the integration design records only upload scopes). These are verified in task 1.1 before the specs are implemented; none of them changes a requirement.

## Risks / Trade-offs

- [Risk] The quota of reads competes with uploads in phase 1 on the same project. → Mitigation: reads are counted in the connection's quota state shown by `add-channel-connections`; the re-sync interval is configurable.
- [Risk] The Shorts rule differs from the assumption. → Mitigation: format is set from the verified rule, and the test in task 2.1 fails if the classification changes.
- [Risk] Analytics figures arrive late. → Mitigation: late figures are recorded as unavailable and collected again on later runs, as in `add-content-analytics`.
- [Trade-off] Two read APIs mean two sets of permissions to verify. Accepted: each is read-only, and watch time cannot be had otherwise.

## Open Questions

- Whether unlisted videos are imported (currently yes, see Decisions).
- The Analytics API scope and metric names, to be fixed in task 1.1.
