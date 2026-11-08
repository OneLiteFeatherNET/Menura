# Proposal

Conventional Commits: `feat(library)` (code PR). Phase: 1 (Pool, Calendar & Analytics). Depends on: `add-access-control` (phase 0), `add-asset-upload` (phase 1). Where `add-brand-kit` is available, timeline layout templates are validated against it; where `add-content-analytics` is available, external posts are linked to raw items in analytics; where `add-content-distribution` is available, usage status includes publication records; where `add-youtube-integration` is available, chapter lines reach YouTube descriptions; where `add-recording-ingest` is available, OBS recordings also become raw items; where `add-hub-mcp-server` is available, the library MCP tools are offered; where `add-twitch-insights` is available, imported broadcasts are linked to raw items.

Strand: content.

## Why

Stream recordings and clips live on individual streamers' PCs, so nobody on the team can find a moment from last month's stream or reuse a finished clip. The team needs one shared pool: every raw recording and every clip, with who uploaded it, who appears in it and what it is about, searchable by the words spoken on stream. Transcripts are produced on the editing PC and uploaded with the material; the hub stores and searches them but never transcribes.

## What Changes

- Add one shared pool of content items. Every item is raw material (a stream recording) or a clip (a time range of a raw item with a title), has a stage (raw, cut or rendered), an uploader, person tags (the hub people or accounts who appear in it) and free tags.
- Upload raw material from the web app by the presigned multipart upload of `add-asset-upload`; a verified upload becomes a raw item owned by its uploader.
- Attach transcripts uploaded as WebVTT or SRT files (Should: JSON segments with speaker). The hub parses them into timed segments and indexes them; a malformed file is refused with a problem naming the line. Replacing a transcript is audited.
- Store timelines (ordered source ranges, overlays, captions and a layout template reference, checked against the brand kit where `add-brand-kit` is available) and register finished assets with lineage: every finished asset references the timeline, clips and raw items it came from. Usage status shows whether an item was never used; where `add-content-distribution` is available, it also shows whether the item was already published, and where.
- Add full-text search over transcripts with jump-to-timestamp, filterable by uploader, person, tag, stage, date and usage status; semantic search over transcript segments (Should).
- Store suggested highlight candidates as clips with status `suggested` (Could); where the hub MCP server is available, MCP clients may create them too.
- Store optional chapter marks on timelines and, where `add-youtube-integration` is available, add one chapter line per mark to the YouTube description of each post whose finished asset was made from that timeline.
- Where `add-content-analytics` is available, link external posts cut from a raw item to that raw item, its stream and its uploader, and provide the library dimensions to its rollups.
- Add retention: raw media can be moved to cold storage or deleted per policy while clips, transcripts and assets remain; every deletion is explicit and audited.
- No breaking changes: this is the first content-library capability.

## Capabilities

### New Capabilities
- `content-library`: pool items (raw material and clips), raw uploads, transcript upload, tags and uploader, timelines, finished assets, their lineage, usage status, suggested clips, retention and deletion.
- `content-search`: full-text and semantic search over transcripts, with filters and jump-to-timestamp results.
- `timeline-publishing`: chapter marks on timelines and the YouTube chapter lines generated from them.
- `library-mcp-tools`: hub MCP tools and workflow prompts on the library (search, transcript range reads, clip suggestions, lineage reads); built when `add-hub-mcp-server` is available.

### Modified Capabilities
- None.

## Impact

- Server: pool item model and metadata store, tags, lineage links, transcript parser and segment store, search index, retention job and audit entries for deletions and transcript replacements. Media bytes stay in object storage; the server never proxies them, never transcodes and never transcribes.
- Consumers: the web library view, the local editing tools that fetch source ranges and upload timelines, assets and transcripts, and the hub MCP server build on these requirements in later changes.
- Integrations: where `add-youtube-integration` is available, the chapter lines are supplied to its YouTube upload; where `add-twitch-insights` is available, its imported broadcasts are matched to raw items, and neither change's records are altered in their structure.
- Access: all writes are subject to the roles defined in `add-access-control`.
