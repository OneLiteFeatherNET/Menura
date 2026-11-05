# Design

## Context

The lab prototype renders scenes from static data files and a live state daemon on the streamer's machine. Menura moves this to the hub so that scenes, modules and themes are managed centrally and shared by the team. Requirements are in `specs/` (overlay-scenes, overlay-modules, overlay-live-control); motivation is in proposal.md. Principle 3 applies: each overlay browser source holds its own Server-Sent-Events feed; no relay through OBS, chat bots or game-server messaging. OBS browser sources cannot sign in interactively, so they authenticate with a variant-scoped token from access control; the owner's OBS plugin (`add-obs-plugin`) creates the browser sources and rotates that token, so no person ever handles it.

The prototype's module catalogue (reference only, not copied) is: crew, limits, mic, alerts, camera, promo, promoBand, board, chat, goal, name, banners, frame. The brief's required built-in set is chat rail, task board, follower goal, promo card, now-playing and limits. The remaining catalogue modules (mic, alerts, camera, promoBand, name, banners, frame) are built-in as well. The prototype's `crew` module is not carried over: the Claude presence module from `add-agent-activity-feed` replaces it.

## Goals / Non-Goals

**Goals:**
- One hub-side model of scenes and module instances that the web app edits and the overlay renders.
- A module contract (manifest) that built-in and registry modules satisfy in the same way.
- Live state that a browser source can rebuild from a single read after any reconnect.
- One central template per scene that every person inherits, with personal variants that change only what their brand profile allows.

**Non-Goals:**
- No copying of prototype code; the prototype is the reference for domain rules only.
- The overlay does not control OBS (scene switching, sources, recording or streaming state).
- No freeform visual scene editor with drag handles in this change; positions and sizes are edited as values in the web app.
- Sandboxing of third-party modules is not required for the first delivery (it is a Could requirement).
- Personal variants do not carry their own layout; a layout change is always made on the central template.

## Decisions

**Scene feed as one SSE stream per browser source, with a snapshot on connect.** Each connection receives the current value of its topics first, then deltas. Alternatives: polling (rejected: cannot meet the one-second requirement without high load), WebSocket (rejected: SSE is the project standard for feeds and needs no bidirectional channel here; operator actions go over plain HTTP), a shared relay in a single process per venue (rejected: principle 3).

**Resync by reading state, not by replaying events.** After a reconnect the overlay fetches the scene snapshot and replaces its state. Alternative: replay from an event log with Last-Event-ID (rejected: requires long event retention for a stream that only needs current values; a snapshot is simpler and always correct).

**Topic filtering on the server.** The hub only sends a module the topics in its manifest and filters private data before it reaches the feed, so a hidden area never sends data to the browser source. Alternative: sending everything and hiding in the client (rejected: private data would reach the browser source and could be recovered from it, which breaks principle 5).

**Variant token in the browser-source URL, rotated by the OBS plugin.** The token is scoped to one variant, read-only and rotatable; the OBS plugin writes the URL into the browser source and rotates it without displaying it. Redaction in logs and a no-referrer policy keep it out of traces and outgoing requests. Alternative: session cookies from an interactive login in OBS (rejected: browser sources have no login flow). Alternative: a person copying the URL by hand (rejected: the URL would be shown and reused).

**Module manifest as the single contract.** Built-in and registry modules declare id, version, config schema, topics and actions; the hub validates configuration against the schema on save. Alternative: hard-coding modules into the overlay app (rejected: the brief requires third-party modules from the registry).

**Central template with personal variants.** A variant stores only its owner's overrides (tokens and assets the brand kit marks overridable); layout and everything else is resolved from the template at read time. A template change therefore reaches every variant at once, except where the owner overrode that exact token. Alternative: copying the template per person (rejected: every central change would need a fan-out to every copy and would drift).

**Theme as template references, not raw colours.** Modules reference brand tokens from the overlay theme template, so a new brand kit version reaches live variants without editing them. Alternative: copying colours into each scene (rejected: drift between scenes and the brand kit).

**Operator actions as audited HTTP commands.** The operator panel sends commands that the hub validates, applies to live state and publishes on the feed. Alternative: the operator panel writing directly to the feed (rejected: no audit trail and no role check at the hub).

**Identifiers are random, not sequential.** Templates, variants and module instances get non-sequential identifiers and a request for another person's variant answers 404, so ids cannot be enumerated.

## Risks / Trade-offs

- [Risk] A variant token leaks and grants read of that variant's feed. → Mitigation: tokens are scoped to one variant, read-only, rotated by the owner's plugin and revocable by an admin; they are redacted from logs and the feed carries no credentials and no viewer personal data.
- [Risk] A faulty third-party module breaks the whole scene. → Mitigation: manifest validation at registration, render errors contained per module; sandboxing is available as a Could requirement.
- [Risk] Live changes take longer than one second on a slow connection. → Mitigation: deltas are small; the latency requirement is verified in the integration test with an injected clock and a measured budget, and failures are reported as a metric.
- [Risk] Private data reaches the screen through a stale module state. → Mitigation: server-side filtering before the feed plus the resync snapshot honouring the same filter.
- [Trade-off] A person who operates another person's stream cannot do so through this change, because variants are private. Accepted; a shared operator variant would be a separate decision.
- [Trade-off] Snapshot resync sends more data after a reconnect than replaying missed events would. Accepted: scene state is small.

## Open Questions

None that change specs, approach or tasks.
