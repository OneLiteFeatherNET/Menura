# Proposal

Conventional Commits: `feat(ingest)` (code PR). Phase: 4 (S3 & Recording). Depends on: `add-access-control`, `add-client-and-mcp-auth` (the OBS plugin signs in with device tokens).

Strand: content.

## Why

Every stream produces recordings that are worth keeping, cutting and re-using, but today they stay as loose files
on a streaming PC. Getting them into Menura must not depend on a desktop app running, on the network staying up,
or on the streamer remembering to upload. The OBS plugin is installed on every streaming PC anyway, so it signs in
itself, announces each finished recording and uploads it, resuming after any interruption. Transcription and
cutting need a GPU and are handled later by the media job queue, not here.

## What Changes

- The OBS plugin detects the end of a recording and of a stream, announces the finished file(s) with metadata
  (scene collection, start and end time, audio track layout with each track's role and, for spoken tracks, its language, stream title and category if known) and uploads
  them itself. The desktop app takes no part in the upload.
- The OBS plugin dock shows upload state and lets the streamer add a marker during the stream with a hotkey
  ("highlight here"). Markers travel with the recording.
- The server creates a recording session for each announced recording and issues presigned multipart upload
  URLs. Uploads resume after network loss or an OBS restart. The checksum is verified on completion.
- Sessions move through the states announced, uploading, uploaded, indexed and failed. A session that reaches
  uploaded makes the server create a transcription job in the media job queue (`add-media-job-queue`).
- The server never proxies media bytes and never transcodes.
- Duplicate recordings are detected by checksum.
- Each session records its owner and contributors; any team member holding the editor role contributes their own
  recordings, and changes to another person's session follow the editor role, not ownership alone.
- Optional live chunk upload during a stream is possible, but post-stream upload stays the default.

## Capabilities

### New Capabilities

- `recording-ingest`: recording sessions on the server, metadata and markers, owner and contributors, presigned
  resumable upload, checksum verification, session states, duplicate detection and the trigger of transcription jobs.
- `obs-recording-upload`: how the OBS plugin detects finished recordings and streams, announces them, uploads them
  itself with resume, and shows upload state and markers in its dock.

### Modified Capabilities

None (no existing capabilities).

## Impact

- Server: session resource and state machine, presigned multipart URL issuance against S3-compatible object storage,
  checksum verification, owner and contributor fields, metadata and markers in PostgreSQL, job creation on uploaded.
- OBS plugin (C++, libobs, Windows, macOS and Linux): sign-in through `add-access-control`, stop detection, announce
  and upload with persisted resume state, dock UI, marker hotkey. The plugin installer and auto-update belong to
  `add-obs-plugin`.
- Desktop app: no part in recording upload.
- Transcription and cutting: `add-local-transcription`, `add-media-job-queue`.
