# Tasks

## 1. Read adapter

- [ ] 1.1 Read the official Twitch Helix references for the videos and clips reads, the read scopes they need and the `view_count` field; record the verified values and URLs in design.md; verify the deprecation status of `view_count` is recorded
- [ ] 1.2 Write failing unit tests with a fake Twitch read client and an injected clock: the listing returns VODs, highlights and clips with identifier, URL, title, duration, publish time and format; a repeated listing creates no second record; a clip whose broadcast is known is linked to it and one whose broadcast is unknown is imported unlinked; verify they fail for the expected reason
- [ ] 1.3 Implement the read-only Twitch client and the listing operation of the adapter; verify the tests from 1.2 pass

## 2. Removal

- [ ] 2.1 Write failing unit tests that an item reported as deleted or unavailable is marked removed with its link no longer offered and its view counts kept, and that a VOD missing from a complete listing is marked removed; verify they fail
- [ ] 2.2 Implement the removal mapping from Twitch's report to the external post removal state; verify the tests from 2.1 pass

## 3. View counts

- [ ] 3.1 Write failing unit tests that view counts of imported VODs, highlights and clips are recorded as provided by Twitch with their collection time, and that a missing count is recorded as unavailable and not as zero; verify they fail
- [ ] 3.2 Implement the view count metrics operation of the Twitch adapter; verify the tests from 3.1 pass

## 4. Permissions

- [ ] 4.1 Write a failing test that the consent request for a channel connected for import only contains read scopes and no channel update, clip creation, schedule, chat, moderation, subscription or payment scope; verify it fails
- [ ] 4.2 Implement the read-only scope declaration of the adapter; verify the test from 4.1 passes

## 5. Retention and documentation

- [ ] 5.1 Write a failing unit test that the re-sync interval configured for Twitch channels is shorter than 14 days and that the default is used when the setting is absent; verify it fails
- [ ] 5.2 Implement the interval check and its default; verify the test from 5.1 passes
- [ ] 5.3 Document in the operator guide the read-only scopes, the 14-day VOD retention and the re-sync interval; verify the steps are complete by following them on a test environment

## 6. Integration check

- [ ] 6.1 Run one read-only import against a test channel with no write requests and verify a VOD, a highlight and a clip appear as external posts with their view counts
- [ ] 6.2 Open the code pull request titled `feat(twitch): import existing VODs, highlights and clips`
