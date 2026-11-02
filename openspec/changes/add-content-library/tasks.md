# Tasks

## 1. Content model

- [ ] 1.1 Write failing unit tests for content item kinds with stream reference and for rejecting a clip range outside its recording; verify they fail for the expected reason.
- [ ] 1.2 Implement the content item and clip model with range validation; verify the 1.1 tests pass.
- [ ] 1.3 Write failing unit tests for timeline validation (ordered source ranges, unknown layout template rejected) and verify they fail.
- [ ] 1.4 Implement timeline validation and storage as a document; verify the 1.3 tests pass and an uploaded timeline is returned unchanged.
- [ ] 1.5 Write failing unit tests that the creator becomes owner, contributors can be added, the library filters by contributor, and a viewer cannot upload or a member without editor role cannot change another member's item; implement the ownership model and role checks; verify the tests pass.
- [ ] 1.6 Add the database migration for content items, clips, timelines and lineage edges; verify it applies to an empty PostgreSQL in an integration test.

## 2. Finished assets and lineage

- [ ] 2.1 Write failing unit tests: asset registration is rejected when a referenced timeline, clip or recording is missing, and an asset with an unverified upload is not listed; verify they fail.
- [ ] 2.2 Implement asset registration on the finished asset records of `add-asset-upload` and the lineage edges; verify the 2.1 tests pass.
- [ ] 2.3 Write failing unit tests for lineage queries by stream (exactly the matching assets, timelines and clips) and for re-cutting a clip whose lineage stays unchanged; verify they fail.
- [ ] 2.4 Implement the lineage queries; verify the 2.3 tests pass.
- [ ] 2.5 Write failing unit tests for usage status (never used, used, already published on a named channel) using a fake publication source; verify they fail.
- [ ] 2.6 Implement usage status; verify the 2.5 tests pass.
- [ ] 2.7 Write failing unit tests for suggested clips created by a person and by an MCP client, both listed with status `suggested`; implement the status; verify the tests pass.
- [ ] 2.8 Document the content item, owner, contributor, lineage and usage status fields in the OpenAPI description; verify the OpenAPI document validates and lists those fields.

## 3. Retention and deletion

- [ ] 3.1 Write failing unit tests: the retention policy selects only recordings over the configured age, and lineage still names a recording whose raw media is removed; verify they fail.
- [ ] 3.2 Implement the retention job against a fake object-store lifecycle port and record the media state on the recording; verify the 3.1 tests pass.
- [ ] 3.3 Write failing unit tests: a clip is never deleted without an explicit request, and a deletion writes one audit entry with actor, item, time and channel; verify they fail.
- [ ] 3.4 Implement explicit deletion with the audit entry; verify the 3.3 tests pass.
- [ ] 3.5 Document the retention policy keys and the deletion audit entries in the server documentation; verify every documented key exists in the configuration class.

## 4. Full-text search

- [ ] 4.1 Write failing integration tests: a word returns its segment with recording and start time, a word without a match returns an empty result, and a freshly attached transcript is searchable; verify they fail.
- [ ] 4.2 Implement transcript full-text indexing and the search query; verify the 4.1 tests pass.
- [ ] 4.3 Write failing unit tests that stream, date, tag, marker and usage status filters combine with AND; verify they fail.
- [ ] 4.4 Implement the filters; verify the 4.3 tests pass.
- [ ] 4.5 Write a failing unit test that each hit carries the start time of its speech; implement it; verify the test passes.
- [ ] 4.6 Document the search endpoint and its filters in the OpenAPI description; verify the OpenAPI document validates.

## 5. Semantic search (Should)

- [ ] 5.1 Write failing unit tests with a fake embedding port: a segment without shared words is ranked by meaning, and a stream filter restricts semantic results; verify they fail.
- [ ] 5.2 Implement segment embedding after a transcript is attached and the vector query; verify the 5.1 tests pass.
- [ ] 5.3 Document the semantic search mode in the OpenAPI description; verify the OpenAPI document validates and documents the mode parameter.

## 6. Library MCP tools

- [ ] 6.1 Write failing unit tests that a transcript range read returns the segments between two timestamps for a caller who may read the recording, and answers 404 for a caller who may not; verify they fail first
- [ ] 6.2 Implement the transcript range tool on the hub MCP server; verify the 6.1 tests pass
- [ ] 6.3 Write failing unit tests that a search through MCP returns matches from every contributor's content the caller may read, and nothing else; verify they fail first
- [ ] 6.4 Implement the library search tool across contributors within roles; verify the 6.3 tests pass
- [ ] 6.5 Write failing unit tests that clip suggestions created through MCP by an editor are stored with status `suggested`, and that a viewer is refused; verify they fail first
- [ ] 6.6 Implement the clip suggestion tool; verify the 6.5 tests pass
- [ ] 6.7 Write failing unit tests that the lineage read returns the timeline, clips and recordings of a finished asset; verify they fail first
- [ ] 6.8 Implement the lineage read tool; verify the 6.7 tests pass
- [ ] 6.9 Write failing unit tests that the short-candidates prompt for the last stream guides the client to search that stream's transcripts and to suggest up to five clips, and that the prompt and resources are listed for the caller's role; verify they fail first
- [ ] 6.10 Implement the workflow prompt and resources; verify the 6.9 tests pass
- [ ] 6.11 Document the library MCP tools and the workflow prompt in the hub MCP documentation; verify every tool name listed there exists in the tool registry test

## 7. YouTube chapters from timeline marks

- [ ] 7.1 Write failing unit tests that chapter marks are stored with the timeline and returned unchanged, and that a timeline whose marks are not ascending, do not start at 00:00 or are less than ten seconds apart is refused with the chapter problem named; verify they fail first
- [ ] 7.2 Implement chapter mark validation and storage on the timeline document; verify the 7.1 tests pass
- [ ] 7.3 Write failing unit tests with a fixed timeline that the chapter lines are one line per mark in ascending order starting at 00:00, that a timeline without marks adds no lines, and that the lines reach the YouTube upload of the post whose finished asset was made from that timeline; verify they fail first
- [ ] 7.4 Implement the chapter lines and their hand-over to the YouTube upload of `add-youtube-integration`; verify the 7.3 tests pass
- [ ] 7.5 Document the chapter mark fields in the OpenAPI description; verify the OpenAPI document validates

## 8. Lineage of imported broadcasts

- [ ] 8.1 Write failing unit tests with a fake clock: an imported broadcast whose span overlaps one recording of its channel by more than the configured share is linked; a broadcast without a matching recording stays unlinked; a span overlapping two recordings makes no link and asks a person to choose; verify they fail first
- [ ] 8.2 Implement the broadcast matching with the configured overlap share and the ambiguity rule; verify the 8.1 tests pass
- [ ] 8.3 Document the overlap share key in the server documentation; verify the key exists in the configuration class

## 9. Analytics: link metrics to recordings and add library dimensions

- [ ] 9.1 Write failing tests that an external post cut from a recording is linked in content analytics to that recording, its stream title and category and its contributor, and that aggregates can be grouped and filtered by tags or topic, source stream and contributor from rollups; verify they fail
- [ ] 9.2 Provide the recording, stream and contributor links and the library dimensions to the analytics rollup job; verify the tests from the previous task pass

## 10. Integration check

- [ ] 10.1 Run the integration suite tagged `integration` on a fresh database and verify search, lineage, usage status, retention, the library MCP tools, chapter lines and broadcast lineage work end to end.
- [ ] 10.2 Open the code pull request titled `feat(library): add content library`.

## Workflow follow-up

- Tick the completed boxes and archive the change in a follow-up pull request after the code pull request is merged.
