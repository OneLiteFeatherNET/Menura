# Design

## Context

TikTok is one of the four first-stage platforms (principle 13: native adapters, no aggregator). The connection itself (platform consent, minimal scopes, encrypted tokens, refresh, expiry and revocation display, the adapter port with publish, status, delete and capabilities) belongs to `add-channel-connections` and is not restated here. This change implements the TikTok adapter and the TikTok-specific rules. Posts, approval and the due-post lease are owned by `add-content-distribution`; native metrics are specified in `add-tiktok-insights`. Motivation is in proposal.md.

## Platform constraints (verified 2026-11-02, source URL per item)

Checked against TikTok's official developer documentation. The product-team starting points in the brief were confirmed except where marked **differs**.

- Direct post starts with an init call on the Content Posting API (`POST /v2/post/publish/video/init/`) and the video bytes are uploaded to the returned upload URL, which is valid for one hour. Source: https://developers.tiktok.com/doc/content-posting-api-reference-direct-post and https://developers.tiktok.com/doc/content-posting-api-reference-upload-video
- Direct post requires scope `video.publish`. Source: https://developers.tiktok.com/doc/content-posting-api-get-started
- Creator info (`POST /v2/post/publish/creator_info/query/`, scope `video.publish`) must be queried before posting; the returned privacy level options must match the value sent. Returned fields include the privacy options, whether comments, duets and stitches are disabled by the creator, and the maximum video duration in seconds. Source: https://developers.tiktok.com/doc/content-posting-api-reference-query-creator-info and https://developers.tiktok.com/doc/content-posting-api-reference-direct-post
- Privacy values are `PUBLIC_TO_EVERYONE`, `MUTUAL_FOLLOW_FRIENDS`, `FOLLOWER_OF_CREATOR` and `SELF_ONLY`. Public accounts get the first, second and last; private accounts get the second, third and last. Source: https://developers.tiktok.com/doc/content-posting-api-reference-query-creator-info
- Unaudited clients: "All content posted by unaudited clients will be restricted to private viewing mode"; lifting it requires an audit against TikTok's terms. A publish attempt is blocked with `unaudited_client_can_only_post_to_private_accounts`. Source: https://developers.tiktok.com/doc/content-posting-api-get-started and https://developers.tiktok.com/doc/content-posting-api-reference-direct-post
- Caption/title maximum 2200 UTF-16 runes. Source: https://developers.tiktok.com/doc/content-posting-api-reference-direct-post
- Inbox draft (`POST /v2/post/publish/inbox/video/init/`) requires scope `video.upload`; the creator must open the inbox notification in TikTok to complete the post. Source: https://developers.tiktok.com/doc/content-posting-api-reference-upload-video
- **Differs from the brief:** the brief named no scope. Direct post needs `video.publish`; inbox drafts need `video.upload`.
- Outcome polling: status is read with the publish identifier returned by init; status values are `PROCESSING_UPLOAD`, `PROCESSING_DOWNLOAD` (URL pull only), `SEND_TO_USER_INBOX`, `PUBLISH_COMPLETE` and `FAILED`. Failure reasons include `internal` and `video_pull_failed` (retryable), `spam_risk_too_many_posts` (daily cap reached), `spam_risk_user_banned_from_posting`, `spam_risk_text`, `spam_risk`, `auth_removed` and `publish_cancelled` (not retryable). Source: https://developers.tiktok.com/doc/content-posting-api-reference-get-video-status
- Rate limits: 6 requests per minute per user access token for the init calls; 30 requests per minute per user access token for status. Exceeding returns HTTP 429 `rate_limit_exceeded`. Sources: https://developers.tiktok.com/doc/content-posting-api-reference-upload-video, https://developers.tiktok.com/doc/content-posting-api-reference-get-video-status
- **Differs from the brief:** the brief gave no rate limits. The daily posting cap has no numeric value in the documentation; it surfaces only as `spam_risk_too_many_posts`.
- Brand disclosure toggles exist on the post options (`brand_content_toggle`, `brand_organic_toggle`). Source: https://developers.tiktok.com/doc/content-posting-api-reference-direct-post
- Not stated in the pages read: a numeric maximum file size, chunk size rules, the inbox draft expiry, and any delete endpoint. The Content Posting API reference pages read contain no delete call; the publisher removes posts in the app. Re-check before release.
- Webhooks for publish outcomes (`post.publish.failed`, `post.publish.inbox_delivered`) were seen in a search summary but not read on the official page; they are not relied on here.

