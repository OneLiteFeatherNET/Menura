# Tasks

## 1. Record model

- [ ] 1.1 Write failing unit tests that an external post requires channel, platform, external identifier, URL, title, publish time (UTC) and format, and that a second record with the same channel and external identifier is refused; use an injected clock; verify they fail for the expected reason
- [ ] 1.2 Implement the external post model and its unique key; verify the tests from 1.1 pass
- [ ] 1.3 Write a failing unit test that the record holds metadata and the platform URL only and that a record created by a Menura publication, with the same fields, is read the same way as an imported one; verify it fails
- [ ] 1.4 Implement the record fields and the shared read path; verify the test from 1.3 passes

## 2. Import

- [ ] 2.1 Write failing unit tests with a fake adapter and an injected clock: the import records the published items of every connected channel with all fields; a channel needing reconnection is skipped and shown as waiting; running the import twice creates no duplicate; verify they fail
- [ ] 2.2 Implement the import on the listing operation of the adapter port from `add-channel-connections`; verify the tests from 2.1 pass

## 3. Re-sync and removal

- [ ] 3.1 Write failing unit tests with a fake adapter and a fake clock: a new item is added; a changed title is updated without a second record; a failed listing leaves records unchanged and records the failure; an item missing from a complete listing is marked removed with the time seen, keeps its metric points and is not deleted; a removed item listed again becomes live; a listing that fails part-way marks nothing; verify they fail
- [ ] 3.2 Implement the comparison of a complete listing with stored records, the removal state and the failure record; verify the tests from 3.1 pass
- [ ] 3.3 Write a failing integration test (tagged `integration`, PostgreSQL via Testcontainers) that the unique key holds under concurrent re-sync runs and that a re-sync of an unchanged channel writes no rows; verify it fails
- [ ] 3.4 Implement the periodic re-sync job in the `jobs` component with a configurable interval per connection and the skip for channels needing reconnection; verify the test from 3.3 passes

## 4. Channel overview

- [ ] 4.1 Write failing tests that the channel overview lists title, format, publish time, link and status for each external post, shows removed posts without a live link, lets a viewer read it without edit controls, and answers 404 as Problem Details for a channel the caller may not see; verify they fail
- [ ] 4.2 Implement the REST read endpoint and the channel overview view with the last successful re-sync time; verify the tests from 4.1 pass

## 5. Documentation and integration check

- [ ] 5.1 Document in the operator guide the re-sync interval setting, the meaning of "removed" and the rule that an incomplete listing marks nothing; verify the steps are complete by following them on a test environment
- [ ] 5.2 Run the integration tests from 3.3 with the project's heavy-job lock (`flock ~/.cache/claude-heavy.lock`) and verify they pass
- [ ] 5.3 Open the code pull request titled `feat(publish): import existing posts of connected channels`
