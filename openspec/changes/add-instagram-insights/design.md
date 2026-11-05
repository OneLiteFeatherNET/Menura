# Design

## Context

Instagram accounts are connected through `add-channel-connections`, which defines consent, tokens and the adapter port. Imports, re-sync and removal tracking come from `add-external-post-import`; insights are stored as metric points by `add-content-analytics`. This change adds the read side of the Instagram adapter only. Requirements are in `specs/instagram-insights`; motivation is in proposal.md. Principle 13 applies: Meta's official Instagram Platform API, one adapter, minimal permissions. Principle 1 applies: references and metrics, no media.

## Goals / Non-Goals

**Goals:**
- Every existing Reel of a connected account appears as an external post with its link and publish time.
- Reel insights reach content analytics, with figures not yet reported recorded as unavailable and collected again.
- The consent asks for the basic account and insights permissions only.

**Non-Goals:**
- No publishing, container creation, publishing allowance check or deletion (phase 2, `add-instagram-integration`).
- No import of feed posts, carousels or stories in this change; only Reels.
- No download of video files and no copy of media.
- No scraping and no third-party analytics service (principle 13).

## Decisions

**Instagram Login variant for reading.** The read operations use the Instagram API with Instagram Login, with the permissions `instagram_business_basic` and `instagram_business_manage_insights`. It needs no linked Facebook Page. Rejected: the Facebook Login variant. It requires a linked Facebook Page (Platform constraints), and the Instagram Login variant needs none.

**Reels only.** The import takes Reels, which are the format the team publishes. Other media types are left out so the archive and the insights are comparable. Rejected: importing all media. The insights for feed posts and stories differ, and mixing them in the same analytics would distort comparisons.

**Caption as the title.** Instagram has no separate title for a Reel; the caption's text, as the platform reports it, is stored as the title.

**Insights are read again for delayed figures.** Figures for a Reel published less than 48 hours ago may not be reported yet. Those metrics are recorded as unavailable, and the collection reads them again on later runs, so a late figure is never stored as zero. Rejected: waiting 48 hours before the first import. The Reel would then be missing from the archive for two days.

**Metric availability as reported.** Each insight the platform reports is stored with its name; an insight the account's API response omits is recorded as unavailable.

## Platform constraints (verified 2026-11-02)

Read-side facts copied from `openspec/changes/add-instagram-integration/design.md`, which holds the full verification. Publishing, container, allowance and media-deletion facts there are write-side and are not used here.

- **Insights for Reels:** `views`, `reach`, `likes`, `comments`, `shares`, `saved`, `total_interactions`, `ig_reels_avg_watch_time`, `ig_reels_video_view_total_time`, `reels_skip_rate` and others. Permissions: `instagram_business_basic` and `instagram_business_manage_insights` for the Instagram Login variant. Data may be delayed up to 48 hours. Source: https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/instagram-media/insights (checked 2026-11-02).
- **Account type:** the integration design states that publishing requires an Instagram professional account and that business and creator accounts both count as professional. Source: https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login (read via search summary; confirm in the release check). The insights read is assumed to need the same account type; confirm in task 1.1.
- **Login variant:** the integration design states that Meta's Instagram API with Facebook Login requires a linked Facebook Page. The integration design gives no source URL for this item.
- **Open (not verified in this round):** the endpoint that lists an account's media and its paging, which the integration design does not record; to be read from the reference in task 1.1.

## Risks / Trade-offs

- [Risk] Figures delayed by up to 48 hours are read as zero. → Mitigation: unreported figures are unavailable, and collection runs again for Reels within the delay window.
- [Risk] The media listing returns non-Reel media. → Mitigation: the listing keeps Reels only, tested with a feed post in the fixture.
- [Risk] Insight names differ between the two login variants. → Mitigation: the Instagram Login variant only is implemented; the names are checked against the reference in task 1.1.
- [Trade-off] Feed posts and stories are not in the archive. Accepted: the first stage covers Reels only.

## Open Questions

- Whether the insights read needs a professional account type beyond what publishing needs; to be confirmed in task 1.1.
