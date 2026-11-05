# Proposal

Conventional Commits: `feat(library)` (code PR). Phase: 4 (S3 & Recording). Depends on: `add-content-analytics`, `add-recording-ingest`, `add-asset-upload`, `add-hub-mcp-server`, `add-twitch-insights`, `add-youtube-integration`.

Strand: content.

## Why

Recordings, transcripts and cut clips currently live on individual streamers' PCs and in the lab
prototype, so nobody can find a moment from last month's stream or reuse a finished asset. The hub
needs a central library that indexes all content items with their lineage, so that every clip can
be found, recut and recycled.

## What Changes

- Add a central library of content items: recordings, transcripts, clips (a time range on a
  recording with title and tags), timelines (ordered source ranges, overlays, captions and a layout
  template reference, produced locally and stored centrally for reuse) and finished assets
  (rendered videos, thumbnails and images). Finished assets are the records of `add-asset-upload`
  (phase 2), which this change extends with lineage and usage; it does not create a second record.
- Record lineage: every finished asset references the timeline, clips and recordings it came from,
  so all assets made from a stream can be found and an old clip can be re-cut in a new layout.
- Add full-text search over transcripts with jump-to-timestamp, filterable by date, stream, tags,
  markers and usage status (never used, already published where).
- Add semantic search over transcript segments (Should) and store AI-suggested highlight candidates
  as clips with status `suggested`, which MCP clients may also create.
- Link imported Twitch broadcasts to the recordings they match by time-span overlap; a broadcast with no match, or with several candidates, stays unlinked until a person chooses.
- Store optional chapter marks on timelines and add one chapter line per mark to the YouTube description of each post whose finished asset was made from that timeline.
- Add retention: raw recordings can be moved to cold storage or deleted per policy while clips,
  transcripts and assets remain; every deletion is explicit and audited.
- No breaking changes: this is the first content-library capability.

## Capabilities

### New Capabilities
- `content-library`: content items (recordings, transcripts, clips, timelines, finished assets), their lineage, lineage of imported Twitch broadcasts to recordings, suggested clips and retention.
- `content-search`: full-text and semantic search over transcripts, with filters and jump-to-timestamp results.
- `timeline-publishing`: chapter marks on timelines and the YouTube chapter lines generated from them.
- `library-mcp-tools`: hub MCP tools and workflow prompts on the library (search, transcript range reads, clip suggestions, lineage reads); built on the hub MCP server of `add-hub-mcp-server`.

### Modified Capabilities
- None.

## Impact

- Server: new content-item model and metadata store, lineage links, search index, retention job and audit entries for deletions. Media bytes stay in object storage; the server never proxies them and never transcodes.
- Consumers: the web library view, the desktop app (fetches source ranges and uploads timelines and assets) and the hub MCP server (search, clip suggestions, lineage reads) build on these requirements in later changes.
- Integrations: the imported Twitch broadcasts of `add-twitch-insights` are matched to recordings, and the chapter lines are supplied to the YouTube upload of `add-youtube-integration`; neither change's records are altered in their structure.
- Access: all writes are subject to the roles defined in `add-access-control`.
