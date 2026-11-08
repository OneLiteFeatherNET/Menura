# Proposal

Conventional Commits: `feat(tiktok)` (code PR). Phase: 2 (Platforms & Join Attribution). Depends on: `add-channel-connections`, `add-external-post-import`, `add-content-analytics`.

Strand: platform.

## Why

The team already posts to TikTok, and the videos they made there are not in the hub. Without an import, the analytics cannot show how the existing videos performed, and any later comparison with Menura-published posts would start from nothing. TikTok reports views, likes, comments and shares per video, so the numbers can be collected from the first run.

## What Changes

- Import the public videos of a connected TikTok account as external posts, with platform, video identifier, URL, title, publish time and format, through the video list read operation of the TikTok API.
- Collect views, likes, comments and shares into content analytics; a metric TikTok does not report for a post is recorded as unavailable, not as zero.
- Request only the read scopes for the video list and the account's basic information. No publish, upload or draft scope is requested by this change.
- State in the channel view that the video list covers public videos only, so private videos are not imported.
- Not in this change: publishing, direct post, inbox drafts and creator checks (`add-tiktok-integration`, phase 2).

## Capabilities

### New Capabilities
- `tiktok-insights`: read-only import of public TikTok videos as external posts, native metrics into content analytics, and the read-only permission set.

### Modified Capabilities
- None.

## Impact

- Server: a read-only TikTok client and the listing and metrics operations of the TikTok adapter.
- Jobs: the re-sync and collection jobs run for TikTok channels.
- Web: the channel view states the public-only coverage of the import.
- Platform changes: `add-tiktok-integration` (phase 2) extends the same adapter with its publishing operations and scopes, requested in its own consent.
