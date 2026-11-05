# Proposal

Conventional Commits: `feat(twitch)` (code PR). Phase: 2 (Planning & Scheduling). Depends on: `add-channel-connections`, `add-content-distribution`, `add-asset-upload`, `add-twitch-insights`.

Strand: platform.

## Why

Streams are planned in Menura and recorded on the streaming PCs, but the Twitch side is maintained by hand: the stream title and category are changed in the Twitch dashboard, and the schedule is typed in again. Twitch is also the place where clips are made for the community, and a clip made during a live broadcast is not linked to the stream it came from. The native Twitch integration keeps the Twitch channel in step with Menura's plan and creates clips from Menura. Importing Twitch's own broadcasts, highlights and clips, and their view counts, is specified in `add-twitch-insights`.

Twitch does not offer a video upload API. Publishing finished videos to Twitch is therefore out of scope; the recording and the finished assets are published elsewhere (see the YouTube change).

## What Changes

- Create clips while the broadcast is live, from the connected channel, and store the clip identifier and link on the planned stream.
- Lineage of imported Twitch broadcasts to the recordings they match is specified in `add-content-library` (phase 4), not here.
- Update the channel's stream title and category from the planned stream when the planned stream starts, or when a publisher asks for it.
- Sync the Twitch stream schedule with Menura's planned streams: planned streams become schedule segments, changes and cancellations are mirrored, and a planned stream that Twitch cannot accept (for example a channel type that may not create segments) is reported.
- Imports of VODs, highlights and clips, their view counts and removed items are specified in `add-twitch-insights` (phase 1), not here.
- **BREAKING:** none. This is the first Twitch integration.
- Non-Goal: uploading or publishing video to Twitch. Twitch offers no video upload endpoint; this is not planned.
- Non-Goal: downloading VOD media from Twitch. No official download endpoint exists; Menura references the broadcast and uses its own recording.

## Capabilities

### New Capabilities
- `twitch-integration`: clip creation during live broadcasts, title and category updates from the plan, stream schedule sync, and the explicit exclusion of video upload.

### Modified Capabilities
- None.

## Impact

- Server: Twitch adapter behind the channel-connections port; clip creation and confirmation; title, category and schedule sync.
- Jobs: the schedule sync runs in the `jobs` component.
- Web: Twitch section on a stream (title, category, clip button while live) and a schedule-sync status.
- Assets: no Twitch channel is offered as a publication target for a finished asset; no media is copied to or from Twitch.
- Access: changing a Twitch title, category or schedule requires the publisher role; clip creation requires a connected channel with the clip permission.
