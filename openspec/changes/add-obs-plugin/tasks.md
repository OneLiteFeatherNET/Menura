# Tasks

## 1. Sign-in and token storage

- [ ] 1.1 Write failing tests with a fake authorization server and a fake keychain that the sign-in uses PKCE with a loopback redirect, stores tokens only in the keychain, and binds them to a DPoP key; verify they fail for the expected reason.
- [ ] 1.2 Implement the sign-in flow and the keychain storage with DPoP binding; verify the tests from 1.1 pass.
- [ ] 1.3 Write failing tests that a revoked token leads to a sign-in prompt and that the configuration files contain no token value; verify they fail.
- [ ] 1.4 Implement revocation handling and the token-free configuration; verify the tests from 1.3 pass.

## 2. Scene sync in OBS

- [ ] 2.1 Write failing tests with a fake OBS scene model that the first sync creates the scenes and browser sources of the variant, that a central move updates the layout, and that a source the streamer added manually is not removed; verify they fail.
- [ ] 2.2 Implement the sync of scenes and browser sources into OBS through the libobs adapter; verify the tests from 2.1 pass.
- [ ] 2.3 Write failing tests that a scene the hub no longer defines is removed only when it has no streamer-added sources; verify they fail.
- [ ] 2.4 Implement the removal rule; verify the tests from 2.3 pass.
- [ ] 2.5 Write failing tests that a token rotation writes the new URL into the browser source, that the old token is refused afterwards, and that no dialog or dock shows the token value; verify they fail.
- [ ] 2.6 Implement the token rotation and the hidden token handling; verify the tests from 2.5 pass.

## 3. Personal profile and live updates

- [ ] 3.1 Write failing tests that a change to the signed-in person's profile and a central brand kit change re-sync the scenes of their variant, and that a token the person does not override follows a central change; verify they fail.
- [ ] 3.2 Implement the personal profile sync and the re-sync on profile and brand kit changes; verify the tests from 3.1 pass.
- [ ] 3.3 Write failing tests with a fake event source that a theme change is applied without restart, and that after a reconnect the plugin reads the current state and applies the missed change; verify they fail.
- [ ] 3.4 Implement the plugin's own SSE subscription, live application and reconnect resync; verify the tests from 3.3 pass.

## 4. Hook relay and privacy filter

- [ ] 4.1 Write the conformance test that loads the privacy fixtures from `add-agent-activity-feed` (task 1.3) and verify the test fails until the filter exists.
- [ ] 4.2 Implement the privacy filter (allowlist, field removal, fixed categories, fail-closed on missing allowlist); verify the conformance test passes.
- [ ] 4.3 Write failing tests that events from the local IPC endpoint are filtered before forwarding, that an event outside the allowlist is dropped, and that forwarding uses the plugin's DPoP-bound token; verify they fail.
- [ ] 4.4 Implement the local IPC endpoint with owner-only permissions on each OS and the forwarding with the plugin token; verify the tests from 4.3 pass.
- [ ] 4.5 Write failing tests that the kill switch drops every event while active and that the dock shows the switch state; verify they fail.
- [ ] 4.6 Implement the kill switch in the dock; verify the tests from 4.5 pass.
- [ ] 4.7 Write an integration check (tagged `integration`) that the plugin opens no listening network socket while running; verify it passes on each platform's runner.

## 5. Offline operation

- [ ] 5.1 Write failing tests that the last synced scenes, theme and profile are stored locally and loaded when the hub is unreachable, and that the plugin reports the offline state; verify they fail.
- [ ] 5.2 Implement the local store of the last sync and the offline state; verify the tests from 5.1 pass.
- [ ] 5.3 Write failing tests that the sync resumes when the network returns; verify they fail.
- [ ] 5.4 Implement the resume; verify the tests from 5.3 pass.

## 6. Installation, updates and first start

- [ ] 6.1 Write failing tests that an update package with an invalid signature is refused and the installed version kept; verify they fail.
- [ ] 6.2 Implement the signature check for updates; verify the tests from 6.1 pass.
- [ ] 6.3 Write failing tests that the first-start wizard asks for sign-in and then shows the sync and hook state; verify they fail.
- [ ] 6.4 Implement the first-start wizard; verify the tests from 6.3 pass.
- [ ] 6.5 Build the signed installers for Windows, macOS and Linux and verify that each installs, loads in OBS and updates to a newer signed build (recorded in the pull request).
- [ ] 6.6 Write the streamer setup guide in the web app help and verify it links the installer and the kill switch instructions.

## 7. Secrets

- [ ] 7.1 Write failing tests that logs and crash reports of a failed sync that carries a scene token contain no token value; verify they fail.
- [ ] 7.2 Implement token redaction in logs and crash reports; verify the tests from 7.1 pass.

## 8. Integration checks

- [ ] 8.1 Run the plugin against a test hub in OBS on one PC per platform: sign in, sync a variant, change a template centrally and check the update appears without restart, and run one hook event through the relay; record the results in the pull request.
- [ ] 8.2 Run the plugin test suite with the project's heavy-job lock and verify it passes.

## 9. Delivery

- [ ] 9.1 Open the code pull request titled `feat(obs): add obs plugin`.