## Goals / Non-Goals

**Goals:**
- No TikTok post goes out with a setting the creator's account does not allow, or as a public post while the app is unaudited.
- A publication is never repeated automatically after an outcome the platform did not confirm.

**Non-Goals:**
- No Duet, Stitch or live features, no comment or direct message handling.
- No aggregator between Menura and TikTok (principle 13).
- No change to the connection, consent or token rules, which belong to `add-channel-connections`.

## Decisions

**Creator options read immediately before each publication.** The adapter reads the creator information in the same job run that publishes, and the dry run reads it again at scheduling and before publication. The privacy and interaction options are never cached between runs because a creator can change them at any time.
- Rejected: reading the options once at connect time. A creator could change the account to private after connection, and the post would then fail at the platform.

**Direct post by default, inbox as a per-channel mode.** Direct post is the default because the calendar then reflects what is actually published. Inbox mode is available for channels where a creator wants to add effects or edit in the app before posting; it needs the `video.upload` scope.
- Rejected: inbox only. It would make every approved post wait for a creator action and would break the calendar's promise of published times.
- Rejected: one mode for all channels. Creators differ in workflow.

**Unaudited restriction enforced by the server and shown.** A channel carries an audit flag that a publisher sets after TikTok confirms the audit. While the flag is off, the server refuses non-private TikTok posts in the scheduling and dry-run paths, so the interface cannot bypass it.
- Rejected: rely on the platform rejection alone. Then a public post would fail at its scheduled time, which is the surprise the calendar exists to prevent.

**Upload from object storage with the platform's upload URL.** The publication job takes the asset from object storage and uploads it to the upload URL that TikTok issues, within its one-hour validity. The job never sends media through the server (principle 1).
- Rejected: pull from URL. The platform downloads from a URL that must be publicly reachable. That would expose a storage object or require a public proxy, which breaks principle 1.

**Outcome by polling inside the publication job.** The job polls status for the publish identifier at the rate limit, within the job run. Each attempt is recorded before the call, as in `add-content-distribution`.
- Rejected: webhooks as the primary path. They need an inbound endpoint with verification in the server, and the publication job is designed to finish its own work under a lease.

**Retry only the reasons the platform marks retryable.** Retryable: `internal`, `video_pull_failed`. Everything else (spam and ban reasons, `auth_removed`, `publish_cancelled`, format and duration checks, and the daily cap) is marked failed with the reason.
- Rejected: a blanket retry. A banned or capped account would be retried until the limit, and a re-sent upload could duplicate a live post.

**Deletion reported as unsupported.** The adapter reports delete as unavailable, and the publisher is told to delete in the app.
- Rejected: marking the post deleted in Menura only. The post would stay live on TikTok while the calendar says it is gone.

## Risks / Trade-offs

- [Risk] TikTok audit takes long or is refused, so every TikTok post stays private. → Mitigation: the restriction is visible on the channel, the other platforms are not blocked, and private posts are a supported state.
- [Risk] A creator changes privacy or disables interactions between scheduling and publication. → Mitigation: the options are read again at publication; the post reports the mismatch instead of publishing with a different setting.
- [Risk] The daily cap is reached silently by many posts from one creator. → Mitigation: the calendar shows the cap reason and no automatic retry.
- [Risk] An inbox draft is never completed by the creator. → Mitigation: the unfinished state is shown for a publisher to decide.
- [Trade-off] Polling costs requests against a 30-per-minute limit. Accepted: one publish produces few status calls, and the job spaces them.

## Migration Plan

New channel type; no existing TikTok posts. The adapter ships disabled per channel and is enabled after the first audit or with a private-only channel. Rollback disables the adapter; posts already sent to TikTok remain on TikTok and are listed in the calendar with their identifiers.

## Open Questions

- How long a creator's inbox draft waits before the post is shown as unfinished for a publisher to decide. The documentation read gives no expiry; the value is configuration, not a spec change.
- Whether the audit can be requested in the same release. This is a release-planning question and does not change the specs.
