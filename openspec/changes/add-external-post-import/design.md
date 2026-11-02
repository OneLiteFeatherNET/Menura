# Design

## Context

Channels and their connections come from `add-channel-connections`, which defines the adapter port including a listing operation for existing published items and a metric reading operation. Requirements are in `specs/external-posts`; motivation is in proposal.md. Principle 1 applies: records live in PostgreSQL, and no other store is added. Principle 13 applies: the listing comes from the platform's official API through its own adapter. Principle 2 and the media rule of principle 1 apply: Menura references platform items and does not copy their media.

## Goals / Non-Goals

**Goals:**
- One record shape for every post on a channel, imported or published by Menura, that content analytics can join to metric points.
- A re-sync that is idempotent: running it again changes nothing that has not changed on the platform.
- A removed post stays visible as removed, with its history.

**Non-Goals:**
- No media download or copy from any platform. The Twitch facts in `add-twitch-insights` (Platform constraints) record that no VOD download endpoint exists, and the same reference-only rule applies to all platforms.
- No platform-specific listing code in this change. Each platform change implements the listing operation of the port.
- No write path back to the platform (publishing, editing, deleting) in this change.
- No MCP read of external posts here; that belongs to `add-hub-mcp-server` and is not a task of this change.

## Decisions

**One record per channel and platform identifier.** The external post record is keyed by channel and the platform's own identifier, with a unique constraint. The URL and title are attributes, because a platform can change them. Rejected: keying by URL. A title or slug change would create a second record for the same video, and metric history would split.

**Re-sync compares a complete listing with stored records.** The re-sync reads the channel's listing, creates records for new identifiers, updates changed fields, and marks stored records that are absent from a complete listing as removed. Rejected: deleting and re-importing on each run. It would lose the removal history and break the metric links of `add-content-analytics`.

**Removal is a state with a timestamp, never a deletion.** A removed post keeps its record and its metric points. A removed post that is listed again becomes live again. Rejected: hard delete, which destroys the history analytics needs. Rejected: a silent hidden flag, which would show the same post as live in another view.

**A listing that did not complete marks nothing.** A failed or partial listing is treated as unknown, not as evidence of removal. Only a complete listing can mark posts removed. This protects against pagination failures or rate limits that would otherwise make a whole channel look deleted.

**Re-sync as a scheduled job per channel.** The `jobs` component runs the re-sync on a configurable interval for each connected channel. Channels that need reconnection are skipped and shown as waiting, using the connection state from `add-channel-connections`. Rejected: syncing only when someone opens the channel. Analytics and the removal state would then depend on who looks at the page.

**Published and imported posts share the record.** Posts Menura publishes later are written to the same external post record, with the same fields. Rejected: a separate table for published posts. Analytics would then need two read paths, and the comparison between imported and published posts (revision and recycling, `add-content-analytics`) would branch on origin.

**Times stored in UTC.** Publish times are stored in UTC with the platform's value. The web app converts them for display.

## Risks / Trade-offs

- [Risk] A channel with a long history needs several listing pages and quota. → Mitigation: the listing is paged, progress is stored per channel, and the first import may span several runs. The quota state is shown per connection by `add-channel-connections`.
- [Risk] A platform does not list some items, for example private or unlisted videos. → Mitigation: the platform change states what its listing covers (TikTok lists public videos only, see `add-tiktok-insights`), and the overview says so.
- [Risk] A platform reuses or changes identifiers. → Mitigation: the unique key is the platform identifier only as the platform defines it; a change of identifier appears as a removal and a new post, and the overview shows both.
- [Trade-off] Re-sync runs on a schedule, so a removal is seen up to one interval late. Accepted: removal state is for planning and reporting, not for real-time display.

## Open Questions

- Re-sync interval per platform. It is configured with a default in environment configuration and tuned after the first live week; the specs do not change.
