# Proposal

Conventional Commits: `feat(youtube)` (code PR). Phase: 1 (Pool, Calendar & Analytics). Depends on: `add-channel-connections`, `add-content-distribution`, `add-asset-upload`, `add-youtube-insights`; all four are phase 1.

Strand: platform.

## Why

The first platform that OneLiteFeather publishes to natively is YouTube. Today a finished video is uploaded by hand in YouTube Studio, with chapters, thumbnail, playlist and publish time set one by one, and nothing records what was published or how it performed. The native integration takes a finished asset recorded by `add-asset-upload`, applies the chapter marks, thumbnail and metadata the team already decided, and publishes it at the planned time, within YouTube's quota and audit limits.

## What Changes

- Upload long videos and Shorts from finished assets recorded by `add-asset-upload` as resumable uploads through `videos.insert`; the bytes go directly from the hub storage to the platform, never through the server.
- Set title, description and tags per post from the approved post, in the snippet of the upload. Chapter lines generated from timeline marks are specified in `add-content-library` (phase 1), not here.
- Add the video to one or more playlists the channel owns, through `playlistItems.insert`.
- Schedule publication natively: the video is uploaded as private with `status.publishAt` set to the scheduled time, and the platform makes it public at that time. The adapter declares native scheduling to `add-content-distribution`.
- Send the platform's visibility (`privacyStatus`) and made-for-kids declaration explicitly on every upload; publication is refused without an explicit value, and no platform default is relied on.
- Account the daily API quota per channel: each step consumes its configured units, and a step that would exceed the remaining quota of the day or the daily `videos.insert` cap is refused before the platform is called. The remaining quota is shown.
- Retry each failed step (upload, metadata, playlist, schedule, thumbnail) with backoff; a step that still fails is shown as failed with its reason on the post.
- Should: set a custom thumbnail through `thumbnails.set` after the video has finished processing, from a thumbnail asset selected on the post.
- Read back the visibility that YouTube actually applied after upload and show any mismatch with the requested visibility.
- Not in this change: live stream control, comment moderation, and YouTube as an overlay data source.

## Capabilities

### New Capabilities
- `youtube-integration`: YouTube uploads for long videos and Shorts, metadata, chapters, playlists, thumbnails, native scheduled publication, explicit visibility and made-for-kids settings, daily quota accounting with refusal, retries of failed steps and read-back of the applied visibility.

### Modified Capabilities
- None.

## Impact

- Server: YouTube adapter behind the channel-connections port; upload session tracking and quota accounting in the publication job.
- Jobs: the publication CronJob uploads due posts through the adapter.
- Web: YouTube fields on a post (visibility, made-for-kids, playlist, thumbnail), the remaining quota of the channel, and the failed steps of a post.
- Posts: the published video identifier and URL are stored on the post and recorded as an external post (`add-external-post-import`).
- Access: publication requires the publisher role and an approved post (principle 4).
