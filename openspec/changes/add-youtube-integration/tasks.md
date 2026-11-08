# Tasks

## 1. Verify platform facts

- [ ] 1.1 Confirm the `videos.insert` unit cost in the Google Cloud Console quota page for a test project, and record it in the quota configuration together with the 50-unit costs of `playlistItems.insert` and `thumbnails.set`; verify the configured values equal the console values
- [ ] 1.2 Confirm in a test upload to a test channel that a video uploaded as private with `status.publishAt` stays private until that time and becomes public at it; record the result in design.md; verify the result matches the documented behaviour
- [ ] 1.3 Confirm the minimal scope set for playlists, thumbnails and read-back against the method reference; verify the consent request lists exactly that set

## 2. Quota budgeting

- [ ] 2.1 Write failing tests for the daily cost accounting from the configuration table, with a fixed clock and the quota reset at midnight Pacific Time, and for the refusal of a step that would exceed the remaining quota or the daily `videos.insert` cap, with the remaining quota shown; verify they fail first
- [ ] 2.2 Implement the cost accounting per channel and the refusal before the platform is called; verify the tests pass
- [ ] 2.3 Write a failing test that a publication delayed by an exhausted quota is attempted again after the reset; verify it fails first, then implement it and verify it passes

## 3. Playlists

- [ ] 3.1 Write failing tests for playlist selection limited to the channel's own playlists, and for the added video being inserted through `playlistItems.insert`; verify they fail first
- [ ] 3.2 Implement playlist selection through `playlistItems.insert` and the scheduling refusal for foreign playlists; verify the tests pass

## 4. Upload and publication

- [ ] 4.1 Write failing tests for resumable upload against a fake platform: interrupted upload resumes from the confirmed byte, and a lost response is resolved by reading the upload status before any retry; verify they fail first
- [ ] 4.2 Implement the resumable upload from object storage in the publication job; verify the tests pass and that no server handler receives media bytes
- [ ] 4.3 Write failing tests for the visibility and made-for-kids requirement (refused without either, both sent explicitly on every upload, never left to a default) and for scheduled publication (upload as private with `publishAt` equal to the scheduled time, native scheduling declared in the adapter capability flag); verify they fail first
- [ ] 4.4 Implement the explicit visibility and made-for-kids values, the private upload with `publishAt`, and the capability flag; verify the tests pass
- [ ] 4.5 Write failing tests for the read-back of the applied visibility and thumbnail state, including the mismatch problem; verify they fail first
- [ ] 4.6 Implement read-back and the thumbnail setting through `thumbnails.set` after processing, with the eligibility warning; verify the tests pass
- [ ] 4.7 Write a failing test that a Short is published with the Short format and a long video without it; verify it fails first, then implement it and verify it passes

## 5. Retries of failed steps

- [ ] 5.1 Write failing tests with a fake platform that a failed playlist or thumbnail step is retried with growing delays and both attempts are recorded, that a lost upload response is resolved by reading the upload status before any retry, and that a step failing at the limit is shown as failed with its reason; verify they fail first
- [ ] 5.2 Implement per-step retries with backoff and the failed-step display on the post; verify the tests pass

## 6. Documentation

- [ ] 6.1 Document the YouTube setup in the operator guide: app creation, audit status recording, quota configuration and what the read-back shows; verify the steps are complete by following them on a test project

## 7. Integration check

- [ ] 7.1 Publish one scheduled test video to a test channel end to end and verify the platform shows the planned publish time, the playlist and the applied visibility
- [ ] 7.2 Open the code pull request titled `feat(youtube): publish videos and shorts natively`

## Note

Native statistics live in `add-youtube-insights` (phase 1).
