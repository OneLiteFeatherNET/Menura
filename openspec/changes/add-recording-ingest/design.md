# Design

## Context

The OBS plugin is installed on every streaming PC and holds its own sign-in (`add-access-control`). It can read the
file paths of finished recordings and the stream events. The server stores media in S3-compatible object storage and
metadata in PostgreSQL (principle 1). Transcription runs only on GPU PCs and is scheduled by the media job queue
(`add-media-job-queue`), so the upload path must not depend on a GPU or a desktop app (principles 2 and 12).
See proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- A recording that stops is never lost, even when OBS or the network is unavailable at that moment.
- Bytes flow from the PC directly to object storage; the server only signs and verifies.
- Every streaming PC can upload, whether or not it has a GPU.

**Non-Goals:**
- Server-side transcoding, thumbnail generation or any ffmpeg step on the cluster (principle 2).
- Transcription on the streaming PC itself or on the cluster (the job queue decides where it runs).
- Choosing among recording profiles or encoders; OBS settings stay as the streamer has them.
- Writing to the stream destination or chat; ingest only moves files to the hub.

## Decisions

- **The OBS plugin announces and uploads; no desktop app in the path.** Every streaming PC has the plugin, so the
  plugin is the only component guaranteed to be present when a recording stops. This removes the local handoff.
  Rejected: local handoff to the desktop app (the desktop app exists only on GPU PCs, so recordings from other PCs
  would never leave them; also a second process to keep alive).
- **Two-step ingest: announce, then presigned multipart upload.** The server creates the session first, so an
  interrupted upload has a session to resume against. Each part is uploaded directly to object storage.
  Rejected: a single presigned PUT for the whole file (a 3 GB upload cannot resume after a network loss); a server
  proxy for media (violates principle 1).
- **Resume state is kept by the plugin and confirmed by the server.** The plugin stores the session and file
  identifiers in OS-permissioned storage; the server's list of confirmed parts is the source of truth, so partly
  written local state cannot cause a wrong resume.
  Rejected: resume state held only in memory (lost on OBS restart).
- **Checksum is computed by the plugin and verified by the server on completion.** A mismatch fails the session rather
  than indexing a broken file.
  Rejected: trusting storage success alone (does not catch corruption that happened before upload).
- **Duplicate detection is a flag, not a rejection.** A rejected duplicate could silently drop a recording whose first
  copy failed.
  Rejected: refusing duplicate announcements outright.
- **Owner is an attribute of the session, set from the announcing token.** The library filters by contributor
  without a join. Editing rights come from the editor role only.
  Rejected: a separate ownership table (extra join on every list); owner may always edit (a former editor keeps write access).
- **Session state machine is enforced on the server.** Clients may request a transition; the server accepts only the
  transitions in the spec.
- **Transcription job created by the state change to uploaded.** One place decides when work is queued, so a session
  cannot be uploaded without a job or a job without an uploaded file.

## Risks / Trade-offs

- [Risk] Plugin update changes the announcement format → Mitigation: the payload carries a version; the server refuses
  unknown versions with a Problem Details error that the plugin shows.
- [Risk] Presigned URLs expire during a long resume → Mitigation: the plugin requests fresh URLs for the remaining parts
  after a restart; the server keeps confirmed parts.
- [Risk] Disk fills before upload finishes → Mitigation: the dock shows pending state; the plugin never deletes a
  recording that is not yet uploaded.
- [Risk] Live chunk upload (Could) adds complexity → Mitigation: off by default, same session and upload code; it can be
  removed without changing the state machine.
