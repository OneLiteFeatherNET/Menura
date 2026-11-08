# Design

## Context

Finished assets are recorded by `add-asset-upload`, and platform connections come from `add-channel-connections`. Published posts are written as external posts by `add-external-post-import`. Performance numbers and posting-hour aggregates are read from `add-content-analytics` (phase 1). The series catalogue with its opening formula, call-to-action, hashtags, clip tags, playlists and weekday slot comes from `add-content-series` (phase 1). Roles and tokens come from `add-access-control`, including the rule that MCP tokens never carry the publisher right once the hub MCP server (`add-hub-mcp-server`, phase 3) exists. See proposal.md - Why for the motivation.

Constraints from the project context: principle 4 (nothing leaves OLF without a person approving it), principle 1 (media stays in object storage, the server never proxies bytes; metadata and the job state live in PostgreSQL, with no further database), principle 2 (the cluster runs no media work), principle 6 (credentials and tokens never leave the server), principle 8 (configuration through environment variables), principle 11 (secrets stored hashed, 404 for hidden objects, 429 with Retry-After).

Round 3 decision D6: publication runs as small Kubernetes Jobs (a CronJob in the `jobs` component) that pick due approved posts. The server only plans: it marks posts as due and answers the pipeline and runway queries. D7: each person gets a read-only iCalendar subscription feed.

## Goals / Non-Goals

**Goals:**
- A single state machine for posts that enforces human approval in the server, not only in the interface.
- Publication that cannot silently duplicate or silently disappear.
- Credentials that cannot leak through any response or tool result.

**Non-Goals:**
- No publishing from a streamer's PC; every external post goes through the hub (proposal.md - What Changes).
- No MCP tool that approves or publishes a post (principle 4, `add-hub-mcp-server`); the MCP parts of this change are only specified for where that server is available.
- No writing to calendars (D7): the iCalendar feed is read-only, and no calendar client can create or change posts through it.
- No time series, rollups or analytics dashboards here; `add-content-analytics` owns collection and aggregation (phase 1). This change only reads its latest values and aggregates for the calendar.
- No generic plugin system for platforms; each platform is one adapter behind one port.

## Decisions

**Status transitions enforced in the domain model.** The server holds the allowed transitions (draft, in review, approved, scheduled, published, failed) and refuses any other, whichever client asks. Approval is an operation that requires the publisher role and an interactive session.
- Rejected: enforcing approval only in the web interface. An MCP client calling the API directly would bypass it.

**Revisions as separate records.** An optimisation proposal, from a person or, where the hub MCP server is available, from an MCP client, is stored as its own revision with its rationale, author and status (pending, accepted, rejected). The post changes only when a revision is accepted.
- Rejected: overwriting the post and keeping a change log. The rationale, the proposer and the reject path would be lost.

**Publication by claimed jobs.** The server marks an approved post as due at its handover time: its scheduled time, or earlier for a channel whose adapter declares native scheduling (see the next decisions). A CronJob run claims due posts with a lease stored on the post row, so that two overlapping runs cannot publish the same post, and a lease that expires without a recorded outcome leaves the post failed with an unknown outcome. Job runs read the time from an injected clock so tests drive the schedule without waiting.
- Rejected: in-memory timers inside the server. A restart or a second replica would lose or duplicate scheduled posts.
- Rejected: a separate queue service. PostgreSQL row leases are enough for the expected volume and keep the state in the one database (principle 1).

**Native scheduling by a capability flag of the adapter.** The adapter port exposes a capability flag `nativeScheduling`. For a channel whose adapter sets it, the approved post is handed to the platform ahead of its scheduled time together with that time, and the platform publishes it; for any other channel the job publishes at the scheduled time. The flag comes from the adapter, never from channel configuration, so a platform without native scheduling cannot be configured into it.
- Rejected: always publishing from the hub. A late job would publish late, and the platform's own scheduled publication would go unused.
- Rejected: a per-channel setting. It could claim a native schedule that the platform does not provide.

**Series defaults copied into the post when planned.** Planning copies the series defaults (title, description with opening formula and call-to-action, hashtags, clip tags, and the playlist of each channel) into the post's own fields. Before approval the editor changes only that post; after approval a change goes through a revision, as already decided. A later change to the series never changes a planned post.
- Rejected: resolving the series at publication time. The published text would differ from the text a publisher approved, against principle 4.

