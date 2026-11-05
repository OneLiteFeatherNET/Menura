# Proposal

Conventional Commits: `feat(youtube)` (code PR). Phase: 1 (Platforms & Statistics). Depends on: `add-channel-connections`, `add-external-post-import`, `add-content-analytics`.

Strand: platform.

## Why

Before Menura publishes anything to YouTube, the team needs to see what the channel already has and how it performs. The back catalogue of videos and Shorts is invisible to the hub until it is imported, so content analytics would show nothing for the channel's existing work. YouTube publishes its own statistics per video, which makes it the first platform to feed the analytics with real figures.

## What Changes

- Import the existing videos and Shorts of a connected YouTube channel as external posts, with platform, video identifier, URL, title, publish time and format (video or Short), through the read operations of the YouTube Data API.
- Collect the statistics YouTube provides for each external post (views, watch time, likes and comments where available) into content analytics, with each metric the platform does not provide recorded as unavailable.
- Request only the read permission this feature needs. No upload, edit, delete, live stream or comment permission is requested by this change.
- Not in this change: publishing, playlists, thumbnails and scheduled publication (`add-youtube-integration`, phase 2), live stream control and comment moderation (out of scope for the product).

## Capabilities

### New Capabilities
- `youtube-insights`: read-only import of existing YouTube videos and Shorts as external posts, native statistics into content analytics, and the read-only permission set.

### Modified Capabilities
- None.

## Impact

- Server: a read-only YouTube client and the listing and metrics operations of the YouTube adapter.
- Jobs: the re-sync and collection jobs of `add-external-post-import` and `add-content-analytics` run for YouTube channels; the YouTube quota used by reads is counted against the connection's quota state.
- Platform changes: `add-youtube-integration` (phase 2) extends the same adapter with the write operations and their scopes, requested in its own consent.
