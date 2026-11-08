# Design

## Context

Raw material (stream recordings) is uploaded from the web app by the presigned multipart flow of `add-asset-upload`, so the server never handles media bytes. Transcripts are produced on the editing PC outside Menura and uploaded together with the material as a subtitle file; the hub parses, stores and searches them and never transcribes. Where `add-recording-ingest` is available, recordings made by the OBS plugin also arrive as raw items; where `add-local-transcription` is available, its automated transcripts are uploaded the same way. This change adds the central pool on top: what an item is, who uploaded it and who appears in it, how items reference each other, what can be searched, and how raw media ages out. See proposal.md - Why for the motivation.

Constraints from the project context: media stays in S3-compatible object storage, metadata and the search index live in PostgreSQL, the server never proxies media bytes and never transcodes or transcribes, and the cluster needs no GPU.

Finished assets are not new in this change: `add-asset-upload` already records each uploaded finished asset with its format, resolution, size, checksum and uploader, and its verified upload is the input here. This change adds the lineage edges and the usage status to those existing records, and lists them in the pool; a finished asset is never stored twice.

## Goals / Non-Goals

**Goals:**
- One shared pool in which every item has a kind (raw or clip), a stage, an uploader and person and free tags.
- Lineage that can be queried in both directions (from a raw item to its assets, from an asset to its sources).
- Search over transcript text with filters, plus a meaning-based search that works without a GPU on the cluster.

**Non-Goals:**
- No server-side rendering, cutting, transcoding or transcription (principle 2). Timelines are produced and rendered locally; transcripts are produced on the editing PC.
- No proxying of media bytes through the server (principle 1).
- No web-based timeline editor; timelines are created by the desktop app or by Claude through its local MCP server (`add-local-editing-mcp`).
- No publication to external platforms here; where `add-content-distribution` is available, its publication records are only read for usage status.
- No per-person libraries; there is one pool for the team.

## Decisions

**One shared pool with uploader and person tags.** All raw material and clips of the team live in one pool. Each item records its uploader, the hub people or accounts who appear in it (person tags) and free tags. Filters "mine", by person, by tag and by stage narrow the pool.
- Rejected: one pool per person. It splits the team's material, hides clips from the people who would recut them, and makes cross-person search and lineage queries awkward.

**Transcripts uploaded as subtitle files from the editing PC.** A transcript is a WebVTT or SRT file uploaded with its raw item; the optional JSON form with speaker names is a Should. The hub parses the file into timed segments and refuses malformed files with the failing line named.
- Rejected: server-side transcription in the cluster. Transcription needs a GPU for acceptable speed, which principle 2 keeps off the cluster, and it would make the hub run compute-heavy media work.
- Rejected: an external transcription API. Raw material would leave OLF's own infrastructure, which the team does not accept for stream recordings.

**Uploader recorded, edit rights from roles.** The uploader is stored once at creation and never changes. Whether a person may change an item, including its tags, is decided by their role, so a former editor now in the viewer role cannot edit, and any editor may fix content another member uploaded.
- Rejected: owner-only editing. It would block editors from fixing content made by others, which the team workflow needs.
- Rejected: contributor lists maintained by hand. Person tags answer "who appears in it" and the uploader answers "who made it"; a second list drifts from the people who actually worked on an item.

**Raw material by presigned multipart upload.** Raw recordings go to the private Ceph bucket through the flow of `add-asset-upload`, in parts of at most 95 MB because of the Cloudflare body limit (principle 15). The verified upload is registered as a raw item.
- Rejected: uploading through the API server. It would proxy large media bytes through the cluster (principle 1).

**Clip status.** A clip has status `active` (the default) or `suggested`, independent of its stage. A suggested clip is listed and filterable as suggested. The spec does not define how a suggestion becomes active; this change keeps the status as stored and leaves the acceptance workflow to the web library UI.
- Rejected: a separate table for suggestions. It duplicates the clip model and complicates the "clips" listing.

**Timelines stored as validated documents.** A timeline is one JSON document in PostgreSQL, validated against the timeline structure on upload and returned unchanged. Timelines are small (a list of ranges, overlays and captions), so a document column keeps them queryable and avoids an extra object-storage round trip.
- Rejected: storing timelines as files in object storage. Files cannot be queried for reuse or validated centrally without downloading them.

**Relational model in PostgreSQL.** Pool items, clip ranges, timeline documents, tags, transcript segments and lineage edges are rows in PostgreSQL tables. Lineage is a separate edge table (asset to timeline, timeline to clip, clip to raw item), so that "all assets made from a stream" is an indexed query.
- Rejected: storing lineage as a JSON array inside the asset record. It cannot answer "all assets from a stream" without scanning every asset.

**Full-text search with PostgreSQL.** Transcript segments are indexed with PostgreSQL full-text search (a generated text-search column with a GIN index) and filtered with ordinary SQL predicates on uploader, person, tag, stage, date and usage status. This keeps search inside the database that already holds the metadata (principle 1), with one backup and one operational surface.
- Rejected: a dedicated search engine. It adds a second store to keep in sync for a corpus of stream transcripts that PostgreSQL handles comfortably.

