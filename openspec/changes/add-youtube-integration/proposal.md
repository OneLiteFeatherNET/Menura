# Proposal

Conventional Commits: `feat(youtube)` (code PR). Phase: 2 (Planning & Scheduling). Depends on: `add-channel-connections`, `add-content-distribution`, `add-asset-upload`, `add-youtube-insights`.

Strand: platform.

## Why

The first platform that OneLiteFeather publishes to natively is YouTube. Today a finished video is uploaded by hand in YouTube Studio, with chapters, thumbnail, playlist and publish time set one by one, and nothing records what was published or how it performed. The native integration takes a finished asset recorded by `add-asset-upload`, applies the chapter marks, thumbnail and metadata the team already decided, and publishes it at the planned time, within YouTube's quota and audit limits.

## What Changes

- Upload long videos and Shorts from finished assets recorded by `add-asset-upload` as resumable uploads; the bytes go directly from the hub storage to the platform, never through the server.
- Set title, description and tags per post from the approved post. Chapter lines generated from timeline marks are specified in `add-content-library` (phase 4), not here.
- Add the video to one or more playlists the channel owns.
- Set a custom thumbnail from a thumbnail asset selected on the post.
- Schedule publication: the video is uploaded with a publish time and the platform makes it public at that time.
- Set the platform's visibility and made-for-kids declaration per post; publication is refused without an explicit value.
- Budget the daily API quota: the schedule dry run warns when the day's planned uploads and updates exceed the quota the connection reports.
- Read back the visibility that YouTube actually applied after upload and show any mismatch with the requested visibility.
- Not in this change: live stream control, comment moderation, and YouTube as an overlay data source.

## Capabilities

### New Capabilities
- `youtube-integration`: YouTube uploads for long videos and Shorts, metadata, chapters, playlists, thumbnails, scheduled publication, visibility and made-for-kids settings, quota budgeting and read-back of the applied visibility.

### Modified Capabilities
- None.

## Impact

- Server: YouTube adapter behind the channel-connections port; upload session tracking and quota accounting in the publication job.
- Jobs: the publication CronJob uploads due posts through the adapter.
- Web: YouTube fields on a post (visibility, made-for-kids, playlist, thumbnail), and quota warnings in the dry run.
- Posts: the published video identifier and URL are stored on the post and recorded as an external post (`add-external-post-import`).
- Access: publication requires the publisher role and an approved post (principle 4).
