# Proposal

Conventional Commits: `feat(instagram)` (code PR). Phase: 2 (Platforms & Join Attribution). Depends on: `add-channel-connections`, `add-content-distribution`, `add-asset-upload`, `add-instagram-insights`.

Strand: content.

## Why

Instagram Reels are a first-stage channel for the stream and short-form content. Publishing a Reel is a two-step platform process with its own waiting, limits and failure states, and the platform caps how many posts one account may publish per day. Without a native adapter, these limits are discovered after a post is due, and the metrics that would show which Reels work stay inside Instagram.

## What Changes

- Add an Instagram adapter behind the adapter port of `add-channel-connections`: publish, status and capabilities.
- Accept only professional (business or creator) accounts; the check runs when the account is connected, and a non-professional account cannot publish.
- Publish Reels from a finished asset recorded by `add-asset-upload`, with a cover image from the asset (or a frame of the video). Only Reels are published in this change.
- Run the two-step publication: create the media container, wait until it is ready with status polling, then publish it. A container that expires or errors before publication is recreated within the retry limit; an unknown publish outcome is never repeated automatically.
- Check captions (length, hashtags, mentions), video format, size and duration in the schedule dry run.
- Respect the account's publishing limit for the rolling 24 hours: the planner does not schedule past the remaining allowance, and the dry run shows the post that would exceed it.
- Report deletion as unsupported through the chosen connection type; the publisher removes the Reel in Instagram.
- **Non-Goals:** no feed images, carousels, stories or trial Reels; no comment or message handling; no aggregator between Menura and Instagram.

## Capabilities

### New Capabilities
- `instagram-integration`: professional account requirement, Reels publishing with status waiting, cover rules, caption and media checks, the daily publishing allowance, and the adapter capability report.

### Modified Capabilities
- None.

## Impact

- Server and jobs: an Instagram adapter implementing the adapter port; the publication job and the planner use the allowance.
- Platform setup: an Instagram app with the Instagram API product and the publishing permission (verified in `design.md`).
- Web: the channel view shows the account type and the remaining daily allowance; the calendar shows dry-run results.
- Assets: publishes finished assets recorded by `add-asset-upload`; each published Reel is recorded as an external post (`add-external-post-import`).
