# Tasks

## 1. Verify platform facts

- [ ] 1.1 Confirm the `videos.insert` unit cost and the costs of thumbnail, playlist and update methods in the Google Cloud Console quota page for a test project, and record them in the quota configuration; verify the configured values equal the console values
- [ ] 1.2 Confirm in a test upload to a test channel how `publishAt` combines with `privacyStatus` and whether a scheduled video stays private until its publish time; record the result in design.md; verify the result matches the scheduling assumption
- [ ] 1.3 Confirm the minimal scope set for playlists, thumbnails and read-back against the method reference; verify the consent request lists exactly that set

## 2. Quota budgeting

- [ ] 2.1 Write failing tests for the daily cost estimate from the configuration table, with a fixed clock and the quota reset at midnight Pacific Time; verify they fail first
- [ ] 2.2 Implement the cost estimate and the dry run warning for planned posts that exceed the remaining quota; verify the tests pass
- [ ] 2.3 Write a failing test that a publication delayed by an exhausted quota is attempted again after the reset; verify it fails first, then implement it and verify it passes

## 3. Playlists

- [ ] 3.1 Write failing tests for playlist selection limited to the channel's own playlists; verify they fail first
- [ ] 3.2 Implement playlist selection and the scheduling refusal for foreign playlists; verify the tests pass

## 4. Upload and publication

- [ ] 4.1 Write failing tests for resumable upload against a fake platform: interrupted upload resumes from the confirmed byte, and a lost response is resolved by reading the upload status before any retry; verify they fail first
- [ ] 4.2 Implement the resumable upload from object storage in the publication job; verify the tests pass and that no server handler receives media bytes
- [ ] 4.3 Write failing tests for the visibility and made-for-kids requirement (refused without either, sent to the platform when present) and for scheduled publication time; verify they fail first
- [ ] 4.4 Implement the visibility, made-for-kids and publish time handling; verify the tests pass
- [ ] 4.5 Write failing tests for the read-back of the applied visibility and thumbnail state, including the mismatch problem; verify they fail first
- [ ] 4.6 Implement read-back and thumbnail setting with the eligibility warning; verify the tests pass
- [ ] 4.7 Write a failing test that a Short is published with the Short format and a long video without it; verify it fails first, then implement it and verify it passes

## 5. Documentation

- [ ] 5.1 Document the YouTube setup in the operator guide: app creation, audit status recording, quota configuration and what the read-back shows; verify the steps are complete by following them on a test project

## 6. Integration check

- [ ] 6.1 Publish one scheduled test video to a test channel end to end and verify the platform shows the planned publish time, the playlist and the applied visibility
- [ ] 6.2 Open the code pull request titled `feat(youtube): publish videos and shorts natively`

## Note

Native statistics live in `add-youtube-insights` (phase 1).
