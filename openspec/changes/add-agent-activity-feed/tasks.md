# Tasks

## 1. Event schema and privacy conformance

- [ ] 1.1 Write failing tests that the event schema accepts lifecycle type, session identifier, agent kind, tool category, subagent count and timestamp, and rejects any event with a prompt, file content or path field; verify they fail for the expected reason.
- [ ] 1.2 Implement the event schema with the fixed lifecycle and tool category lists; verify the tests from 1.1 pass.
- [ ] 1.3 Write the privacy conformance fixtures (allowlisted and non-allowlisted sessions, prompt text, file paths, missing allowlist, kill switch on and off) as shared test data that the OBS plugin relay must pass, and verify the fixtures are loaded by the OBS plugin test suite (see `add-obs-plugin`).

## 2. Claude Code hook

- [ ] 2.1 Write failing tests with a fake local IPC endpoint that the hook reports session start, working, tool category, waiting, subagent spawned and finished, and done in order, and that a missing or refusing endpoint drops the event without blocking; verify they fail.
- [ ] 2.2 Implement the hook as a reporter that writes schema-valid events to the local IPC endpoint with a short time budget; verify the tests from 2.1 pass.
- [ ] 2.3 Write failing tests that the hook never reads or sends the prompt text, tool arguments or file contents, and that tool names are mapped to categories before sending; verify they fail.
- [ ] 2.4 Implement the tool category mapping in the hook; verify the tests from 2.3 pass.
- [ ] 2.5 Document the hook installation and the kill switch for streamers in the OBS plugin help text (see `add-obs-plugin`) and verify the link to that help text exists in the hook installation output.

## 3. Hub ingestion and presence

- [ ] 3.1 Write failing tests that events forwarded with a valid OBS plugin device token are accepted, that events from a revoked token are refused with 401 Problem Details, and that a request from a hook without the plugin is refused with 401; verify they fail.
- [ ] 3.2 Implement event ingestion accepting only the plugin's device-bound token; verify the tests from 3.1 pass.
- [ ] 3.3 Write failing tests with an injected clock that presence lists active sessions with state and subagent count, is per person, times out silent sessions, and carries the agent kind and optional label; verify they fail.
- [ ] 3.4 Implement the per-person presence aggregation with stale timeouts; verify the tests from 3.3 pass.
- [ ] 3.5 Write failing tests that a scene feed publishes presence on `agent.activity` and sends the current presence on connect; verify they fail.
- [ ] 3.6 Implement the `agent.activity` topic on the scene feed; verify the tests from 3.5 pass.

## 4. Claude presence overlay module

- [ ] 4.1 Write failing mount tests that the module shows one icon per session and per subagent, shows no icon without sessions, and animates working, waiting and done; verify they fail.
- [ ] 4.2 Implement the Claude presence module with its manifest subscribing only to `agent.activity`; verify the tests from 4.1 pass.
- [ ] 4.3 Write failing tests that the icon colours come from the overlay theme tokens and that sessions of another agent kind show a different icon; verify they fail.
- [ ] 4.4 Implement brand theming and the agent kind icons; verify the tests from 4.3 pass.
- [ ] 4.5 (Could) Write failing tests for the hover label and implement it; verify the tests pass, or skip this task and note the skip in the pull request.

## 5. Integration checks

- [ ] 5.1 Run an integration test (tagged `integration`) that a hook event from an allowlisted session, sent through the OBS plugin, reaches a connected scene feed as presence within the stale timeout, and that a session outside the allowlist never appears; verify it passes.
- [ ] 5.2 Run the hook, hub and overlay test suites with the project's heavy-job lock and verify they pass.

## 6. Delivery

- [ ] 6.1 Open the code pull request titled `feat(activity): add agent activity feed`.
