# Tasks

## 1. Domain model and token protection

- [ ] 1.1 Write failing unit tests for the connection state machine (connected, needs reconnection, disconnected) with an injected clock; verify they fail before the model exists
- [ ] 1.2 Implement the connection model and its state transitions; verify the state tests pass
- [ ] 1.3 Write failing tests that a channel representation has no token field and that encrypted token storage round-trips with a test key; verify they fail first
- [ ] 1.4 Implement token encryption with the key from environment configuration and the token-free channel representation; verify the tests pass and that a missing key stops startup with a clear error

## 2. Consent flow

- [ ] 2.1 Write failing tests for the consent start and callback handling (success, refusal, state mismatch, different account on reconnect) against a fake platform consent endpoint; verify they fail first
- [ ] 2.2 Implement the authorization code flow with state and PKCE in the server and the "Connect channel" action in the web app; verify the tests pass and that no screen offers a field for a token
- [ ] 2.3 Write a failing test that the requested scopes equal the scopes the adapter declares for the enabled features; verify it fails first
- [ ] 2.4 Implement scope declaration per adapter and the missing-scope flag; verify the test passes

## 3. Refresh, health and reconnection

- [ ] 3.1 Write failing tests for refresh before expiry, rotation of the refresh token and marking a rejected refresh as needing reconnection, using a fake clock; verify they fail first
- [ ] 3.2 Implement the refresh logic and the health check job in the `jobs` component; verify the tests pass
- [ ] 3.3 Write a failing test that a publication attempt refreshes a token near expiry; verify it fails first, then implement it and verify it passes
- [ ] 3.4 Implement reconnection that keeps channel identity and history and refuses a different platform account; verify with tests

## 4. Disconnect and quota visibility

- [ ] 4.1 Write failing tests for disconnect: revocation requested where supported, tokens deleted even when revocation fails; verify they fail first
- [ ] 4.2 Implement disconnect; verify the tests pass
- [ ] 4.3 Write failing tests for quota state shown per connection and for the delay reason on a rate-limited post; verify they fail first
- [ ] 4.4 Implement quota and rate-limit state per connection and its display in the channel view; verify the tests pass

## 5. Audit status and adapter port

- [ ] 5.1 Write failing tests that an unknown or unaudited app reports the restriction "public visibility not available" through the channel's readable restrictions and that the channel view shows the restriction text; verify they fail first
- [ ] 5.2 Implement the per-channel-type audit status record and its readable restrictions; verify the tests pass
- [ ] 5.3 Write a failing contract test that every adapter exposes publish, status, delete, capabilities, listing of existing published items and metric reading, and that an unsupported feature refuses scheduling; verify it fails first
- [ ] 5.4 Write a failing test with a fake adapter that the listing returns platform identifier, URL, title, publish time and format for each published item, lists only items the platform reports, and records a metric the adapter does not expose as absent, not zero; verify it fails first
- [ ] 5.5 Implement the adapter port, the capability check, the listing operation and the metric reading; verify the contract and listing tests pass using an in-memory fake adapter

## 6. Documentation and integration check

- [ ] 6.1 Document in the operator guide how to create the platform app, record its audit status and rotate the encryption key; verify the steps are complete by following them on a test environment
- [ ] 6.2 Run the full server test suite with the `integration` tag for the token store and verify it passes
- [ ] 6.3 Open the code pull request titled `feat(publish): connect platform channels through consent`
