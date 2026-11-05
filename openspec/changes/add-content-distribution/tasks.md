# Tasks

## 1. Channels and connections

- [ ] 1.1 Write failing unit tests that a channel can be configured only for supported platforms and that its API representation has no credential field; verify they fail.
- [ ] 1.2 Implement channels that reference a connection from `add-channel-connections`, with the first-stage platform allowlist (YouTube, TikTok, Instagram for publishing; Twitch for schedule); verify the 1.1 tests pass.
- [ ] 1.3 Write failing unit tests that no response, MCP tool result, log line or error message contains a credential value, using a known test credential; verify they fail.
- [ ] 1.4 Implement redaction of credentials across responses, logs and errors; verify the 1.3 tests pass.
- [ ] 1.5 Add the database migration for channels and their connection references; verify it applies to an empty PostgreSQL in an integration test.
- [ ] 1.6 Document the channel list and the first-stage platform allowlist in the server documentation; verify every documented key exists in the configuration class. The credential key is documented by `add-channel-connections`.

## 2. Posts, drafts and lifecycle

- [ ] 2.1 Write failing unit tests for the allowed status transitions and for refusing a skip such as draft to published; verify they fail.
- [ ] 2.2 Implement the post model and status transitions; verify the 2.1 tests pass.
- [ ] 2.3 Write failing unit tests that only finished assets recorded by `add-asset-upload` can be attached to a post and that a person with the editor role can create and edit a draft that is not approved, with the person recorded as author; verify they fail.
- [ ] 2.4 Implement asset attachment and draft creation and editing; verify the 2.3 tests pass.
- [ ] 2.5 Add the database migration for posts and per-channel text; verify it applies in an integration test.
- [ ] 2.6 Document the post resource and its statuses in the OpenAPI description; verify the OpenAPI document validates.

## 3. Approval and revisions

- [ ] 3.1 Write failing unit tests that only the publisher role can approve, that an editor is refused, and that approval is refused outside an interactive session; verify they fail.
- [ ] 3.2 Implement approval as a publisher operation with the interactive-session check; verify the 3.1 tests pass.
- [ ] 3.3 Write failing unit tests that a revision proposal is stored as pending with its rationale and leaves the post unchanged, and that a rejection keeps the post unchanged; verify they fail.
- [ ] 3.4 Implement revision records with accept and reject; verify the 3.3 tests pass.
- [ ] 3.5 Write failing unit tests that accepting a revision on an approved post returns it to in review; verify they fail.
- [ ] 3.6 Implement the re-entry to review; verify the 3.5 tests pass.
- [ ] 3.7 Document the revision resource and the approval operation in the OpenAPI description; verify the OpenAPI document validates and marks approval as not available to MCP clients.

## 4. Schedule dry run and calendar

- [ ] 4.1 Write failing unit tests for each dry-run check (text and hashtag limits, format limits, connection validity, collision with another post on the same channel and time) using a fake platform rule set; verify they fail.
- [ ] 4.2 Implement the dry run and run it when a post is scheduled; verify the 4.1 tests pass.
- [ ] 4.3 Write failing unit tests that the calendar lists planned and published posts per channel and shows each dry-run problem on its post; verify they fail.
- [ ] 4.4 Implement the calendar query and the calendar view data; verify the 4.3 tests pass.
- [ ] 4.5 Document the calendar and dry-run results in the OpenAPI description; verify the OpenAPI document validates.

## 5. Publication, retries and results

- [ ] 5.1 Write failing unit tests with a fake platform port and an injected clock: a successful publication stores the external identifier and URL, writes one external post record, and the dry run runs again before publication; verify they fail.
- [ ] 5.2 Implement the planning step on the server (mark due posts) and the publication job that claims due posts with a lease; verify the 5.1 tests pass.
- [ ] 5.3 Write failing unit tests that a transient failure is retried with growing delays up to the configured limit, that an ambiguous outcome is not retried automatically, and that the final failure is shown as failed with its reason; verify they fail.
- [ ] 5.4 Implement retries with recorded attempts and the failed status; verify the 5.3 tests pass.
- [ ] 5.5 Write one contract test of the publishing use of the adapter port with a fake adapter; verify it passes. Each platform change carries its own adapter contract test against recorded responses.
- [ ] 5.6 Document the retry policy keys and the failure states in the server documentation; verify every documented key exists in the configuration class.
- [ ] 5.7 Add the CronJob definition with its schedule and a concurrency setting of one to the Helm chart values; verify `helm template` renders one CronJob with that concurrency.

## 6. Recycling with lineage

- [ ] 6.1 Write failing unit tests that a new post re-using a finished asset on another channel or with new text references the original post and leaves the original unchanged; verify they fail.
- [ ] 6.2 Implement recycling with lineage; verify the 6.1 tests pass.
- [ ] 6.3 Document the lineage field in the OpenAPI description; verify the OpenAPI document validates.

