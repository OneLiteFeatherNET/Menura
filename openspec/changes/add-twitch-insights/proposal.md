# Proposal

Conventional Commits: `feat(twitch)` (code PR). Phase: 1 (Recognition & Measurability). Depends on: `add-channel-connections`, `add-external-post-import`, `add-content-analytics`.

Strand: platform.

## Why

Twitch holds most of the team's long-form material: broadcasts, highlights and clips that viewers have seen but nobody has catalogued. Twitch deletes VODs after 14 days for normal broadcasters and 60 days for others, so an import that waits loses them. Importing the channel's broadcasts, highlights and clips as external posts makes this archive searchable and measurable, and their view counts give the analytics a Twitch baseline before any publishing starts.

## What Changes

- Import the existing VODs, highlights and clips of a connected Twitch channel as external posts, with platform, identifier, URL, title, duration, publish time and format. Menura references the broadcast and its playback link and does not copy any media.
- Link each clip to its broadcast external post when the broadcast is known.
- Record view counts into content analytics, marked as provided by Twitch and collected at a known time.
- Mark items that Twitch reports as deleted or unavailable as removed, and stop offering their links.
- Request only read permissions. No channel update, clip creation, schedule, chat, moderation, subscription or payment permission is requested by this change.
- Not in this change: clip creation during a live broadcast, title and category updates, the stream schedule (`add-twitch-integration`, phase 2).

## Capabilities

### New Capabilities
- `twitch-insights`: read-only import of VODs, highlights and clips as external posts, clip-to-broadcast links, view counts, removal of deleted items, and the read-only permission set.

### Modified Capabilities
- None.

## Impact

- Server: a read-only Twitch client and the listing and metrics operations of the Twitch adapter.
- Jobs: the re-sync runs for Twitch channels at an interval shorter than Twitch's VOD retention window; the view-count collection runs with the analytics job.
- Platform changes: `add-twitch-integration` (phase 2) extends the same adapter with its write operations, requested in its own consent.
