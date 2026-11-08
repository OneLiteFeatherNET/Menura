# Tasks

## 1. Pool model, uploader and tags

- [ ] 1.1 Write failing unit tests for pool items of kind raw and clip with stage, stable identifier and uploader, and for rejecting a clip range outside its raw item; verify they fail for the expected reason.
- [ ] 1.2 Implement the pool item and clip model with range validation; verify the 1.1 tests pass.
- [ ] 1.3 Write failing unit tests that the uploader is recorded at creation and unchanged by later edits, that free tags match `^[a-z0-9-]{2,30}$` with at most 20 per item, and that a viewer cannot change tags while an editor can; verify they fail.
- [ ] 1.4 Implement the uploader record, free and person tags and the role check; verify the 1.3 tests pass.
- [ ] 1.5 Write failing unit tests that the pool listing filters by uploader ("mine" or a named member), person tag, free tag and stage, and that the filters combine with AND; verify they fail.
- [ ] 1.6 Implement the pool listing filters; verify the 1.5 tests pass.
- [ ] 1.7 Add the database migration for pool items, clips, tags, timelines and lineage edges; verify it applies to an empty PostgreSQL in an integration test.

## 2. Raw material and transcripts

- [ ] 2.1 Write failing unit tests that a verified multipart upload registers a raw item owned by the uploader with stream title and date, that an unverified upload registers nothing, and that a viewer cannot register raw material; verify they fail.
- [ ] 2.2 Implement raw item registration on the verified upload of `add-asset-upload`; verify the 2.1 tests pass.
- [ ] 2.3 Write failing unit tests that a WebVTT file and an SRT file each parse into segments with start, end and text, that a malformed cue is refused with a Problem Details response naming its line, and that a file above the configured transcript size limit (default 10 MB) is refused; verify they fail.
- [ ] 2.4 Implement the WebVTT and SRT parsers, the transcript size limit and the Problem Details refusal; verify the 2.3 tests pass.
- [ ] 2.5 Write failing unit tests that replacing a transcript replaces all its segments and writes one audit entry with actor, item, time and channel, and that a raw item without transcript yields no segments and starts no transcription; verify they fail.
- [ ] 2.6 Implement transcript replacement and its audit entry; verify the 2.5 tests pass.
- [ ] 2.7 Write failing unit tests (Should) that JSON segments with speaker store the speaker on each segment and that WebVTT segments store none; verify they fail.
- [ ] 2.8 Implement the JSON segment format with speaker; verify the 2.7 tests pass.
- [ ] 2.9 Where add-recording-ingest is available: write a failing unit test that a recording uploaded by the OBS plugin is registered as a raw item, implement the registration, and verify the test passes.

## 3. Timelines, finished assets and lineage

- [ ] 3.1 Write failing unit tests for timeline validation (ordered source ranges; unknown layout template rejected where add-brand-kit is available) and that an uploaded timeline is returned unchanged; verify they fail.
- [ ] 3.2 Implement timeline validation and storage as a document; verify the 3.1 tests pass.
- [ ] 3.3 Write failing unit tests that asset registration is rejected when a referenced timeline, clip or raw item is missing, that an asset with an unverified upload is not listed, and that registration moves the clips of its timeline to stage rendered; verify they fail.
- [ ] 3.4 Implement asset registration on the finished asset records of `add-asset-upload`, the lineage edges and the stage change; verify the 3.3 tests pass.
- [ ] 3.5 Write failing unit tests for lineage queries by raw item (exactly the matching assets, timelines and clips) and for re-cutting a clip whose lineage stays unchanged; verify they fail.
- [ ] 3.6 Implement the lineage queries; verify the 3.5 tests pass.
- [ ] 3.7 Write failing unit tests for usage status (never used, used; where add-content-distribution is available, also already published on a named channel) using a fake publication source; verify they fail.
- [ ] 3.8 Implement usage status; verify the 3.7 tests pass.
- [ ] 3.9 Write failing unit tests (Could) that a suggested clip created by a person is stored and listed with status `suggested`; verify they fail.
- [ ] 3.10 Implement the suggested status; verify the 3.9 tests pass.
- [ ] 3.11 Document the pool item, uploader, tag, lineage and usage status fields in the OpenAPI description; verify the OpenAPI document validates and lists those fields.

## 4. Retention and deletion

- [ ] 4.1 Write failing unit tests: the retention policy selects only raw items over the configured age, and lineage still names a raw item whose raw media is removed; verify they fail.
- [ ] 4.2 Implement the retention job against a fake object-store lifecycle port and record the media state on the raw item; verify the 4.1 tests pass.
- [ ] 4.3 Write failing unit tests: a clip is never deleted without an explicit request, and a deletion writes one audit entry with actor, item, time and channel; verify they fail.
- [ ] 4.4 Implement explicit deletion with the audit entry; verify the 4.3 tests pass.
- [ ] 4.5 Document the retention policy keys and the deletion audit entries in the server documentation; verify every documented key exists in the configuration class.

## 5. Search

