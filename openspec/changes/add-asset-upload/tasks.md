# Tasks

## 1. Asset record and roles

- [ ] 1.1 Write failing unit tests that an asset record holds kind, format, duration (videos only), resolution, size, checksum and uploader, and that a declaration with a missing required field is refused naming the field; verify they fail for the expected reason.
- [ ] 1.2 Implement the asset model and its validation; verify the 1.1 tests pass.
- [ ] 1.3 Write failing unit tests that only the editor role may create an upload session, that a viewer receives 403 and no session is created; verify they fail.
- [ ] 1.4 Implement the role check on session creation; verify the 1.3 tests pass.
- [ ] 1.5 Add the database migration for assets and upload sessions; verify it applies to an empty PostgreSQL in an integration test.

## 2. Presigned multipart upload

- [ ] 2.1 Write failing unit tests with a fake object storage that a session creates one multipart upload and presigned part URLs, that no part is larger than 95 MB (a 12 GB file yields parts of at most 95 MB), and that the session moves to uploading; verify they fail.
- [ ] 2.2 Implement multipart creation and presigned part URL issuance against the S3 API; verify the 2.1 tests pass.
- [ ] 2.3 Write a failing test that no route of the server accepts a request body with media bytes, by checking the route table against an allowlist; verify it fails if such a route exists, then implement the allowlist check and verify it passes.
- [ ] 2.4 Write failing unit tests that a raw recording session is recorded as raw material with its stream title and date and uses parts of at most 95 MB, that a recording above the configured raw material limit (default 50 GB, set by environment variable) is refused with a Problem Details response and creates no session; verify they fail.
- [ ] 2.5 Implement the raw material kind, the configurable size limit and its environment variable; verify the 2.4 tests pass.

## 3. Resume and completion

- [ ] 3.1 Write failing unit tests that the session reports the confirmed parts, that the upload continues with the first part not confirmed, and that a reload returns the same session without creating a second one; use a fake clock; verify they fail.
- [ ] 3.2 Implement the confirmed-parts report and the resume path; verify the 3.1 tests pass.
- [ ] 3.3 Write failing unit tests that a matching stored checksum records the asset as available and lists it, that a mismatch marks the session failed and lists nothing, and that the mismatch is returned to the editor; verify they fail.
- [ ] 3.4 Implement completion with the checksum comparison and no media processing step; verify the 3.3 tests pass.

## 4. Listing and signed links

- [ ] 4.1 Write failing unit tests that a signed link is issued for one object with the configured short lifetime, that a link older than that lifetime is refused by the storage port, and that no link appears in logs or error output; use a fake clock; verify they fail.
- [ ] 4.2 Implement signed link issuance for preview and download, with redaction of links from logs and traces; verify the 4.1 tests pass.
- [ ] 4.3 Write failing unit tests that an asset the caller may not see answers 404 for listing, preview and deletion alike, and that the list contains only visible assets; verify they fail.
- [ ] 4.4 Implement the visibility filter and the 404 answer; verify the 4.3 tests pass.

## 5. Deletion

- [ ] 5.1 Write failing unit tests that a deletion request by an editor removes an unreferenced asset and writes exactly one audit entry with actor, asset, time and channel, that a deletion of a referenced asset is refused with the referencing post named, and that no asset is deleted without a request; verify they fail.
- [ ] 5.2 Implement the deletion with the reference check port and the audit entry; verify the 5.1 tests pass.

## 6. Duplicate detection (Should)

- [ ] 6.1 Write failing unit tests that an upload declaring a checksum already recorded for the same uploader is flagged with a reference to the existing asset, and that a confirmed duplicate creates a second record; verify they fail.
- [ ] 6.2 Implement duplicate flagging by checksum and the confirmation step; verify the 6.1 tests pass.

## 7. Integration check

- [ ] 7.1 Run the integration tests tagged `integration` against PostgreSQL and an S3-compatible test container, under `flock ~/.cache/claude-heavy.lock`, and verify a multipart upload with a dropped and resumed part ends as an available asset with a matching checksum, and that a tampered part ends as failed.
- [ ] 7.2 Open the code pull request titled `feat(library): add asset upload`.

## Workflow follow-up

- Tick the completed boxes and archive the change in a follow-up pull request after the code pull request is merged.