**Semantic search with stored vectors.** Each transcript segment gets an embedding stored in the same PostgreSQL database with a vector index. Embeddings are computed on the server by a small CPU-only model, so the cluster stays free of GPUs and the search works on every PC.
- Rejected: computing embeddings on the editing PC and uploading them. Editing PCs may be offline and the transcript upload does not carry embeddings; this decision is revisited under Open Questions.
- Rejected: a separate vector database. Same reasoning as for full-text search.

**Retention by lifecycle policy.** A retention job reads a configured policy per stream and moves raw media to a cold storage class through the object store's lifecycle mechanism, then records the new media state on the raw item. Deletion of raw media sets the media state to removed and keeps the raw item row, so lineage and clip ranges remain valid.
- Rejected: cascading deletes from raw items. A deleted raw item would take its clips, transcripts and assets with it, which the requirements forbid.
- Rejected: per-item manual actions only. The retention requirement describes a policy, and manual action does not scale across streams.

**Broadcast lineage by time-span overlap.** Where `add-twitch-insights` is available, an imported Twitch broadcast is linked to a raw item of the same channel when their spans overlap by more than a configured share. Two or more candidates produce no link and ask a person to choose. Ambiguous or low-overlap matches stay unlinked rather than being guessed.
- Rejected: linking by title or by name. Titles are edited on the platform and are not stable.

**Chapter marks stored on the timeline.** Chapter marks are part of the timeline document, validated on upload (ascending, first mark at 00:00, at least ten seconds apart). Where `add-youtube-integration` is available, chapter lines are built from them when the YouTube description of a post is prepared, so a later edit of the timeline changes the description only for posts that are not yet published.
- Rejected: storing a hand-typed chapter list per post. It duplicates the timeline and drifts from the cut.

**Audit for deletions and transcript replacements.** Every deletion and every transcript replacement writes an audit entry with the actor, the item, the time and the channel (web, desktop, OBS plugin or MCP). The audit mechanism itself is defined by `add-access-control`; this change only emits the entries.

**Later components by "Where" clauses.** Requirements that need a component of a later phase (`add-hub-mcp-server`, `add-recording-ingest`, `add-twitch-insights`) are phrased "Where <component> is available", so this change hard-depends only on `add-access-control` and `add-asset-upload`. The other phase 1 components (`add-brand-kit`, `add-content-analytics`, `add-content-distribution`, `add-youtube-integration`) are also phrased "Where <component> is available", so the pool can be built first and each link applies once its change exists (principle 16).
- Rejected: moving the whole library-MCP capability to a later change. The capability's requirements are stated now so that the hub MCP work has a fixed contract to build against.

## Risks / Trade-offs

- [Risk] Search slows down as transcripts grow → Mitigation: GIN index on the generated search column, and a load check with a year of stream transcripts in the integration suite.
- [Risk] A finished asset is registered before the timeline it cites exists → Mitigation: the registration validates that all referenced timelines, clips and raw items exist and rejects the asset otherwise.
- [Risk] Retention moves media that a timeline still needs for re-cutting → Mitigation: the policy moves media only; clips and timelines stay valid, and re-cutting a cold raw item needs the restore flow listed under Open Questions before the policy is enabled.
- [Risk] Embeddings on CPU make indexing slow for long streams → Mitigation: embeddings are created asynchronously after a transcript is uploaded; full-text search works immediately, so the slower path never blocks a search.
- [Risk] Person tags are missing for people who appear in a recording → Mitigation: tags are set by editors when they cut or upload; the pool filter shows untagged raw items so they can be completed.
- [Risk] A time-span match links the wrong raw item → Mitigation: ambiguous or low-overlap matches are not linked; a person chooses among candidates.
- [Risk] A transcript file from a different recording is uploaded by mistake → Mitigation: the upload names the raw item explicitly; replacement is audited and reversible by a new upload.
- [Trade-off] Timelines as documents trade schema-level querying for simplicity; queries use lineage edges and clip ranges, not timeline internals.

## Migration Plan

This is the first content-library release, so there is no existing data to migrate. Schema changes ship as forward-only migrations with the server release. Rollback is done by deploying the previous server release and leaving the new tables in place; no content is removed by rollback. The retention policy is disabled by default and enabled per stream after the restore flow is available.

## Open Questions

- Should the editing PC supply embeddings with its transcripts instead of the server computing them? The specs read the same either way; the choice changes the editing PC and server split only.
- Chapter line format (time and title layout) and the minimum chapter length are confirmed against the YouTube description rules during the implementation tasks; the spec states the ten-second minimum, so a platform change would only alter the line format.
- How is a cold raw item restored for re-cutting, and who may trigger it? Needed before the retention policy is enabled for any stream; does not change the requirements in this change.
