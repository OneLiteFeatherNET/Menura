# Proposal

Conventional Commits: `feat(publish)` (code PR). Phase: 2 (Planning & Scheduling). Depends on: `add-access-control`, `add-channel-connections`, `add-external-post-import`, `add-asset-upload`, `add-content-analytics`.

Strand: content.

## Why

Finished videos and posts are published today by hand, one platform at a time, with text and timing decided ad hoc. Nothing records what went where, when a post failed, or which asset it came from. OneLiteFeather needs one place where approved content is scheduled to its channels, with a human approval before anything leaves the organisation.

## What Changes

- Add channels provided by the platform integrations: YouTube, TikTok and Instagram for publishing, Twitch for schedule. A channel is connected through the platform's own consent in `add-channel-connections`; no credential is pasted into Menura and none is returned by any API or MCP tool. X, Bluesky, Mastodon and Discord are later candidates, not part of this change.
- Add posts: one or more finished assets recorded by `add-asset-upload`, per-channel text, hashtags, cover and scheduled time. Posts move through draft, in review, approved, scheduled, published or failed. Only finished assets recorded by `add-asset-upload` can be scheduled; every external post goes through the hub.
- Require a human publisher to approve a post in the web app.
- Add a schedule dry run that checks a planned post before its time (format and text limits, connection state, platform audit status, collisions) and shows problems in the calendar.
- Add a content calendar with planned and published posts per channel, and recycling: re-schedule an asset to other channels or re-post it with new text, with a link to the original post.
- Add optimisation revisions: a proposed revision of a draft or scheduled post, with a rationale, is stored as its own record; a publisher accepts or rejects it, and an accepted revision of an approved post returns it to review.
- Run publication as small Kubernetes Jobs (CronJob) that claim due approved posts; the server only plans. Failed publications are retried with backoff and shown.
- Store external identifiers and URLs of published posts, and record each published post as an external post of `add-external-post-import`.
- Add a pipeline view per channel and stage (draft, in review, approved, scheduled), and a runway forecast: the date each channel is expected to run empty, from scheduled posts and the configured cadence, with a warning below a configurable threshold (default seven days).
- Add a private, read-only iCalendar (RFC 5545) subscription feed per person with planned posts, approvals due and runway warnings; the feed URL carries a revocable 256-bit token. Writing to calendars is a Non-Goal.
- Where the hub MCP server (`add-hub-mcp-server`, phase 3) is available, MCP clients may create drafts, propose revisions and read the calendar and pipeline. No MCP tool approves or publishes a post in any case.
- Should: show the latest views and likes of each published post in the calendar, as collected by `add-content-analytics` (phase 1), with the time of collection.
- Could: per-channel best-time suggestions from the aggregates of `add-content-analytics`, with their basis; and a read-only feed of approved posts and their finished assets for the OLF website.
- No breaking changes: this is the first distribution release.

## Capabilities

### New Capabilities
- `content-distribution`: channels provided by platform integrations, posts and their lifecycle, human approval, dry run, calendar, recycling, optimisation revisions, job-based publication, publication results and retries, pipeline view, runway forecast, latest performance numbers and best-time suggestions from analytics, and the website feed.
- `content-calendar-feed`: the private read-only iCalendar subscription feed per person, its token, event identity and access rules.

### Modified Capabilities
- None.

## Impact

- Server: channel references to connections (no credential fields), post and revision model, planning of due posts, pipeline and runway queries, platform adapters behind one port, calendar queries, and the read-only feed endpoints.
- Jobs: a Kubernetes CronJob in the `jobs` component claims due posts, publishes them, records outcomes and writes the external post record, with retries and backoff.
- Helm: CronJob definition and its schedule and concurrency settings.
- MCP (only where `add-hub-mcp-server` is available): draft creation, calendar and pipeline reads and revision proposals; no approve or publish tool.
- Web: approval, calendar, dry-run result and revision review screens.
- Assets: posts reference finished assets from `add-asset-upload`; the publication records are the source that `add-content-library` (phase 4) will later use for usage status.
- Analytics: reads the latest values and the posting-hour aggregates of `add-content-analytics`; it collects nothing itself.
- Access: approval requires the publisher role from `add-access-control`.
