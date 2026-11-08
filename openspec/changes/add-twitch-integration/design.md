# Design

## Context

Twitch is the second platform adapter. It uses the connection layer and port from `add-channel-connections`. Imports of broadcasts, highlights and clips, and their view counts, are specified in `add-twitch-insights`. The streams themselves are planned in Menura and recorded by the OBS plugin on the streaming PC (`add-recording-ingest`, `add-obs-plugin`). See proposal.md - Why for motivation.

Principle 13 applies: Twitch's official API only, minimal scopes, quotas and limits visible. Principle 4 applies to changes that are visible to viewers: title, category and schedule changes come from an approved plan or a publisher's request, and the AI client never changes them directly.

## Platform constraints (verified 2026-11-02)

Verified against the official Twitch developer documentation on 2026-11-02. Items that were not verified are listed as open.

- **No video upload:** the Helix API reference lists no endpoint that uploads video; the only video write endpoint shown is deletion of videos. Source: https://dev.twitch.tv/docs/api/reference/ (checked 2026-11-02). This confirms the Non-Goal in the proposal.
- **No VOD download:** the videos documentation lists Get Videos and Delete Videos only, with no download endpoint. Source: https://dev.twitch.tv/docs/api/videos/ (checked 2026-11-02). Developer forum threads state that undocumented download methods conflict with the Developer Services Agreement; the agreement text itself was not read in full in this round. Consequence: Menura references broadcasts and does not copy media from Twitch.
- **Clip creation:** creating a clip requires a user token with the `clips:edit` scope; the broadcaster must be streaming and clips must be enabled; creation is asynchronous and the clip is looked up afterwards, and if it is not visible after 15 seconds the attempt is treated as failed. Source: https://dev.twitch.tv/docs/api/clips/ (checked 2026-11-02).
- **Channel information:** changing the stream title and category uses the channel update endpoint with the `channel:manage:broadcast` scope. Source: https://dev.twitch.tv/docs/api/reference/ (checked 2026-11-02). The category is set through the game identifier field, which was not re-read on the field-level page in this round; see open items.
- **Stream schedule:** reading the schedule needs an app or user token; creating, updating and deleting segments needs a user token with `channel:manage:schedule`; only partners and affiliates may create non-recurring segments; a recurring segment's duration can change but its start date cannot. Source: https://dev.twitch.tv/docs/api/schedule/ (checked 2026-11-02).
- **Token lifetime and refresh:** access tokens carry an `expires_in` value; a refresh response includes a new refresh token that must replace the stored one; refresh tokens of public clients expire after 30 days; a refresh that returns 401 requires a new consent. Source: https://dev.twitch.tv/docs/authentication/refresh-tokens/ (checked 2026-11-02).
- **Rate limits:** requests are counted in separate buckets for app and user tokens, per client ID and per user for user tokens, with a default cost of one point per request. The numeric per-minute limit was not stated on the page read; the design therefore reads the platform's limit headers rather than a constant. Source: https://dev.twitch.tv/docs/api/guide/ (checked 2026-11-02).
- **Breaking changes:** Twitch announces breaking changes in its developer forum before they take effect. Source: https://dev.twitch.tv/docs/api/guide/ (checked 2026-11-02).

**Open (not verified in this round):** the exact category field name and its lookup by name; whether the schedule's iCalendar endpoint is needed (not used here); the numeric rate limit; the full Developer Services Agreement wording on downloads (legal review before any media feature is considered, not part of this change).

## Goals / Non-Goals

**Goals:**
- No second copy of Twitch media in Menura; the broadcast is referenced with its lineage to the local recording.
- The Twitch channel reflects the approved plan (title, category, schedule) without someone retyping it.
- Clips from live broadcasts are linked to the stream they came from.

**Non-Goals:**
- No video upload or publishing to Twitch (no endpoint exists).
- No download of VOD media from Twitch.
- No live chat, EventSub follows or chat data as overlay sources; live overlay data from the platform is outside this round.
- No moderation or subscription management.

## Decisions

**No media copied from Twitch.** Menura stores only the local recording from the OBS plugin as media. Imported broadcasts are references (see `add-twitch-insights`).
- Rejected: downloading VODs to object storage. There is no official download endpoint, the terms conflict with it, and it duplicates the recording.

**Clip creation only while live; result confirmed by lookup.** The clip request is made on behalf of the connected channel while it is live; the system then looks the clip up and stores it only when the platform confirms it, because the platform's creation is asynchronous.
- Rejected: storing the identifier from the creation response alone. A clip that does not appear would look created.

**Title and category applied at planned start, on request otherwise.** The planned stream is the source; the change happens when the planned stream starts or when a publisher asks. Changes made by an AI client are proposals that a publisher accepts.
- Rejected: continuous sync of the title. It would overwrite a title a streamer changed on purpose during the stream.

**Schedule mirrored one way.** Menura's plan is pushed to the Twitch schedule; edits on the Twitch side are not pulled back, and a conflicting segment is reported.
- Rejected: two-way sync. Two editors on two systems would produce silent overwrites.

**Schedule only where the platform allows it.** Non-recurring segments are created only for channels whose platform type allows them; other channels are reported, not approximated with recurring segments.
- Rejected: silently creating recurring segments as a workaround. It publishes a plan the streamer did not make.

## Risks / Trade-offs

- [Risk] Refresh token expires after 30 days for public-type clients → Mitigation: the connection layer shows the reconnect state before the grant expires and the health check refreshes regularly.
- [Risk] Title or category change is rejected while the stream is live → Mitigation: the rejection reason is shown and the old values stay.

## Migration Plan

No existing Twitch data. The adapter is enabled per channel after its connection is in place. Rollback disables the adapter; imported items stay as specified in `add-twitch-insights`, and schedule segments already created remain on Twitch until removed by a person or by a cancellation.

## Open Questions

- Category field name and lookup: confirmed against the field reference when the title and category feature is implemented (tasks). This does not change the specs.
- Numeric rate limit: read from the response headers at runtime; no design change.
