# Proposal

Conventional Commits: `feat(tiktok)` (code PR). Phase: 2 (Planning & Scheduling). Depends on: `add-channel-connections`, `add-content-distribution`, `add-asset-upload`, `add-tiktok-insights`.

Strand: content.

## Why

TikTok is a first-stage channel. Its rules differ from every other platform: the creator's privacy and interaction options are set per account and must be read before each post, and an unaudited app can only post privately. Without a native adapter, these rules are applied by hand, and a public post that TikTok would refuse, or a post that silently ends up private, is discovered after its scheduled time.

## What Changes

- Add a TikTok adapter behind the adapter port of `add-channel-connections`: publish, status and capabilities.
- Publish as a direct post, or as a draft in the creator's TikTok inbox where the channel is set to inbox mode. In inbox mode the creator completes the post in the TikTok app.
- Before every post, query the creator information of the connected account and use only the privacy and interaction options it returns. Options the creator cannot use are not offered.
- While the TikTok app is unaudited, every TikTok post is private. The restriction is shown on the channel and the schedule dry run refuses a public TikTok post.
- Check the video length against the creator's maximum and the title length against the platform limit in the dry run.
- Record the platform outcome per attempt. Retry only the failure reasons that are safe to retry; show every other failure with its reason.
- Report deletion as unsupported for TikTok; the publisher removes a post in the TikTok app.
- **Non-Goals:** no Duet or Stitch creation, no comment or message handling, no aggregator between Menura and TikTok.
- **Later candidate only:** fully public posting after the app passes the platform audit (a configuration change once audited, no code change in this change).

## Capabilities

### New Capabilities
- `tiktok-integration`: TikTok publishing rules (creator check, privacy and interaction options, unaudited restriction, direct and inbox modes), publication outcomes and retry classes, length and title checks, and the capability report of the adapter.

### Modified Capabilities
- None.

## Impact

- Server and jobs: a TikTok adapter implementing the adapter port; the publication job calls it.
- Platform setup: a TikTok developer app with the Login Kit and Content Posting API products and the video publish and video upload permissions (scopes are verified in `design.md`).
- Web: channel view shows the unaudited restriction and the inbox state; the calendar shows the dry-run results.
- Assets: publishes finished assets recorded by `add-asset-upload`; each published post is recorded as an external post (`add-external-post-import`).
