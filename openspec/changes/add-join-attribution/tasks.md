# Tasks

The proxy that sends the events, and the wildcard DNS record for the join hosts, are outside this change (server repository and operator). The tests here use a fake proxy client and fixed host names.

## 1. Join hosts

- [ ] 1.1 Write failing unit tests with an in-memory channel repository that a join host is stored for one channel, that a host held by another channel is refused with 409, that an upper-case host and a host outside the base domain are refused with Problem Details, and that a second host replaces the first; verify they fail for the expected reason
- [ ] 1.2 Implement the join host model, the uniqueness rule and the base-domain check; verify the tests from 1.1 pass
- [ ] 1.3 Write failing tests that a viewer's change answers 403 and that the host of an invisible channel answers 404; verify they fail
- [ ] 1.4 Implement the role check and the 404 rule on the join host endpoints; verify the tests from 1.3 pass

## 2. Join event ingestion

- [ ] 2.1 Write failing tests with a fake clock and an in-memory event store that an accepted event answers 201, a repeated event id answers 200 without a second count, and the same id with different content answers 409; verify they fail
- [ ] 2.2 Implement the event model, the idempotent insert by event id and the endpoint; verify the tests from 2.1 pass
- [ ] 2.3 Write failing tests that a payload with a `uuid`, `username` or `client_address` field is refused with a Problem Details error that does not repeat the value, that nothing is stored, and that the refused value appears in no log record captured by a test appender; verify they fail
- [ ] 2.4 Implement the field check at the boundary and the log redaction for refused payloads; verify the tests from 2.3 pass
- [ ] 2.5 Write failing tests that a call without a credential answers 401, a call with a person token answers 403, and a call over the rate limit answers 429 with Retry-After; verify they fail
- [ ] 2.6 Implement the machine-client check, the constant-time credential comparison and the rate-limit configuration for the proxy client in Envoy Gateway; verify the tests from 2.5 pass

## 3. Attribution

- [ ] 3.1 Write failing unit tests that host `YT.OneLiteFeather.net:25565.` normalises to the channel host, that an unknown host and the plain domain count as direct, that a malformed host is counted as direct and accepted, and that a non-first join is not counted as a new player; verify they fail
- [ ] 3.2 Implement host normalisation and the direct classification; verify the tests from 3.1 pass
- [ ] 3.3 Write failing unit tests with a fake clock that the daily and weekly first-join counts per channel and platform equal the sums of the accepted events, and that direct joins are shown on their own line; verify they fail
- [ ] 3.4 Implement the per-channel and per-platform counts and the weekly sum; verify the tests from 3.3 pass
- [ ] 3.5 Write failing unit tests for the post attribution: three posts split 1/3 each, a post 80 hours before receives nothing, a per-channel window of 24 hours excludes a post 30 hours before, a post after the join receives nothing, the overlapping-window case gives post A 1.5 and post B 0.5, and every attribution is labelled as an estimate; verify they fail
- [ ] 3.6 Implement the window selection and the even split with the estimate label; verify the tests from 3.5 pass
- [ ] 3.7 Write failing unit tests that the series sums equal the sum of the post estimates, that posts without a series count under "no series", and that an invisible post answers 404; verify they fail
- [ ] 3.8 Implement the series attribution read and the 404 rule; verify the tests from 3.7 pass

## 4. Join metrics in analytics

- [ ] 4.1 Write a failing unit test with a fake analytics store that an accepted first join writes a join count point for its channel, platform and day, that direct joins are stored under the "direct" line, and that a join visible in the daily rollup within 60 seconds of acceptance is read with a fake clock advanced by 59 seconds and no wait; verify it fails
- [ ] 4.2 Implement the point write and the in-transaction increment of the daily join rollup on acceptance; verify the test from 4.1 passes
- [ ] 4.3 Write a failing integration test (tagged `integration`) that the rollup equals the accepted first joins of a day after a correction rebuild; verify it fails, then implement the rebuild step for join points and verify it passes

## 5. Call-to-action text

- [ ] 5.1 Write failing unit tests that each channel has a call-to-action text per language containing its join host, that a channel without a join host gets no text and is reported as missing, and that a changed host changes the text; verify they fail
- [ ] 5.2 Implement the per-language call-to-action texts and the port used by the render bundle of `add-content-series`; verify the tests from 5.1 pass

## 6. Integration checks

- [ ] 6.1 Write a failing integration test (tagged `integration`) that two concurrent deliveries of the same event id store one event and one count; verify it fails, then implement the unique constraint and verify it passes
- [ ] 6.2 Run the integration suite with the project's heavy-job lock (`flock ~/.cache/claude-heavy.lock`) and verify the tests pass

## 7. Delivery

- [ ] 7.1 Open the code pull request titled `feat(attribution): attribute new players to channels and posts`

## Workflow follow-up

The proposal pull request `docs(openspec): propose add-join-attribution` is merged before the code pull request is opened, and the code branch starts from the main branch that contains it. A follow-up pull request that ticks the tasks in this file or archives the change (`docs(openspec): archive add-join-attribution`) is opened only when the maintainer asks for it. The proxy change in the server repository must be merged before the attribution has any data to show.
