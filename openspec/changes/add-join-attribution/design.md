# Design

## Context

Channels, platform connections and the external posts with their publication times come from `add-channel-connections` and `add-external-post-import`. Series assignments come from `add-content-series`, and the metric points, rollups and the "Where ... is available" rules for dashboards come from `add-content-analytics`. Requirements are in `specs/join-attribution`; motivation is in proposal.md.

The network proxy is outside Menura. It is a component of the server repository, and it sends join events to Menura. Principle 3 forbids relaying through Minecraft plugin messaging, so the proxy pushes to Menura over REST and Menura never reads proxy logs. Principle 5 (privacy by default) and principle 1 (metadata and metrics in PostgreSQL) apply. Principle 11 applies to the machine credential and the rate limits.

## Goals / Non-Goals

**Goals:**
- Estimate, per channel and post and series, how many first joins followed a short.
- Store nothing that identifies a player: no UUID, no name, no IP address.
- Make a join visible in reads within 60 seconds.
- Keep the attribution rule simple enough to explain to the team in one sentence.

**Non-Goals:**
- No exact attribution. Every post figure is an estimate, and the UI always says so.
- No tracking of a player after the first join, and no link between a player and their later activity.
- No click counting. Join hosts count players, not clicks.
- No proxy implementation and no DNS configuration in this repository.
- No join attribution to a single clip; the unit of attribution is the post, and the post maps to the clip.

## Decisions

**One join host per channel, not per series or per clip.** A channel's host is the same for all of its shorts, so it can be given in the video description and spoken in the last seconds, and viewers remember it. The platform is the channel's platform, so the host also identifies the platform. Alternatives considered:
- Host per series and platform (rejected: a viewer has to remember a different address for each series, which is harder than remembering one per channel).
- A code typed per clip (rejected: the address changes every clip, which works against recognition, the purpose of the series).
- A short link (rejected: it counts clicks, not players, and a click does not show that a player joined).

**Pseudonym computed at the proxy with a keyed hash.** The proxy derives a pseudonym from the player UUID with a secret key that stays at the proxy, and sends only the pseudonym. Menura never sees a UUID and cannot reverse the pseudonym. Alternatives considered:
- The UUID in Menura (rejected: it is personal data that Menura does not need, and principle 5 forbids it).
- An unkeyed hash at the proxy (rejected: UUIDs are a space that can be enumerated, so an unkeyed hash can be reversed by anyone who has the hash function).

**Refusal of personal data at the boundary.** The endpoint checks the payload fields against a fixed list of forbidden names and value shapes and refuses the whole payload. A refusal is not partly accepted, and the refused value is never logged. Alternative: dropping the field and accepting the rest (rejected: it would accept a leak silently, and the proxy would never learn that it sends personal data).

**Even split across the posts in the window.** A first join is split evenly across the posts of the channel published in the window before it. It is an estimate of which posts the player probably saw, and it does not favour one post. Alternative: the last published post wins (rejected: it gives all the credit to the last short and none to the earlier ones that the player may have seen first, and it inflates the figures of the post published last).

**Window per channel, default 72 hours.** The window is the time a viewer plausibly remembers a short before joining. It is configurable per channel because the posting frequency differs. Alternatives: a fixed window for all channels (rejected: channels post at different rates), or an unbounded window (rejected: any join would be credited to an old post).

**Push over REST from the proxy.** The proxy calls `api-menura` with its machine credential. Alternatives considered:
- Relay through Minecraft plugin messaging (rejected: forbidden by principle 3).
- Menura polling the proxy logs (rejected: the logs contain player names and IP addresses, which principle 5 and the refusal rule forbid Menura to store).

**Freshness by writing the rollup on acceptance.** An accepted event increments the daily join rollup in the same transaction as its insert. The rollup job of `add-content-analytics` runs on an interval and would miss the 60-second requirement, so the join count is kept current on write, and the job still rebuilds it on correction. Alternative: wait for the rollup job (rejected: it lags by the job interval).

**Rate limits by machine client.** The endpoint is rate-limited per machine client in Envoy Gateway, with 429 and Retry-After, so a faulty proxy cannot flood the store.

## Risks / Trade-offs

- [Risk] Wildcard DNS for the join hosts and the proxy's logging of hosts are configured outside Menura. → This change depends on the server repository for the proxy and on the operator for DNS. The change is not usable until both exist, and the tasks say so.
- [Risk] Players who type the plain domain instead of the channel host count as direct. → The direct line is shown separately, and the number is reported to the team as a limit of the method rather than hidden.
- [Risk] The even split gives a short credit it did not earn, and the estimates overstate the effect of a busy channel. → Every figure is labelled as an estimate, and the report shows the number of posts in the window next to each estimate.
- [Risk] The proxy's key leaks, and a pseudonym can be recomputed. → The key stays at the proxy under its own secret handling, and a rotation of the key starts new pseudonyms; the first-join flag does not depend on the pseudonym staying stable across rotations.
- [Risk] The machine credential leaks. → It is a 256-bit random secret stored hashed and compared in constant time (principle 11), it can be revoked, and it never appears in logs or traces.
- [Risk] A player joins through a channel host without having seen a short. → The attribution is an estimate by design; a join with no post in the window stays with the channel only.

## Open Questions

- Retention of the pseudonyms and of the join event rows. This change keeps them while the analytics keep their points, and the operator decides the period.
- The default window of 72 hours is a starting value; the maintainer confirms it after the first posts of a channel have been measured.
- Ownership of the wildcard DNS record and of the proxy key rotation, both outside this repository.
