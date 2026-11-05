# Design

## Context

Instagram is one of the four first-stage platforms (principle 13). The connection, OAuth consent, minimal scopes, encrypted tokens, refresh and revocation display, and the adapter port are owned by `add-channel-connections`; this change implements the Instagram adapter and its rules. Posts, approval, the due-post lease and the dry-run framework come from `add-content-distribution`; Reel insights are specified in `add-instagram-insights`. Motivation is in proposal.md.

## Platform constraints (verified 2026-11-02, source URL per item)

Checked against Meta's official Instagram Platform documentation. Where a brief starting point was confirmed, it is noted; **differs** marks a fact that the brief got wrong or left open.

- Publishing requires an Instagram professional account. Source: https://developers.facebook.com/docs/instagram-api/guides/content-publishing/ and https://developers.facebook.com/docs/instagram-platform/content-publishing
- Business and creator accounts both count as professional. Source: https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login (read via search summary; confirm in the release check).
- Two-step publication: create a container (`POST /<IG_ID>/media`), then publish it with `POST /<IG_ID>/media_publish` and the container identifier. Source: https://developers.facebook.com/docs/instagram-platform/content-publishing
- Container status (`status_code`): `IN_PROGRESS`, `FINISHED` (ready to publish), `PUBLISHED`, `ERROR`, `EXPIRED`. Polling is recommended once per minute for no more than five minutes; a container is not published within 24 hours of creation and then expires. Source: https://developers.facebook.com/docs/instagram-platform/content-publishing
- Reels: `media_type` set to `REELS` with `video_url` (must be publicly fetchable by Meta). Optional `cover_url` (an image, which takes precedence over `thumb_offset`), `thumb_offset` in milliseconds (default 0) and `share_to_feed`. Source: https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-user/media
- **Differs from the brief:** `video_url` must point to a publicly reachable server, so the media must be fetched from a public address, which the object-storage design must provide with a time-limited link. Reels cannot be placed in carousels.
- Reel files: MOV or MP4, no edit lists, moov atom at the front, maximum 300 MB, duration 3 seconds to 15 minutes. Source: https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-user/media
- Caption: maximum 2200 characters, 30 hashtags and 20 @ tags. Source: https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-user/media
- An account may create 400 containers within a rolling 24 hours. Source: https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-user/media
- Publishing allowance: the guide states "Instagram accounts are limited to 100 API-published posts within a 24-hour moving period" and that carousels count as one post; the limit is enforced on the publish call, and usage can be read from the content publishing limit endpoint. Source: https://developers.facebook.com/docs/instagram-api/guides/content-publishing/ and https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-user/content_publishing_limit
- **Differs from the brief and inside the sources:** the brief gave no number. The content publishing guide states 100; the endpoint's example response shows `quota_total` 50 with `quota_duration` 86400 (24 hours). A search summary of the guide also reported 50. The change therefore does not hard-code a number; it reads the allowance from the endpoint at runtime. Re-check before release.
- Scopes for publishing with Instagram Login: `instagram_business_basic` and `instagram_business_content_publish`. No Facebook Page is needed for this variant. Source: https://developers.facebook.com/docs/instagram-platform/content-publishing and https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login
- **Differs from the brief:** the brief did not say which login variant to use. Meta's Instagram API with Facebook Login requires a linked Facebook Page.
- Media deletion: Meta announced an endpoint on 2025-12-03 to delete Instagram media including Reels, and says it is available through the Instagram API with **Facebook Login**. The Instagram Login pages read do not list it. Source: https://developers.facebook.com/blog/post/2025/12/03/instragram-api-updates/ (fetched 2026-11-02; permission not named on the page).
- Trial Reels: announced in the same update; out of scope here. Source: same blog post.

## Goals / Non-Goals

**Goals:**
- No Reel is due without a readiness check, a valid caption and media, and room in the account's allowance.
- A Reel is never published twice after an outcome that was not confirmed.

**Non-Goals:**
- No feed posts, carousels, stories or trial Reels in this change.
- No aggregator between Menura and Instagram (principle 13).
- No change to connection, consent or token rules (`add-channel-connections`).

## Decisions

**Instagram Login variant for publishing.** The adapter uses the Instagram Login variant: no linked Facebook Page is needed, and the publish and container calls are documented for it. This keeps the connection simple for the team's accounts.
- Rejected: the Facebook Login variant. It requires a Page per account and is the only variant with the deletion endpoint; the team does not need Page management, and deletion can be done in the app. Revisit if the product owner wants deletion from Menura (see Open Questions).

**Allowance read from the platform, never hard-coded.** The planner and the dry run read the account's publishing allowance from the platform and keep the server's stored copy only as a cache for display. The planner schedules at most the remaining allowance per rolling 24 hours.
- Rejected: a fixed number in configuration. The sources disagree (100 in the guide, 50 in the endpoint example), and the platform may change it.

**Container created when the Reel is due, not at scheduling.** Containers expire 24 hours after creation and count against the 400-per-day container limit, so they are created in the publication job run at the due time.
- Rejected: creating the container at scheduling. Scheduled posts of days ahead would expire before publication and waste the container allowance.

**Readiness by bounded status polling.** The job polls the container status once per minute for at most five minutes, as Meta recommends; a status still in progress after that is an unknown outcome if publication had been called, and otherwise the container is recreated.
- Rejected: webhooks for container status. They need an inbound endpoint; the job already holds a lease and can poll within it.

**Retry only before publication.** Preparation errors and expiry before publication are retried with a new container. Once a publish call was made without a confirmed outcome, no automatic retry happens, because a Reel may already be live.
- Rejected: blanket retry after any failure. A publish call that succeeded but was not acknowledged would then publish a second Reel.

**Cover from an image, with frame as fallback.** The asset's cover image is the cover (`cover_url` overrides `thumb_offset`), and the frame offset is used when there is none.
- Rejected: always use the first frame. The cover is the first thing a viewer sees, and the asset already carries a designed cover from the brand kit.

**Deletion reported as unavailable.** Deletion is reported as unsupported for the Instagram Login variant, and the publisher removes the Reel in Instagram.
- Rejected: marking deleted locally. The Reel would remain live.

## Risks / Trade-offs

- [Risk] The platform's allowance differs from the documented number. → Mitigation: the allowance is read at runtime and shown on the channel.
- [Risk] Media not reachable from the platform's servers at the time of preparation. → Mitigation: the link is issued from object storage at preparation time, and the preparation failure is shown with its reason.
- [Risk] Reels in preparation exceed the five-minute polling window under load. → Mitigation: the outcome is shown as pending, and the recorded attempt allows a person to decide after it expires.
- [Trade-off] No deletion from Menura with the chosen login variant. Accepted until the product owner decides otherwise.

## Migration Plan

New channel type; no existing Instagram posts. The adapter ships disabled per channel and is enabled per account after the account is connected as professional. Rollback disables the adapter; Reels already published stay on Instagram and are listed with their identifiers.

## Open Questions

- Whether deletion from Menura is wanted badly enough to move to the Facebook Login variant (Page linkage, deletion endpoint). This is a product decision that would change the connection rules of `add-channel-connections`, so it is deferred to the product owner.
- The exact allowance value: the platform reports it at runtime, so the specs stay unchanged whichever number Meta confirms.