- [ ] 5.1 Write failing integration tests: a word returns its segment with raw item and start time, a word without a match returns an empty result, and a freshly uploaded transcript is searchable; verify they fail.
- [ ] 5.2 Implement transcript full-text indexing and the search query; verify the 5.1 tests pass.
- [ ] 5.3 Write failing unit tests that uploader, person, tag, stage, date and usage status filters combine with AND, and that a stage filter keeps only hits from raw items with a clip at that stage; verify they fail.
- [ ] 5.4 Implement the filters; verify the 5.3 tests pass.
- [ ] 5.5 Write a failing unit test that each hit carries the start time of its speech; implement it; verify the test passes.
- [ ] 5.6 Document the search endpoint and its filters in the OpenAPI description; verify the OpenAPI document validates.
- [ ] 5.7 Write failing unit tests (Should) with a fake embedding port: a segment without shared words is ranked by meaning, and an uploader filter restricts semantic results; verify they fail.
- [ ] 5.8 Implement segment embedding after a transcript is uploaded and the vector query; verify the 5.7 tests pass.
- [ ] 5.9 Document the semantic search mode in the OpenAPI description; verify the OpenAPI document validates and documents the mode parameter.

## 6. Library MCP tools (Where add-hub-mcp-server is available)

- [ ] 6.1 Write failing unit tests that a transcript range read returns the segments between two timestamps for a caller who may read the raw item, and answers 404 for a caller who may not; verify they fail first.
- [ ] 6.2 Implement the transcript range tool on the hub MCP server; verify the 6.1 tests pass.
- [ ] 6.3 Write failing unit tests that a search through MCP returns matches from every uploader's content the caller may read, and nothing else; verify they fail first.
- [ ] 6.4 Implement the library search tool across uploaders within roles; verify the 6.3 tests pass.
- [ ] 6.5 Write failing unit tests that clip suggestions created through MCP by an editor are stored with status `suggested`, and that a viewer is refused; verify they fail first.
- [ ] 6.6 Implement the clip suggestion tool; verify the 6.5 tests pass.
- [ ] 6.7 Write failing unit tests that the lineage read returns the timeline, clips and raw items of a finished asset; verify they fail first.
- [ ] 6.8 Implement the lineage read tool; verify the 6.7 tests pass.
- [ ] 6.9 Write failing unit tests that the short-candidates prompt for the last stream guides the client to search that stream's transcripts and to suggest up to five clips, and that the prompt and resources are listed for the caller's role; verify they fail first.
- [ ] 6.10 Implement the workflow prompt and resources; verify the 6.9 tests pass.
- [ ] 6.11 Document the library MCP tools and the workflow prompt in the hub MCP documentation; verify every tool name listed there exists in the tool registry test.

## 7. YouTube chapters from timeline marks

- [ ] 7.1 Write failing unit tests that chapter marks are stored with the timeline and returned unchanged, and that a timeline whose marks are not ascending, do not start at 00:00 or are less than ten seconds apart is refused with the chapter problem named; verify they fail first.
- [ ] 7.2 Implement chapter mark validation and storage on the timeline document; verify the 7.1 tests pass.
- [ ] 7.3 Where add-youtube-integration is available, write failing unit tests with a fixed timeline that the chapter lines are one line per mark in ascending order starting at 00:00, that a timeline without marks adds no lines, and that the lines reach the YouTube upload of `add-youtube-integration` for the post whose finished asset was made from that timeline; verify they fail first.
- [ ] 7.4 Where add-youtube-integration is available, implement the chapter lines and their hand-over to the YouTube upload of `add-youtube-integration`; verify the 7.3 tests pass.
- [ ] 7.5 Document the chapter mark fields in the OpenAPI description; verify the OpenAPI document validates.

## 8. Lineage of imported broadcasts (Where add-twitch-insights is available)

- [ ] 8.1 Write failing unit tests with a fake clock: an imported broadcast whose span overlaps one raw item of its channel by more than the configured share is linked; a broadcast without a matching raw item stays unlinked; a span overlapping two raw items makes no link and asks a person to choose; verify they fail first.
- [ ] 8.2 Implement the broadcast matching with the configured overlap share and the ambiguity rule; verify the 8.1 tests pass.
- [ ] 8.3 Document the overlap share key in the server documentation; verify the key exists in the configuration class.

## 9. Analytics: link metrics to raw items and add library dimensions (Where add-content-analytics is available)

- [ ] 9.1 Write failing tests that an external post cut from a raw item is linked in content analytics to that raw item, its stream title and category and its uploader, and that aggregates can be grouped and filtered by tags, source stream and uploader from rollups; verify they fail.
- [ ] 9.2 Provide the raw item, stream and uploader links and the library dimensions to the analytics rollup job; verify the tests from the previous task pass.

## 10. Integration check

- [ ] 10.1 Run the integration suite tagged `integration` on a fresh database and verify pool filters, transcript upload, search, lineage, retention and the library MCP tools (where `add-hub-mcp-server` is available) work end to end, and check usage status (where `add-content-distribution` is available) and chapter lines (where `add-youtube-integration` is available).
- [ ] 10.2 Open the code pull request titled `feat(library): add the pool of clips, raw material and transcripts`.

## Workflow follow-up

- Tick the completed boxes and archive the change in a follow-up pull request after the code pull request is merged.
