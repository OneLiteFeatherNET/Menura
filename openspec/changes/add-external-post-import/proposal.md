# Proposal

Conventional Commits: `feat(publish)` (code PR). Phase: 1 (Pool, Calendar & Analytics). Depends on: `add-access-control`, `add-channel-connections`.

Strand: platform.

## Why

A hub that measures and recycles posts needs to know what already exists on each channel. Posts and videos made before Menura was connected, and those made directly in a platform's studio, are invisible to the hub. Without them, content analytics would start empty and every comparison would be incomplete. Importing a channel's existing posts when it is connected makes its history measurable from the first day, and a periodic re-sync keeps the hub correct when the channel changes on the platform.

## What Changes

- Import the existing published posts and videos of every connected channel as external post records: platform, external identifier, URL, title, publish time and format. The import uses the listing operation of the platform adapter defined in `add-channel-connections`. No media is copied into Menura's storage.
- Re-sync each connected channel on a schedule: new posts are added, changed posts are updated, and nothing is duplicated.
- Mark posts that the platform removes as removed, with the time the removal was seen. Records and metric history are never deleted.
- Show a channel overview that lists its external posts with their status and the time of the last successful re-sync.
- Record posts that Menura publishes later as external posts of the same kind, so content analytics treats them alike. This part applies once content distribution is available.
- Each platform change (`add-youtube-insights`, `add-twitch-insights`, `add-tiktok-insights`, `add-instagram-insights`) implements the listing for its platform with read-only permissions.

## Capabilities

### New Capabilities
- `external-posts`: import of existing posts and videos of connected channels, periodic re-sync, marking of removed posts, the channel overview, and the record of posts Menura publishes.

### Modified Capabilities
- None. The import requirement was moved here from `add-content-distribution`, where it had stored imports as library content items. Records here are external posts, not library items.

## Impact

- Server: external post model in PostgreSQL with one record per channel and platform identifier, the import and re-sync logic on the adapter listing operation, and the removal state.
- Jobs: the periodic re-sync runs as a Kubernetes CronJob in the `jobs` component, skips channels that need reconnection, and records failures per channel.
- Web: channel overview with the imported and published external posts.
- Analytics: metric points attach to external posts (`add-content-analytics`), so this change defines the record that analytics reads.
- Platform changes implement the listing operation and depend on this record model.
