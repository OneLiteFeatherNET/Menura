# Proposal

Conventional Commits: `feat(instagram)` (code PR). Phase: 2 (Planning & Scheduling). Depends on: `add-channel-connections`, `add-external-post-import`, `add-content-analytics`.

Strand: platform.

## Why

Reels are the team's main short-form format on Instagram, and the existing Reels are not in the hub. Without an import, the analytics cannot show how they performed, and a later comparison with Menura-published Reels would start from nothing. Instagram reports reach, views, saves and watch time per Reel, which is the performance evidence the recycling decisions need.

## What Changes

- Import the existing Reels of a connected Instagram professional account as external posts, with platform, media identifier, URL, caption as title, publish time and format (Reel), through the Instagram Graph API read operations. Other media types are not imported by this change.
- Collect the Reel insights (views, reach, likes, comments, shares, saves, total interactions and watch time where reported) into content analytics. Figures Instagram has not yet reported are recorded as unavailable and collected again on a later run.
- Request only the read permissions for basic account access and insights. The publishing permission is not requested by this change.
- Not in this change: publishing Reels, containers, publishing allowance checks and deletion (`add-instagram-integration`, phase 2).

## Capabilities

### New Capabilities
- `instagram-insights`: read-only import of existing Reels as external posts, Reel insights into content analytics, delayed figures, and the read-only permission set.

### Modified Capabilities
- None.

## Impact

- Server: a read-only Instagram client and the listing and metrics operations of the Instagram adapter.
- Jobs: the re-sync and collection jobs run for Instagram accounts; collection runs again for Reels whose figures were delayed.
- Platform changes: `add-instagram-integration` (phase 2) extends the same adapter with publishing operations and the publishing permission, requested in its own consent.
