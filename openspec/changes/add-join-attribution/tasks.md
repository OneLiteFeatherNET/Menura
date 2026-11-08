# Tasks

The proxy that sends the events and asks the in-game question is outside this change (server repository). The tests here use a fake proxy client and fixed event ids; the join domain is the fixed example `onelitefeather.net`.

## 1. Join domain setting

- [ ] 1.1 Write failing unit tests with an in-memory settings store that an admin's join domain is stored for the installation, that an upper-case domain and a value that is not a host name are refused with Problem Details and store nothing, and that a second domain replaces the first; verify they fail for the expected reason
- [ ] 1.2 Implement the join domain setting and its lower-case host-name check; verify the tests from 1.1 pass
- [ ] 1.3 Write failing tests that a person without the admin role answers 403 on the join domain setting; verify they fail
- [ ] 1.4 Implement the admin role check on the setting; verify the tests from 1.3 pass

## 2. Join event ingestion

- [ ] 2.1 Write failing tests with a fake clock and an in-memory event store that an accepted event answers 201, a repeated event id answers 200 without a second count, and the same id with a different first-join flag or timestamp answers 409; verify they fail
- [ ] 2.2 Implement the event model, the idempotent insert by event id and the endpoint; verify the tests from 2.1 pass
- [ ] 2.3 Write failing tests that a payload with a `uuid`, `username` or `client_address` field is refused with a Problem Details error that does not repeat the value, that nothing is stored, and that the refused value appears in no log record captured by a test appender; verify they fail
- [ ] 2.4 Implement the field check at the boundary and the log redaction for refused payloads; verify the tests from 2.3 pass
- [ ] 2.5 Write failing tests that a call without a credential answers 401, a call with a person token answers 403, and a call over the rate limit answers 429 with Retry-After; verify they fail
- [ ] 2.6 Implement the machine-client check, the constant-time credential comparison and the rate-limit configuration for the proxy client in Envoy Gateway; verify the tests from 2.5 pass

## 3. Source answers

- [ ] 3.1 Write failing unit tests with a fake clock and an in-memory store that an answer two hours after its join is stored and answers 201, that an answer 25 hours after the join answers 422 and stores nothing, that the value `reddit` answers 422, that a payload with an extra `text` field answers 422 and its text appears in no captured log record, that an identical repeated answer answers 200 without a second row, that a different second answer answers 409 and keeps the first, and that an answer for an unknown event id answers 404; verify they fail
- [ ] 3.2 Implement the answer model with the six fixed choices, the 24-hour window measured from the join timestamp, the idempotent insert and the field check; verify the tests from 3.1 pass

## 4. Measured and stated figures

- [ ] 4.1 Write failing unit tests with a fake clock that daily and weekly totals equal the sums of the accepted first joins, that five joins of which three are `youtube`, one `friends` and one unanswered show unanswered 1 and an answer rate of 80 %, and that a join with a false first-join flag is not counted; verify they fail
- [ ] 4.2 Implement the totals, the per-source counts, the derived unanswered count and the answer rate; verify the tests from 4.1 pass
- [ ] 4.3 Write failing tests that every per-platform figure carries the label "stated by players", that it shows the report's answer rate, and that no per-platform figure is labelled as measured by a platform; verify they fail
- [ ] 4.4 Implement the labels and the answer rate next to the stated-source figures; verify the tests from 4.3 pass

## 5. Post attribution as estimate

- [ ] 5.1 Write failing unit tests with a fake clock for the post attribution across all channels: three posts published 10, 20 and 30 hours before an unanswered join split 1/3 each; a post 80 hours before receives nothing; an installation window of 24 hours excludes a post 30 hours before; a post 2 hours after the join receives nothing; the overlapping-window case gives post A 1.5 and post B 0.5; every attribution is labelled as an estimate; verify they fail
- [ ] 5.2 Implement the window selection with the installation-wide setting (default 72 hours), the even split and the estimate label; verify the tests from 5.1 pass
- [ ] 5.3 Write failing unit tests for the platform restriction: a join answered `youtube` gives the whole join to a YouTube post in the window and nothing to a TikTok post; a join answered `instagram` with only a TikTok post in the window attributes no post and counts for `instagram`; a join answered `friends` uses the all-posts split; verify they fail
- [ ] 5.4 Implement the platform restriction on the window selection; verify the tests from 5.3 pass
- [ ] 5.5 Write failing unit tests that the series sums equal the sum of the post estimates, that posts without a series count under "no series", and that an invisible post answers 404; verify they fail
- [ ] 5.6 Implement the series attribution read and the 404 rule; verify the tests from 5.5 pass

## 6. Join metrics in analytics

- [ ] 6.1 Write a failing unit test with a fake analytics store that an accepted first join writes a join count point for its day and stated source, that an unanswered join is derived and not stored, and that a join visible in the daily rollup within 60 seconds of acceptance is read with a fake clock advanced by 59 seconds and no wait; verify it fails
- [ ] 6.2 Implement the point write and the in-transaction increment of the daily join rollup on acceptance of an event or an answer; verify the test from 6.1 passes
- [ ] 6.3 Write a failing integration test (tagged `integration`) that the rollup equals the accepted first joins of a day after a correction rebuild; verify it fails, then implement the rebuild step for join points and verify it passes

## 7. Call-to-action text

- [ ] 7.1 Write failing unit tests that the German text reads "Spiel mit uns: <domain>" and the English text reads "Play with us: <domain>", that without a join domain no text is provided and no error is raised, and that a changed domain changes the texts; verify they fail
- [ ] 7.2 Implement the call-to-action texts and the port used by the render bundle of `add-content-series`; verify the tests from 7.1 pass

## 8. Integration checks

- [ ] 8.1 Write failing integration tests (tagged `integration`) that two concurrent deliveries of the same event id store one event and one count, and that two concurrent answers for one join store one answer; verify they fail, then implement the unique constraints and verify they pass
- [ ] 8.2 Run the integration suite with the project's heavy-job lock (`flock ~/.cache/claude-heavy.lock`) and verify the tests pass

## 9. Delivery

- [ ] 9.1 Open the code pull request titled `feat(attribution): attribute new players to platforms and posts`

## Workflow follow-up

The proposal pull request `docs(openspec): propose add-join-attribution` is merged before the code pull request is opened, and the code branch starts from the main branch that contains it. A follow-up pull request that ticks the tasks in this file or archives the change (`docs(openspec): archive add-join-attribution`) is opened only when the maintainer asks for it. The proxy change in the server repository, with the in-game question, must be merged before the attribution has any answers to show.
