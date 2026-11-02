# Tasks

## 1. Adapter and capabilities

- [ ] 1.1 Write failing unit tests that the TikTok adapter reports publish and status as available, delete as unavailable, and that the adapter is registered only for the TikTok platform; verify they fail for the expected reason.
- [ ] 1.2 Implement the TikTok adapter behind the adapter port of `add-channel-connections` and its capability report; verify the 1.1 tests pass.

## 2. Creator check and privacy rules

- [ ] 2.1 Write failing unit tests with a fake platform client that the creator's options are read in the same run before each publication, that a visibility not offered is refused, and that interaction settings disabled by the creator cannot be enabled; verify they fail.
- [ ] 2.2 Implement the creator check and the privacy and interaction validation in the dry run and the publication path; verify the 2.1 tests pass.
- [ ] 2.3 Write failing unit tests that an unaudited channel refuses scheduling of a public post and that the channel view states the restriction; verify they fail.
- [ ] 2.4 Implement the audit flag, the server-side refusal and the channel view text; verify the 2.3 tests pass.

## 3. Publishing modes and outcomes

- [ ] 3.1 Write failing unit tests with a fake platform client and an injected clock that a direct post is published and completes, that an inbox draft is shown as waiting and stays scheduled, and that an unfinished inbox draft is not published again; verify they fail.
- [ ] 3.2 Implement direct post, inbox mode and the waiting state; verify the 3.1 tests pass.
- [ ] 3.3 Write failing unit tests that `internal` and `video_pull_failed` are retried with growing delays, that `spam_risk_too_many_posts`, banned-creator, `auth_removed` and `publish_cancelled` are marked failed with reason and never retried, and that an unknown outcome after lease expiry is not published again; verify they fail.
- [ ] 3.4 Implement the outcome mapping and retry classes; verify the 3.3 tests pass.
- [ ] 3.5 Write failing tests that uploads go to the issued upload URL from object storage and that a status call respects the per-token limit with a back-off on rate-limit responses; verify they fail.
- [ ] 3.6 Implement the upload and the rate-limit handling; verify the 3.5 tests pass.

## 4. Limits and disclosure

- [ ] 4.1 Write failing unit tests for the dry run: a video longer than the creator's maximum and a title over the limit are shown on the post; a branded post sends the disclosure setting; verify they fail.
- [ ] 4.2 Implement the length and title checks and the disclosure setting; verify the 4.1 tests pass.

## 5. Contract tests and documentation

- [ ] 5.1 Write a contract test against recorded platform responses for init, upload, status and the error mapping; verify it passes.
- [ ] 5.2 Document the TikTok setup (developer app, products, scopes `video.publish`, `video.upload`, `user.info.basic`, audit steps) in the operations notes; verify every scope named there appears in the adapter configuration.

## 6. Integration check

- [ ] 6.1 Run the integration suite tagged `integration` with the heavy-job lock and verify the publish, inbox, retry and unaudited flows end to end against a fake TikTok server.
- [ ] 6.2 Open the code pull request titled `feat(tiktok): add tiktok integration`.

## Implemented elsewhere

- Native metrics from the TikTok video list: `add-tiktok-insights` (phase 1).
