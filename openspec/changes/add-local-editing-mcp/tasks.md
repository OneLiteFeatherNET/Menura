# Tasks

## 1. stdio server, GPU gate and hub calls

- [ ] 1.1 Write failing tests that the editing server starts on the stdio transport and that no listening socket is opened, using a fake process environment, and verify they fail
- [ ] 1.2 Implement the stdio MCP server, and verify the tests of 1.1 pass
- [ ] 1.3 Write failing tests that editing tools refuse with the reason on a PC without a usable GPU, and verify they fail
- [ ] 1.4 Implement the GPU gate on editing tools, and verify the tests of 1.3 pass
- [ ] 1.5 Write failing tests that hub calls carry the person's device-bound token from the keychain and that a search returns only the hub's results, with a fake hub, and verify they fail
- [ ] 1.6 Implement hub calls with the keychain token and the library proxy tools, and verify the tests of 1.5 pass

## 2. Sources, cache and timelines

- [ ] 2.1 Write failing tests that a 30-second range of a long recording downloads only the covering segments and that a cached range causes no download, using a fake transport, and verify they fail
- [ ] 2.2 Implement segment-range fetching into a content-addressed cache with a size limit, and verify the tests of 2.1 pass
- [ ] 2.3 Write failing tests that timelines are created and updated as ordered source ranges with overlays, captions and a pinned layout template, and verify they fail
- [ ] 2.4 Implement timeline storage and the create and update tools, and verify the tests of 2.3 pass

## 3. Output profiles, brand and rendering

- [ ] 3.1 Write failing tests that a timeline renders with the short 9:16 profile at 1080x1920, that an unknown profile is refused, and that the render makes no hub call carrying media, using a fake renderer, and verify they fail
- [ ] 3.2 Implement profile resolution and the local render pipeline with ffmpeg, and verify the tests of 3.1 pass
- [ ] 3.3 Write failing tests that brand and template versions come from the timeline's pins, and that a personal accent colour applies only where the brand kit marks it overridable, and verify they fail
- [ ] 3.4 Implement version-pinned brand fetching and the overridable-token rule for person profiles, and verify the tests of 3.3 pass
- [ ] 3.5 Write failing tests that a dry run writes only a low-resolution preview and that a frame preview returns one frame at the requested time, and verify they fail
- [ ] 3.6 Implement dry-run proxies and frame preview, and verify the tests of 3.5 pass

## 4. Validation, upload and lineage

- [ ] 4.1 Write failing tests that a render at -10 LUFS against a -14 LUFS target is not uploaded and the failed check is reported, and that a conforming render is eligible, using measured fixtures, and verify they fail
- [ ] 4.2 Implement duration, loudness and resolution validation before upload, and verify the tests of 4.1 pass
- [ ] 4.3 Write failing tests that clips, timelines and validated renders are uploaded with lineage to their sources, using a fake library, and verify they fail
- [ ] 4.4 Implement upload of every result with lineage, and verify the tests of 4.3 pass

## 5. Editing rules and toolchain check

- [ ] 5.1 Write failing tests that editing rules are listed and readable as MCP resources and that a newer version replaces them, and verify they fail
- [ ] 5.2 Implement editing rules fetching and resource exposure, and verify the tests of 5.1 pass
- [ ] 5.3 Write failing tests that the toolchain check reports a missing font, an older ffmpeg and a missing speech model with the fix for each, and reports success when all match, using a fake manifest, and verify they fail
- [ ] 5.4 Implement the toolchain check against the hub manifest, and verify the tests of 5.3 pass
- [ ] 5.5 Write failing tests that nothing is downloaded or installed without the click on that item's consent button, and verify they fail
- [ ] 5.6 Implement per-item consent for downloads and installs, and verify the tests of 5.5 pass
- [ ] 5.7 Document the toolchain check, its report and the consent steps in the desktop README, and verify each documented step against the report texts

## 6. Editing playbook

- [ ] 6.1 Write failing tests that the hub serves the editing playbook to a client on each operating system with the rules, the toolchain manifest with the same required versions on every OS and per-OS setup instructions, and the workflow prompts for finding candidates, proposing a cut, rendering locally, uploading and drafting a post; verify they fail first
- [ ] 6.2 Implement the playbook retrieval with the per-OS manifest and the workflow prompt set; verify the 6.1 tests pass
- [ ] 6.3 Write failing tests that a toolchain entry needing a download is shown with the instruction that the download happens only after the person's one-click consent in the desktop app, and never silently; verify they fail first
- [ ] 6.4 Implement the consent instruction in the playbook output; verify the 6.3 tests pass

## 7. Cut proposal, confirmation and unattended render

- [ ] 7.1 Write failing tests for the cut state machine proposed → confirmed → rendering → validated → uploaded, where an adjustment returns to proposed and no render starts before confirmation, and verify they fail
- [ ] 7.2 Implement the state machine and the proposal tool producing timeline and low-resolution preview, and verify the tests of 7.1 pass
- [ ] 7.3 Write failing tests that a confirmed and adjusted cut renders, validates and uploads with no further prompt, using fakes for renderer and library, and verify they fail
- [ ] 7.4 Implement the confirm and adjust tools and the unattended render-validate-upload run, and verify the tests of 7.3 pass
- [ ] 7.5 Write failing tests that the desktop app claims a queued cut-proposal job and reports its proposal under the lease, using a fake queue, and verify they fail
- [ ] 7.6 Implement cut-proposal job claims, and verify the tests of 7.5 pass

## 8. Integration

- [ ] 8.1 Run the integration tests tagged `integration` with a test hub and a short fixture recording, and verify a proposed cut is confirmed, rendered at 1080x1920, validated and uploaded with lineage
- [ ] 8.2 Open the code pull request titled `feat(desktop): add local editing mcp`
