# Proposal

Conventional Commits: `feat(library)` (code PR). Phase: 2 (Planning & Scheduling). Depends on: `add-access-control`.

Strand: content.

## Why

Finished videos (for example rendered shorts and long-form cuts) and images (thumbnails, graphics) are today copied around by hand from team PCs and shared folders. Nothing records who made them, what format they have, or whether the copy that reached a publisher is the same file. Scheduling and publication (`add-content-distribution`) can only act on finished assets that already sit in the hub's storage, so the upload path has to exist first. Editors work in the web app, often on large files over connections that drop, so the upload must be resumable and verified, and must never pass the media through the server.

## What Changes

- Add a finished-asset record with its format, duration (videos), resolution, size, checksum and uploader.
- Let an editor upload a finished video or image from the web app directly into the private Menura bucket on the OLF Ceph object storage, through presigned multipart upload with parts of at most 95 MB (principle 15). The server issues URLs and never receives media bytes (principle 1).
- Resume an interrupted upload from the first part that is not yet confirmed.
- Verify the checksum when the upload completes; an asset whose stored bytes do not match is never listed as available.
- List assets and preview them through short-lived signed links, never through public or permanent URLs.
- Delete an asset only by an explicit request, with exactly one audit entry; a deletion is refused while a post still references the asset.
- Should: flag an upload whose checksum is already known, pointing to the existing asset, and let the editor decide.
- No breaking changes: this is the first asset release.
- Not in this change: lineage of assets to timelines, clips and recordings (added by `add-content-library`, phase 4), transcoding or rendering (never on the cluster), and any upload of recordings (`add-recording-ingest`).

## Capabilities

### New Capabilities
- `finished-assets`: presigned multipart upload of finished videos and images, resume after interruption, checksum verification on completion, the asset record, signed listing and preview, explicit audited deletion, and duplicate detection by checksum.

### Modified Capabilities
- None.

## Impact

- Server: asset record and upload session model in PostgreSQL, presigned multipart URL issuance against the S3 API of the OLF Ceph object storage, completion check, signed-link issuance, and the reference check used before deletion.
- Helm and configuration: bucket name, upload URL lifetime, signed-link lifetime and part size as environment variables; the Cloudflare request-body limit stays below 100 MB (principle 15).
- Web: upload dialog with progress and resume, asset list and preview.
- Distribution (`add-content-distribution`): depends on this change and attaches only the assets recorded here.
- Access: uploading requires the editor role from `add-access-control`; deletion requires the same role and is audit-logged.