**Episodes take the next free slot.** The episode planner walks forward from the next occurrence of the series' weekday and time slot and takes each slot that holds no post on that channel. Taken slots are named in the result and never overwritten.
- Rejected: fixed dates per series. A taken slot would collide, and only the dry run would report it afterwards.

**One row per publication step.** Each post keeps a row per step (upload, schedule, metadata, publish) with its state, time and reason, written before the platform is called. A single status per post cannot show that an upload succeeded while the schedule step failed.
- Rejected: one status field per post. It hides which step failed.

**Pipeline and runway computed on read.** The forecast is the first cadence slot after now that has no scheduled post, computed from the stored schedule and cadence on each read. Nothing is cached, so a newly scheduled post removes the warning on the next read.
- Rejected: a stored forecast updated by a job. It would lag behind the calendar and show a warning that a person has already fixed.

**Feed token stored hashed and feed read-only.** The feed token is 256 bits from a CSPRNG and only its hash is stored (principle 11), so a database read cannot reconstruct a working feed URL. Unknown and revoked tokens get the same 404, so the feed does not reveal which tokens existed. Event identifiers are stable per post; a revision marker changes on every update.
- Rejected: an unauthenticated feed per channel. It would expose planned posts to anyone with the URL, and approvals due are only for publishers.

**Retry with recorded attempts.** Each attempt is written before the platform is called. If the platform outcome is ambiguous (for example a timeout after upload), the post is not retried automatically; it is shown as failed with the reason so that a person decides. Backoff delays grow with each attempt up to the configured limit.
- Rejected: blind retries. A timed-out upload may already be live, and a retry would publish it twice.

**Credentials owned by channel connections.** Platform consent, tokens, their encryption and refresh are specified in `add-channel-connections`. A channel here references one connection and has no field for any credential, so a credential cannot be serialised by accident.
- Rejected: plain columns. A database dump would expose every account.

**Dry run on scheduling and again before publication.** The dry run runs when a post is scheduled and again when its time arrives, so that expired credentials or a new collision show up before the publication attempt. Results are stored and shown in the calendar.
- Rejected: dry run only on demand. It would miss changes after scheduling.

**Platform adapters behind one port.** Each platform implements the adapter port of `add-channel-connections` (publish, status, delete, capabilities, metrics); each platform change provides its adapter with contract tests against recorded platform responses and sets the capability flag: `add-youtube-integration` in phase 1, and `add-tiktok-integration`, `add-instagram-integration` and `add-twitch-integration` in phase 2.
- Rejected: a single generic posting form. Platform limits and media rules differ too much for one code path to stay correct.

**Publication recorded as an external post.** When a post is published, the publication job writes its platform identifier, URL and publish time as an external post of `add-external-post-import`, so that imported and published content share one record of what is live on each channel.
- Rejected: a separate published-post table. Two records of the same live video would drift apart.

**Website feed reads a status filter.** The feed endpoint returns only items whose status is approved or published and uses a consumer token from access control. The website never gets write access.
- Rejected: pushing content to the website. It would need a write credential on the website side.

## Risks / Trade-offs

- [Risk] A platform's API approval for a new channel type takes longer than the release → Mitigation: channel types are enabled individually, so the other platforms ship without them.
- [Risk] A connection expires or is revoked between scheduling and publication → Mitigation: the dry run runs again at publication time and the failure names the channel and offers reconnection.
- [Risk] Platform rate limits block publication bursts → Mitigation: backoff with the same retry policy; the calendar shows the delay.
- [Risk] A person accepts a revision that changes approved text without rereading it → Mitigation: an accepted revision of an approved post returns the post to review, so approval happens again.
- [Trade-off] Ambiguous failures need manual handling; this trades automation for not publishing twice.

## Migration Plan

First release: no existing posts. Channels are connected by an admin or publisher through `add-channel-connections` after deployment. Schema ships as forward-only migrations. Rollback deploys the previous server release and leaves posts in place; scheduled publications stop when the CronJob is suspended, so the calendar must be checked after any rollback.

## Open Questions

- Which retry limit and backoff curve to use per platform; configurable per channel and tuned after the first live week, so the specs do not change.
- Which platforms are first in line for release, depending on their API access; this affects order of tasks only, not the specs. YouTube is the first publishing platform (phase 1); the other platforms are phase 2.
- How long before its scheduled time a natively scheduled post is handed over; configurable per channel with a default, tuned in the first live week, so the specs do not change.
