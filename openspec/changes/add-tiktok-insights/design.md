# Design

## Context

TikTok accounts are connected through `add-channel-connections`, which defines consent, tokens and the adapter port. Imports, re-sync and removal tracking come from `add-external-post-import`; metrics are stored by `add-content-analytics`. This change adds the read side of the TikTok adapter only. Requirements are in `specs/tiktok-insights`; motivation is in proposal.md. Principle 13 applies: TikTok's official API, one adapter, minimal scopes. Principle 1 applies: references and metrics, no media.

## Goals / Non-Goals

**Goals:**
- Every public TikTok video of a connected account appears as an external post with its format and link.
- Views, likes, comments and shares reach content analytics, with absent values marked unavailable.
- The consent asks for read scopes only.

**Non-Goals:**
- No publishing, direct post, inbox draft or creator check (phase 2, `add-tiktok-integration`).
- No download of video files and no copy of media.
- No import of private videos: the official video list does not return them.
- No scraping of TikTok web pages and no third-party analytics service (principle 13).

## Decisions

**The official video list as the only listing.** The adapter's listing operation uses the video list read operation of TikTok's API, which returns the creator's public videos. Rejected: scraping profile pages. It is not an official interface and would break principle 13.

**Public videos only, stated in the channel view.** Private posts are not in the public list, so the import cannot record them. The channel view says so, so a missing private video is not mistaken for a sync error. Rejected: requesting a broader scope to see private posts. No such read is documented in the pages read, and a broader consent would contradict the minimal-scope rule.

**Metrics from the same record.** The video list's counts for views, likes, comments and shares are read as the metric operation for each public video. A count the API does not return is recorded as unavailable. Watch time, retention and follower delta were not found in the pages read, so they are unavailable for TikTok in this change.

**Read scopes only.** The feature requests the video list scope together with the basic account information scope. Publishing and draft scopes belong to `add-tiktok-integration` and are requested only when that feature is enabled.

## Platform constraints (verified 2026-11-02)

Read-side facts copied from `openspec/changes/add-tiktok-integration/design.md`, which holds the full verification. Publishing, init, creator-check, privacy and audit facts there are write-side and are not used here.

- Metrics: the video list (`POST /v2/video/list/`, scope `video.list`, page size up to 20) returns the creator's **public** videos. The video object carries `view_count` (int64), `like_count`, `comment_count` and `share_count` (int32). Sources: https://developers.tiktok.com/doc/tiktok-api-v2-video-list and https://developers.tiktok.com/doc/tiktok-api-v2-video-object (checked 2026-11-02).
- Scope `video.list` (with `user.info.basic`) is required for the video list. Stated in the integration design's metrics item, which cites the same two pages; not separately re-read.
- Private posts are not in the public video list, so their metrics are not available through it. Same source.
- Open (not verified in this round): the rate limit of the video list endpoint was not stated in the pages read; the adapter reads the platform's error response (`rate_limit_exceeded`, HTTP 429) rather than assuming a number. Recheck before release.

## Risks / Trade-offs

- [Risk] A private post is taken for a missing post. → Mitigation: the channel view states the public-only coverage, and no private post is recorded as removed.
- [Risk] TikTok changes the video list fields. → Mitigation: a missing field is recorded as unavailable, and the field names are checked against the reference in task 1.1.
- [Risk] Rate limits are lower than the re-sync load. → Mitigation: the re-sync interval is configurable and rate-limited responses are recorded per channel.
- [Trade-off] Private videos are invisible to the hub. Accepted: the official read operation does not provide them.

## Open Questions

- The video list rate limit, to be read from the reference or the error response in production.
- Whether the watch-time or retention figures become available from TikTok later; if so, a new metric is added without a spec change.
