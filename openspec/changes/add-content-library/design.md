# Design

## Context

Recordings are uploaded directly by the OBS plugin under `add-recording-ingest`, which creates the recording sessions and their media in object storage; the plugin no longer hands files to the desktop app. Transcripts and local index data arrive from the desktop app in `add-local-transcription`. This change adds the central model on top: what a content item is, how items reference each other, what can be searched, and how raw media ages out. See proposal.md - Why for the motivation.

Constraints from the project context: media stays in S3-compatible object storage, metadata and the search index live in PostgreSQL, the server never proxies media bytes and never transcodes, and the cluster needs no GPU.

Finished assets are not new in this change: `add-asset-upload` (phase 2) already records each uploaded finished asset with its format, resolution, size, checksum and uploader, and its verified upload is the input here. This change adds the lineage edges and the usage status to those existing records, and registers them as library items; a finished asset is never stored twice.

## Goals / Non-Goals

**Goals:**
- One data model in which every content item has a kind, a stream reference and, where relevant, a link to the items it was cut from.
- Lineage that can be queried in both directions (from a stream to its assets, from an asset to its sources).
- Search over transcript text with filters, plus a meaning-based search that works without a GPU on the cluster.

**Non-Goals:**
- No server-side rendering, cutting or transcoding (principle 2). Timelines are produced and rendered locally.
- No proxying of media bytes through the server (principle 1).
- No web-based timeline editor; timelines are created by the desktop app or by Claude through its local MCP server (`add-local-editing-mcp`).
- No publication to external platforms here; publication records are owned by `add-content-distribution` and only read for usage status.

## Decisions

**Relational model in PostgreSQL.** Content items, clip ranges, timeline documents and lineage edges are rows in PostgreSQL tables. Lineage is a separate edge table (asset to timeline, timeline to clip, clip to recording), so that "all assets made from stream X" is an indexed query.
- Rejected: storing lineage as a JSON array inside the asset record. It cannot answer "all assets from a stream" without scanning every asset.

**Timelines stored as validated documents.** A timeline is one JSON document in PostgreSQL, validated against the timeline structure on upload and returned unchanged. Timelines are small (a list of ranges, overlays and captions), so a document column keeps them queryable and avoids an extra object-storage round trip.
- Rejected: storing timelines as files in object storage. Files cannot be queried for reuse or validated centrally without downloading them.

**Clip status.** A clip has status `active` (the default) or `suggested`. A suggested clip is listed and filterable as suggested. The spec does not define how a suggestion becomes active; this change keeps the status as stored and leaves the acceptance workflow to the web library UI.
- Rejected: a separate table for suggestions. It duplicates the clip model and complicates the "clips" listing.

**Full-text search with PostgreSQL.** Transcript segments are indexed with PostgreSQL full-text search (a generated text-search column with a GIN index) and filtered with ordinary SQL predicates on stream, date, tags, markers and usage status. This keeps search inside the database that already holds the metadata (principle 1), with one backup and one operational surface.
- Rejected: a dedicated search engine. It adds a second store to keep in sync for a corpus of stream transcripts that PostgreSQL handles comfortably.

**Semantic search with stored vectors.** Each transcript segment gets an embedding stored in the same PostgreSQL database with a vector index. Embeddings are computed on the server by a small CPU-only model, so the cluster stays free of GPUs and the search works on every PC.
- Rejected: computing embeddings on the desktop and uploading them. Desktops may be offline and the transcription change does not carry embeddings; this decision is revisited under Open Questions.
- Rejected: a separate vector database. Same reasoning as for full-text search.

**Retention by lifecycle policy.** A retention job reads a configured policy per stream and moves raw recording media to a cold storage class through the object store's lifecycle mechanism, then records the new media state on the recording. Deletion of raw media sets the media state to removed and keeps the recording row, so lineage and clip ranges remain valid.
- Rejected: cascading deletes from recordings. A deleted recording would take its clips, transcripts and assets with it, which the requirements forbid.
- Rejected: per-recording manual actions only. The retention requirement describes a policy, and manual action does not scale across streams.

**Ownership separate from edit rights.** Every content item stores one owner and a list of contributors. Ownership only records who made the item and drives the contributor filter; whether a person may change an item is decided by their role, so an owner whose role is viewer cannot edit. Team members who may write content are those whose role permits content writes; viewers stay read-only.
- Rejected: owner-only editing. It would block editors from fixing content made by others, which the team workflow needs.

**Broadcast lineage by time-span overlap.** An imported Twitch broadcast is linked to a recording of the same channel when their spans overlap by more than a configured share. Two or more candidates produce no link and ask a person to choose. Ambiguous or low-overlap matches stay unlinked rather than being guessed.
- Rejected: linking by title or by name. Titles are edited on the platform and are not stable.

**Chapter marks stored on the timeline.** Chapter marks are part of the timeline document, validated on upload (ascending, first mark at 00:00, at least ten seconds apart). Chapter lines are built from them when the YouTube description of a post is prepared, so a later edit of the timeline changes the description only for posts that are not yet published.
- Rejected: storing a hand-typed chapter list per post. It duplicates the timeline and drifts from the cut.

**Audit for deletions.** Every deletion writes an audit entry with the actor, the item, the time and the channel (web, desktop, OBS plugin or MCP). The audit mechanism itself is defined by `add-access-control`; this change only emits the entries.

## Risks / Trade-offs

- [Risk] Search slows down as transcripts grow → Mitigation: GIN index on the generated search column, and a load check with a year of stream transcripts in the integration suite.
- [Risk] A finished asset is registered before the timeline it cites exists → Mitigation: the registration validates that all referenced timelines, clips and recordings exist and rejects the asset otherwise.
- [Risk] Retention moves media that a timeline still needs for re-cutting → Mitigation: the policy moves media only; clips and timelines stay valid, and re-cutting a cold recording needs the restore flow listed under Open Questions before the policy is enabled.
- [Risk] Embeddings on CPU make indexing slow for long streams → Mitigation: embeddings are created asynchronously after a transcript is attached; full-text search works immediately, so the slower path never blocks a search.
- [Risk] Owner and contributor lists drift from the people who actually worked on an item → Mitigation: contributors are added explicitly by an owner or editor; no automatic inference from edits.
- [Risk] A time-span match links the wrong recording → Mitigation: ambiguous or low-overlap matches are not linked; a person chooses among candidates.
- [Trade-off] Timelines as documents trade schema-level querying for simplicity; queries use lineage edges and clip ranges, not timeline internals.

## Migration Plan

This is the first content-library release, so there is no existing data to migrate. Schema changes ship as forward-only migrations with the server release. Rollback is done by deploying the previous server release and leaving the new tables in place; no content is removed by rollback. The retention policy is disabled by default and enabled per stream after the restore flow is available.

## Open Questions

- Should the desktop app supply embeddings with its transcripts instead of the server computing them? The specs read the same either way; the choice changes the desktop and server split only.
- Chapter line format (time and title layout) and the minimum chapter length are confirmed against the YouTube description rules during the implementation tasks; the spec states the ten-second minimum, so a platform change would only alter the line format.
- How is a cold recording restored for re-cutting, and who may trigger it? Needed before the retention policy is enabled for any stream; does not change the requirements in this change.
