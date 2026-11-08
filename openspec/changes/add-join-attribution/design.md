# Design

## Context

Channels, platform connections and the external posts with their publication times come from `add-channel-connections` and `add-external-post-import`. Series assignments come from `add-content-series`, and the metric points, rollups and the "Where ... is available" rules for dashboards come from `add-content-analytics`. Requirements are in `specs/join-attribution`; motivation is in proposal.md.

The network proxy and the in-game question are outside Menura. They are components of the server repository, and they send join events and source answers to Menura. Principle 3 forbids relaying through Minecraft plugin messaging, so the proxy pushes to Menura over REST and Menura never reads proxy logs. Principle 5 (privacy by default) and principle 1 (metadata and metrics in PostgreSQL) apply. Principle 11 applies to the machine credential and the rate limits.

## Goals / Non-Goals

**Goals:**
- Show how many new players arrive, and how many name each platform as their source, with the answer rate next to every such number.
- Estimate, per post and series, how many first joins followed a short.
- Store nothing that identifies a player: no UUID, no name, no IP address, no free text.
- Make a join visible in reads within 60 seconds.
- Keep the attribution rule simple enough to explain to the team in one sentence.

**Non-Goals:**
- No exact attribution. Every post figure is an estimate, and the UI always says so.
- No claim that a platform measured anything. Per-platform figures are what players stated.
- No tracking of a player after the first join, and no link between a player and their later activity.
- No click counting. The join domain counts players, not clicks.
- No proxy implementation and no in-game question in this repository.
- No join attribution to a single clip; the unit of attribution is the post, and the post maps to the clip.

## Decisions

**One join domain and an in-game source question.** All players join over one shared domain, so the address cannot identify a channel. The maintainer decided against a host per channel. Instead the proxy asks a new player once, optionally, "Wo hast du uns gefunden?" with the fixed choices youtube, tiktok, instagram, twitch, friends and other. The answer is reported with the first-join event id. The call-to-action names the one domain for all channels. Alternatives considered:
- A join host per channel (rejected by the maintainer: one domain for everything, and per-channel hosts would have to be remembered and DNS-managed for each channel).
- A code typed per clip in the call-to-action (rejected: low uptake, because a viewer has to remember and type it).
- Time window only, with no question (rejected: it gives no platform signal at all; every post in the window is equally likely).

**One answer per first join, within 24 hours.** A second answer for the same join with the same value is ignored with 200, so a retried request is harmless. A second answer with a different value is refused with 409, so the first stated source stands and the count cannot change silently. An answer after 24 hours is refused, because the player no longer plausibly remembers the short that led to the join. A join without an accepted answer is counted as unanswered, and is never guessed.

**Fixed choices, no free text.** The answer is one of six values, and any other value or extra field is refused with 422. Free text would be a channel for personal data that the refusal rule cannot check reliably.

**Pseudonym computed at the proxy with a keyed hash.** The proxy derives a pseudonym from the player UUID with a secret key that stays at the proxy, and sends only the pseudonym. Menura never sees a UUID and cannot reverse the pseudonym. Alternatives considered:
- The UUID in Menura (rejected: it is personal data that Menura does not need, and principle 5 forbids it).
- An unkeyed hash at the proxy (rejected: UUIDs are a space that can be enumerated, so an unkeyed hash can be reversed by anyone who has the hash function).

**Refusal of personal data at the boundary.** The endpoint checks the payload fields against a fixed list of forbidden names and value shapes and refuses the whole payload. A refusal is not partly accepted, and the refused value is never logged. Alternative: dropping the field and accepting the rest (rejected: it would accept a leak silently, and the proxy would never learn that it sends personal data).

**Even split across all posts in the window, restricted by the stated platform.** A first join is split evenly across the posts of all channels published in the window before it. If the player named a platform, the split is restricted to that platform's posts in the window, because the answer is a stronger signal than the time window alone. If that platform had no post in the window, the join is counted for the platform only and no post receives it, rather than giving credit to a post on another platform. Alternative: the last published post wins (rejected: it gives all the credit to the last short and none to the earlier ones that the player may have seen first).

**One window for the installation, default 72 hours.** The window is the time a viewer plausibly remembers a short before joining. Because all players join over one domain, there is no per-channel key to set a window by, so the window is one installation-wide setting that an admin may change. Alternatives: a window per channel (no longer possible without a per-channel join key), or an unbounded window (rejected: any join would be credited to an old post).

**Stated and measured figures are kept apart.** Per-platform numbers are labelled "stated by players" and always shown with the answer rate of the same report. Unanswered joins are derived as the total minus the stated sources, so the total never depends on answers arriving.

**Push over REST from the proxy.** The proxy calls `api-menura` with its machine credential. Alternatives considered:
- Relay through Minecraft plugin messaging (rejected: forbidden by principle 3).
- Menura polling the proxy logs (rejected: the logs contain player names and IP addresses, which principle 5 and the refusal rule forbid Menura to store).

**Freshness by writing the rollup on acceptance.** An accepted event or answer increments the daily join rollup in the same transaction as its insert. The rollup job of `add-content-analytics` runs on an interval and would miss the 60-second requirement, so the counts are kept current on write, and the job still rebuilds them on correction. Alternative: wait for the rollup job (rejected: it lags by the job interval).

**Rate limits by machine client.** The endpoints are rate-limited per machine client in Envoy Gateway, with 429 and Retry-After, so a faulty proxy cannot flood the store.

## Risks / Trade-offs

- [Risk] Low answer rate: most players do not answer the question, so the stated figures cover only part of the new players. → Every stated-source figure is shown next to the answer rate of the same report, so the team sees how much of the picture the answers cover. Unanswered joins are never redistributed to platforms.
- [Risk] Answer bias: the players who answer may differ from those who do not, for example they may be more willing to talk in game. → The stated figures are labelled as stated by players and are not extrapolated to the unanswered joins.
- [Risk] The in-game question is not built yet, or is worded or placed badly, so no answers arrive. → Until the proxy change ships, every join is unanswered, and the reports show that with an answer rate of 0 %. The question itself is implemented in the proxy plugin, outside Menura, and is tested there.
- [Risk] The even split gives a short credit it did not earn, and the estimates overstate the effect of a busy channel. → Every figure is labelled as an estimate, and the report shows the number of posts in the window next to each estimate.
- [Risk] A player joins without having seen a short, or a platform answer names a platform with no post in the window. → The attribution is an estimate by design; a join with no post in the window, or with a platform that has none, is not given to any post.
- [Risk] The proxy's key leaks, and a pseudonym can be recomputed. → The key stays at the proxy under its own secret handling, and a rotation of the key starts new pseudonyms; the first-join flag does not depend on the pseudonym staying stable across rotations.
- [Risk] The machine credential leaks. → It is a 256-bit random secret stored hashed and compared in constant time (principle 11), it can be revoked, and it never appears in logs or traces.

## Open Questions

- Retention of the pseudonyms, the join event rows and the source answers. This change keeps them while the analytics keep their points, and the operator decides the period.
- The default window of 72 hours and the 24-hour answer limit are starting values; the maintainer confirms them after the first posts and answers have been measured.
- Wording and placement of the in-game question, and the ownership of the proxy key rotation; both are outside this repository.
