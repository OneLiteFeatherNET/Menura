# Design

## Context

YouTube is the first platform adapter. It plugs into the connection layer and the port from `add-channel-connections` (connect, refresh, capabilities, publish, status, delete, metrics), Its statistics are specified in `add-youtube-insights`. Publication runs in the `jobs` CronJob for approved posts (principle 4 and the distribution design). See proposal.md - Why for motivation.

Principle 13 applies: the official YouTube Data API, no aggregator, minimal scopes, quotas and audits visible.

## Platform constraints (verified 2026-11-02)

Verified against the official documentation on 2026-11-02. Where the documentation was ambiguous or contradicted the product team's starting point, this is stated. Items not checked are listed as open.

- **Quota per project:** default 10,000 units per day for endpoints without their own allocation; quota resets at midnight Pacific Time. Source: https://developers.google.com/youtube/v3/determine_quota_cost (checked 2026-11-02).
- **videos.insert allocation:** the documentation states a default allocation of 100 `videos.insert` calls and 100 `search.list` calls per day, each with its own bucket. Source: https://developers.google.com/youtube/v3/determine_quota_cost (checked 2026-11-02).
- **videos.insert unit cost: UNRESOLVED.** Two reads of the same page gave different answers: one reported "Each call costs 1 quota" for the insert row, and a search result summary of the newer page reported 1600 units per upload. The product team's starting point ("an upload costs much of it") fits the second value. The design therefore does not hard-code a cost. Quota costs are a configuration table per method, and the value for `videos.insert` must be read from the Google Cloud Console quota page before the quota check is enabled. Recorded for follow-up, not decided here.
- **Other method costs:** one reading of the quota page gave 50 units for `thumbnails.set`, `playlists.insert`, `playlistItems.insert` and `videos.update`, and 1 unit for `videos.list` (the videos resource page states "A call to this method has a quota cost of 1 unit"). The 50-unit values were not confirmed by a second read and are configuration values until then.
- **Audit and quota extension:** more quota requires an audit through the "YouTube API Services - Audit and Quota Extension Form"; a completed compliance audit allows an extension request within 12 months. Source: https://developers.google.com/youtube/v3/guides/quota_and_compliance_audits (checked 2026-11-02).
- **Unverified projects and visibility: CONTRADICTION.** The videos resource documentation states "Videos uploaded from unverified API projects are not restricted to private viewing mode." Source: https://developers.google.com/youtube/v3/docs/videos (checked 2026-11-02). The product team's starting point, and older third-party and forum sources, say that uploads from unaudited projects are forced to private. The page is the official source, so the design follows it, but it does not rely on it: the applied visibility is read back after every upload (see spec "Applied visibility read back"), and the audit status shown on the channel is recorded by an administrator from the platform's own response, not assumed.
- **Resumable upload:** the official upload guide uses resumable uploads with exponential backoff and resumption after failure. Source: https://developers.google.com/youtube/v3/guides/uploading_a_video (checked 2026-11-02).
- **Upload size:** the videos.insert documentation gives a maximum file size of 256 GB. Source: https://developers.google.com/youtube/v3/docs/videos/insert (checked 2026-11-02).
- **Scopes:** videos.insert lists the scopes `youtube.upload`, `youtube`, `youtubepartner` and `youtube.force-ssl`. Source: https://developers.google.com/youtube/v3/docs/videos/insert (checked 2026-11-02). The scope needed for playlists, thumbnails and read-back is not stated on that page and is an open item.
- **Made-for-kids and privacy fields:** `status.selfDeclaredMadeForKids` and `status.privacyStatus` (`public`, `private`, `unlisted`) are settable in `status`; `privacyStatus` defaults to `public` when not given, although creator account settings can change the default. Source: https://developers.google.com/youtube/v3/docs/videos/insert and https://developers.google.com/youtube/v3/docs/videos (checked 2026-11-02).
- **Scheduled publication:** `status.publishAt` is a settable property. The documentation read did not state how it combines with `privacyStatus`; the design assumes the video is uploaded as private with a publish time, which must be confirmed in a test upload before the scheduling spec is implemented.

**Open (not verified in this round):** Shorts duration and format rules; the custom thumbnail eligibility rule; playlist scopes. These are checked during the implementation tasks, and the specs were written so that none of them changes a requirement.

## Goals / Non-Goals

**Goals:**
- Publish a finished asset at its planned time without a person clicking in YouTube Studio, after a person approved it.
- Show what the platform actually did (visibility, thumbnail, quota) rather than what was requested.

**Non-Goals:**
- No live streaming control, no comment moderation, no YouTube Studio automation.
- No use of YouTube as an overlay or live-data source (live overlay data is outside this round).
- No upload of videos that are not finished assets recorded by `add-asset-upload`.

## Decisions

**Resumable upload from storage, not through the server.** The publication job streams the asset from object storage to the platform in resumable chunks, storing the session location on the post. The server never proxies bytes (principle 1).
- Rejected: a single request upload. An interrupted large file would start over and could publish twice.

**Quota costs as configuration, checked before publication.** Each method's unit cost lives in a configuration table; the dry run estimates the day's cost from it. The `videos.insert` value is confirmed in the Cloud Console before the check is enabled.
- Rejected: hard-coded costs in the adapter. The documented numbers have already been inconsistent, and a change in the platform's numbers should not need a release.
- Rejected: ignoring quota until a publish fails. A failed upload late in the day wastes the quota it used.

**Scheduled publication by platform publish time.** The post is uploaded as private with the publish time set to the scheduled time, so the platform makes it public at the planned time even if the job runs late; the job uploads ahead of that time.
- Rejected: uploading at the scheduled time and then flipping visibility. A late job would publish later than planned and would need a second call that can fail.
- Assumption: uploads for scheduled publication are private until the publish time; confirmed by a test upload (see Open items).

**Read-back of the applied visibility.** After upload the adapter reads the video's visibility and the thumbnail state back and stores them on the post. A difference is a publication problem, not a silent success.
- Rejected: trusting the upload response. It does not tell whether the platform applied a restriction.

## Risks / Trade-offs

- [Risk] The `videos.insert` unit cost is higher than the planned daily budget → Mitigation: the dry run estimates the cost before scheduling and warns; the configuration table is updated from the Console value.
- [Risk] The platform restricts visibility for an unaudited app, contrary to the current documentation → Mitigation: read-back shows the problem on the post; the publisher sees it before the audit result changes anything.
- [Risk] Scheduled publication behaves differently than assumed → Mitigation: a test upload is a task before the scheduling feature is enabled; a failing test blocks the feature.
- [Risk] Upload succeeds but the response is lost → Mitigation: the job reads the upload status by the recorded session before any retry, so a second upload does not create a duplicate (see distribution design on ambiguous outcomes).
- [Trade-off] The scheduled-time model depends on platform publish time handling; if a platform change breaks it, posts stay private until a person publishes them manually, which is the safe failure.

## Migration Plan

No existing YouTube publications are migrated. The adapter is enabled per channel after its connection is in place and its audit status is recorded. Rollback disables the adapter per channel; uploaded videos stay on the platform and their records stay in Menura. Suspending the publication CronJob stops all scheduled publications.

## Open Questions

- Exact `videos.insert` unit cost and the other method costs: confirmed in the Cloud Console and a test project before enabling the quota check (task in tasks.md). This does not change the specs.
- Minimal scope set for playlists, thumbnails and read-back: confirmed against the method reference before the consent screen is built. The specs already require minimal scopes; only the list changes.