## 7. Website feed (Could)

- [ ] 7.1 Write failing unit tests that the website feed returns approved and published posts with their finished assets and never drafts; verify they fail.
- [ ] 7.2 Implement the read-only feed endpoint with the consumer token check; verify the 7.1 tests pass.
- [ ] 7.3 Document the feed in the OpenAPI description; verify the OpenAPI document validates.

## 8. Pipeline view and runway forecast

- [ ] 8.1 Write failing unit tests that the pipeline groups posts per channel by stage and excludes published posts; verify they fail.
- [ ] 8.2 Implement the pipeline query and view data; verify the 8.1 tests pass.
- [ ] 8.3 Write failing unit tests with an injected clock for the runway: the first unfilled cadence slot is the empty date, a channel without cadence reports it missing, and a warning appears below the threshold with a global default of seven days and a per-channel override; verify they fail.
- [ ] 8.4 Implement the runway forecast, the threshold settings and the calendar warning; verify the 8.3 tests pass.
- [ ] 8.5 Document the pipeline, runway and threshold settings in the OpenAPI description and the server documentation; verify every documented key exists in the configuration class.

## 9. Calendar feed (ICS)

- [ ] 9.1 Write failing unit tests that the feed lists planned posts, approvals due only for publishers, and runway warnings, limited to channels the person may read; verify they fail.
- [ ] 9.2 Implement feed generation with stable event identifiers and revision markers; verify the 9.1 tests pass.
- [ ] 9.3 Write a failing test that the generated feed passes an RFC 5545 validator, then implement until it passes.
- [ ] 9.4 Write failing unit tests for feed token issuance (256 bits, only the hash stored), revocation and rotation, and for 404 on unknown, revoked and malformed tokens using a fake token store; verify they fail.
- [ ] 9.5 Implement the token handling and the read-only feed endpoint with Referrer-Policy no-referrer and rate limiting that answers 429 with Retry-After; verify the 9.4 tests pass.
- [ ] 9.6 Write failing tests that feed tokens are redacted from log lines and that write requests to the feed are refused without any change; implement them; verify they pass.
- [ ] 9.7 Document the feed and its token lifecycle in the OpenAPI description; verify the OpenAPI document validates.
- [ ] 9.8 Subscribe to a staging feed in Outlook, Google Calendar, Apple Calendar and Nextcloud and verify the planned posts appear; record the result in the code pull request description.

## 10. Performance numbers on posts (Should)

- [ ] 10.1 Write failing unit tests with a fake analytics read port and an injected clock that a published post shows its latest views and likes with their collection time, that a post without collected values shows none, and that this change starts no metric collection; verify they fail.
- [ ] 10.2 Implement the latest-values read of `add-content-analytics` in the calendar view data; verify the 10.1 tests pass.
- [ ] 10.3 Document the latest-values fields in the OpenAPI description; verify the OpenAPI document validates.

## 11. Best-time suggestions (Could)

- [ ] 11.1 Write failing unit tests that a suggested slot per channel is derived from that channel's posting-hour aggregates of `add-content-analytics`, that a channel without collected numbers gets no suggestion, and that each suggestion carries its basis; verify they fail.
- [ ] 11.2 Implement the suggestion and show it with its basis in the calendar; verify the 11.1 tests pass.
- [ ] 11.3 Document the suggestion fields in the OpenAPI description; verify the OpenAPI document validates.

## 12. Revision and recycling comparisons (Should)

- [ ] 12.1 Write failing tests that the comparison of an accepted revision shows the analytics numbers before and after the revision, and that a recycled post is compared with its original, using a fake analytics read port; verify they fail
- [ ] 12.2 Implement the revision and recycling comparisons on top of content analytics; verify the tests from 12.1 pass

## 13. Analytics: source post dimension

- [ ] 13.1 Write failing tests that analytics aggregates can be grouped by the Menura post an external post was published from, with its revisions, read from rollups; verify they fail
- [ ] 13.2 Record the originating post and revision on each published external post for the rollup job; verify the tests from the previous task pass

## 14. Integration check

- [ ] 14.1 Run the integration suite tagged `integration` on a fresh database and verify approval, dry run, job-based publication with retry and external post record, recycling, pipeline, runway, the calendar feed, latest numbers and best-time suggestions end to end with a fake platform and a fake analytics port.
- [ ] 14.2 Open the code pull request titled `feat(publish): add content distribution`.

## Implemented elsewhere

These requirements carry a "Where the hub MCP server is available" clause. Their tasks are not part of this change; `add-hub-mcp-server` (phase 3) implements them:

- Draft creation by an MCP client, revision proposals by an MCP client, the refusal of MCP approval (no approve or publish tool), and MCP reads of the calendar and the pipeline.

## Workflow follow-up

- Tick the completed boxes and archive the change in a follow-up pull request after the code pull request is merged.
