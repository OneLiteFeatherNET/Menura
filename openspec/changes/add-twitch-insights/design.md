# Design

## Context

Twitch channels are connected through `add-channel-connections`, which defines consent, tokens and the adapter port. Imports, re-sync and removal tracking come from `add-external-post-import`; view counts are metric points in `add-content-analytics`. This change adds the read side of the Twitch adapter only. Requirements are in `specs/twitch-insights`; motivation is in proposal.md. Principle 13 applies: Twitch's official Helix API, one adapter, minimal scopes. Principle 1 applies: references and metrics, no media.

## Goals / Non-Goals

**Goals:**
- Every VOD, highlight and clip of a connected channel appears as an external post before Twitch deletes it.
- Clips are linked to their broadcasts when the broadcast is known.
- View counts reach content analytics with their source and collection time.

**Non-Goals:**
- No VOD download and no copy of media into Menura's storage. Twitch's API offers no download endpoint (see Platform constraints).
- No clip creation, title or category change, or schedule change (phase 2, `add-twitch-integration`).
- No chat, moderation, subscription or payment data.

## Decisions

**Reference, never copy.** An imported VOD, highlight or clip is an external post that holds the broadcast metadata and the playback link. Rejected: downloading the VOD to Menura's storage. The Twitch API has no download endpoint for it, and forum threads say undocumented download methods conflict with the Developer Services Agreement (Platform constraints).

**Re-sync interval shorter than the VOD retention window.** A normal broadcaster's VOD is deleted after 14 days, so the re-sync must run more often than that to see every broadcast while it still exists. Rejected: a Twitch EventSub or webhook subscription for new VODs. It adds a second ingest path, and the periodic re-sync of `add-external-post-import` already covers the window if its interval is set below 14 days.

**Unique key by Twitch identifier.** The identifier of each VOD, highlight and clip is the external identifier of the external post, so a repeated import creates no second record for the same broadcast.

**Clip linked to a known broadcast.** A clip is linked to its broadcast external post when the clip's broadcast is in the hub; otherwise it is imported unlinked and the link can be added by a later re-sync.

**Removal follows Twitch's report.** Items that Twitch reports as deleted or unavailable are marked removed as specified in `add-external-post-import`; the record and the view counts stay.

**View counts as metric points with their source.** View counts are recorded as metric points marked as provided by Twitch and collected at a known time. A missing count is stored as unavailable.

## Platform constraints (verified 2026-11-02)

Read-side facts copied from `openspec/changes/add-twitch-integration/design.md`, which holds the full verification. Clip creation, channel update, schedule and upload facts there are write-side and are not used here.

- **No VOD download:** the videos documentation lists Get Videos and Delete Videos only, with no download endpoint. Source: https://dev.twitch.tv/docs/api/videos/ (checked 2026-11-02). Developer forum threads describe undocumented download methods and say they conflict with the Developer Services Agreement; the agreement text itself was not read in full in this round.
- **VOD retention:** Twitch deletes VODs after 14 days for normal broadcasters and 60 days for others such as partners; highlights and uploads do not expire. Source: https://dev.twitch.tv/docs/api/videos/ (checked 2026-11-02).
- **Video fields:** the Get Videos example response includes `view_count`; the page does not state a deprecation. Source: https://dev.twitch.tv/docs/api/videos/ (checked 2026-11-02). The reference's deprecation notices were not found in the portions read; recheck before release.
- **Clip views:** Get Clips returns `view_count` per clip. Source: https://dev.twitch.tv/docs/api/clips/ (checked 2026-11-02).
- **Token lifetime and refresh:** access tokens carry an `expires_in` value; a refresh response includes a new refresh token that must replace the stored one; refresh tokens of public clients expire after 30 days; a refresh that returns 401 requires a new consent. Source: https://dev.twitch.tv/docs/authentication/refresh-tokens/ (checked 2026-11-02).
- **Rate limits:** requests are counted in separate buckets for app and user tokens, per client ID and per user for user tokens, with a default cost of one point per request. The numeric per-minute limit was not stated on the page read; the design reads the platform's limit headers rather than a constant. Source: https://dev.twitch.tv/docs/api/guide/ (checked 2026-11-02).
- **Breaking changes:** Twitch announces breaking changes in its developer forum before they take effect. Source: https://dev.twitch.tv/docs/api/guide/ (checked 2026-11-02).
- **Open (not verified in this round):** the numeric rate limit; the full Developer Services Agreement wording on downloads, to be reviewed legally before any media feature is considered.

## Risks / Trade-offs

- [Risk] A VOD is deleted before the next re-sync. → Mitigation: the re-sync interval is set below the 14-day retention window. A VOD deleted before the first run never reaches the archive; the operator guide states this gap.
- [Risk] Refresh token expires after 30 days for a public client while nobody is looking. → Mitigation: the connection health check of `add-channel-connections` shows the expiry ahead of time.
- [Risk] Twitch changes a field or deprecates `view_count`. → Mitigation: the field is read from the reference before release (task 1.1), and a missing field is recorded as unavailable.
- [Trade-off] No event subscription for new VODs. Accepted: the periodic re-sync is simpler and covers the window.

## Open Questions

- Numeric rate limit and the Developer Services Agreement wording on downloads: recorded for follow-up, not decided here.
