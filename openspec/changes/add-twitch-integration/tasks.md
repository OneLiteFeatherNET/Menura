# Tasks

## 1. Verify platform facts

- [ ] 1.1 Confirm the category field name and its lookup in the channel update reference, and the numeric rate limit from response headers on a test channel; record both in design.md; verify the adapter uses the confirmed names
- [ ] 1.2 Confirm the Developer Services Agreement wording on downloading content with legal review before any media feature is proposed; verify the decision is recorded in design.md

## 2. Clips

- [ ] 2.1 Write failing tests for clip creation: refused while offline, stored on the planned stream only after confirmation, failed when not confirmed in the wait time; use a fake clock; verify they fail first
- [ ] 2.2 Implement clip creation and confirmation; verify the tests pass

## 3. Title, category and schedule

- [ ] 3.1 Write failing tests that title and category are set at planned start and on request, and that a rejection keeps the old values; verify they fail first
- [ ] 3.2 Implement the title and category update and the display of the reported values; verify the tests pass
- [ ] 3.3 Write failing tests for schedule mirroring: create, move and cancel; a channel type that may not create segments reports the planned stream without creating one; verify they fail first
- [ ] 3.4 Implement the one-way schedule sync; verify the tests pass
- [ ] 3.5 Write a failing test that the permissions requested exclude chat, moderation and subscription scopes and include the schedule scope only when the feature is enabled; verify it fails first, then implement and verify it passes

## 4. Exclusion

- [ ] 4.1 Write a failing test that no Twitch channel is offered as a target for a finished asset; verify it fails first, then implement it and verify it passes

## 5. Documentation

- [ ] 5.1 Document in the operator guide the Twitch app setup, the partner or affiliate requirement for schedule segments, and the limit that no video is uploaded; verify the steps are complete by following them on a test channel

## 6. Integration check

- [ ] 6.1 Run an end-to-end check on a test channel: planned stream sets title and category at start, schedule segment appears, a clip made while live is stored on its planned stream; verify each result on the platform
- [ ] 6.2 Open the code pull request titled `feat(twitch): sync title and schedule, create clips`

## Note

Imports of VODs, highlights and clips, their view counts and removed items are implemented in `add-twitch-insights` (phase 2). The lineage of imported broadcasts to recordings is implemented in `add-content-library` (phase 1).
