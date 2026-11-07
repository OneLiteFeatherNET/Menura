# Tasks

Requirements that need the brand kit, the render bundle consumer or the join host are built as stated in design.md. The render bundle is consumed by the external renderer; its contract is tested here with the bundle fixture, not with a renderer.

## 1. Series catalogue

- [ ] 1.1 Write failing tests with an in-memory series repository that a series with all fields is stored and returned with an opaque identifier, that a raw hex colour and an unknown audience are refused with Problem Details, and that a viewer's change answers 403 while an invisible series answers 404; verify they fail for the expected reason
- [ ] 1.2 Implement the series model, the catalogue use cases and the REST endpoints for create, change and read; verify the tests from 1.1 pass

## 2. Badge contrast

- [ ] 2.1 Write failing unit tests with a fixed brand-kit token fake that a pair at 4.5:1 is accepted, a pair at 3.2:1 is refused naming both tokens and the ratio, and a colour change that breaks the pair is refused with the stored colour unchanged; verify they fail
- [ ] 2.2 Implement the WCAG relative-luminance contrast check and call it on save and on colour change; verify the tests from 2.1 pass

## 3. Assignment and episode numbers

- [ ] 3.1 Write failing tests that an imported post with one series hashtag is assigned, that a post with two series hashtags is flagged and not assigned, that a manual reassignment and a "no series" mark are recorded, and that a second concurrent assignment is refused; verify they fail
- [ ] 3.2 Implement the hashtag matcher, the assignment rules and the import hook; verify the tests from 3.1 pass
- [ ] 3.3 Write failing tests that the first assignment takes the next number of the series, that reassignment keeps the number, that a removed post does not free its number, and that "no series" posts have no number; verify they fail
- [ ] 3.4 Implement the per-series counter updated in the same transaction as the assignment; verify the tests from 3.3 pass

## 4. Coverage and audience warning

- [ ] 4.1 Write failing tests with an injected clock and in-memory posts that coverage is 90 % at the threshold and not flagged, 80 % below it and flagged, that unpublished drafts are not counted, and that a week without published shorts is not flagged; verify they fail
- [ ] 4.2 Implement the weekly coverage computation per channel and the flag at below 90 %; verify the tests from 4.1 pass
- [ ] 4.3 Write failing tests that the audience warning appears without an active players series, does not appear with one, and ignores an inactive players series; verify they fail
- [ ] 4.4 Implement the audience warning in the series overview read; verify the tests from 4.3 pass

## 5. Render bundle

- [ ] 5.1 Write failing contract tests against a bundle fixture that the bundle contains exactly the active series with badge text, colour token, opening formula and hashtag, that a channel without a join host has no join host and no error, that a person without the renderer machine role gets 403, and that an unknown channel gets 404; verify they fail
- [ ] 5.2 Implement the bundle assembly and the endpoint with the renderer machine-client check; verify the tests from 5.1 pass
- [ ] 5.3 Write a failing test that the call-to-action text of a channel contains its join host when the join-attribution port provides one, using a fake port; verify it fails, then implement the optional port call and verify it passes

## 6. Playlist check

- [ ] 6.1 Write failing tests with an injected clock and a fake platform adapter that a post 25 hours old and missing from its playlist is flagged, that a post 23 hours old is not, that a post present in its playlist is not flagged, and that an adapter without playlist membership sets no flag and shows "not checked"; verify they fail
- [ ] 6.2 Implement the playlist check on the adapter's reported membership and the flag on the post; verify the tests from 6.1 pass

## 7. Integration checks

- [ ] 7.1 Write a failing integration test (tagged `integration`) that the series tables and the episode counter keep numbers unique under concurrent assignments to one series; verify it fails, then implement the locking and verify it passes
- [ ] 7.2 Run the integration suite with the project's heavy-job lock (`flock ~/.cache/claude-heavy.lock`) and verify the tests pass

## 8. Delivery

- [ ] 8.1 Open the code pull request titled `feat(series): add content series`

## Workflow follow-up

The proposal pull request `docs(openspec): propose add-content-series` is merged before the code pull request is opened, and the code branch starts from the main branch that contains it. A follow-up pull request that ticks the tasks in this file or archives the change (`docs(openspec): archive add-content-series`) is opened only when the maintainer asks for it.
