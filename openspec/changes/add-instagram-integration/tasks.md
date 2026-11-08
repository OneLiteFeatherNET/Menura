# Tasks


## 1. Media handover
- [ ] 1.1 Write failing tests that preparing a Reel issues a signed read-only link to exactly the one asset and cover with an expiry of at most one hour (injected clock), that an expired link is refused, and that no link or signature reaches logs or traces; verify they fail
- [ ] 1.2 Issue the time-limited link for the platform fetch and redact it from logs and traces; verify the tests from 1.1 pass

## 2. Adapter and capabilities

- [ ] 2.1 Write failing unit tests that the Instagram adapter reports publish and status as available, deletion as unavailable, and is registered only for the Instagram platform; verify they fail for the expected reason.
- [ ] 2.2 Implement the Instagram adapter behind the adapter port of `add-channel-connections` and its capability report; verify the 2.1 tests pass.

## 3. Account type and content rules

- [ ] 3.1 Write failing unit tests that a professional account enables publishing, a personal account does not, and that only Reels are accepted for scheduling; verify they fail.
- [ ] 3.2 Implement the account type check in the adapter's verify step and the Reels-only rule; verify the 3.1 tests pass.
- [ ] 3.3 Write failing unit tests for the dry run: caption over 2200 characters, more than 30 hashtags or 20 mentions, a video in an unsupported container, a file over 300 MB and a duration under 3 seconds or over 15 minutes are each shown on the post; verify they fail.
- [ ] 3.4 Implement the caption, format, size and duration checks; verify the 3.3 tests pass.

## 4. Allowance

- [ ] 4.1 Write failing unit tests with a fake platform client and an injected clock that the platform-reported allowance is used when it differs from the stored value, that a Reel beyond the remaining allowance is shown as a problem, and that a schedule within the allowance is accepted; verify they fail.
- [ ] 4.2 Implement the allowance read and the planner and dry-run checks; verify the 4.1 tests pass.

## 5. Two-step publication

- [ ] 5.1 Write failing unit tests with a fake platform client and an injected clock that a container is created when the Reel is due, that status is polled at most once per minute for five minutes, that a finished container is published and the post records the identifier and URL, and that a container which errors or expires before publication is prepared again within the retry limit; verify they fail.
- [ ] 5.2 Implement the container creation, bounded readiness polling, publish call and the retry rules; verify the 5.1 tests pass.
- [ ] 5.3 Write failing unit tests that a publish call without a confirmed outcome is shown as unknown and is not repeated automatically; verify they fail.
- [ ] 5.4 Implement the unknown-outcome handling; verify the 5.3 tests pass.
- [ ] 5.5 Write failing unit tests for the cover rules: a cover image overrides the frame offset, and the frame offset applies when no cover image exists; verify they fail.
- [ ] 5.6 Implement the cover selection; verify the 5.5 tests pass.

## 6. Contract tests and documentation

- [ ] 6.1 Write a contract test against recorded platform responses for container creation, status values, publish and the allowance read; verify it passes.
- [ ] 6.2 Document the Instagram setup (professional accounts, app product, scopes `instagram_business_basic`, `instagram_business_content_publish`, the login variant decision) in the operations notes; verify every scope named there appears in the adapter configuration.

## 7. Integration check

- [ ] 7.1 Run the integration suite tagged `integration` with the heavy-job lock and verify a Reel from schedule to publication, the allowance refusal and the unknown-outcome rule end to end against a fake Instagram server.
- [ ] 7.2 Open the code pull request titled `feat(instagram): add instagram integration`.

## Implemented elsewhere

- Reel insights: `add-instagram-insights` (phase 2).
