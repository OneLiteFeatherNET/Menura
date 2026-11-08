# Proposal

Conventional Commits: `feat(attribution)` (code PR). Phase: 2 (Platforms & Join Attribution). Depends on: `add-access-control`, `add-channel-connections`, `add-external-post-import`, `add-content-analytics`, `add-content-series`.

Strand: content.

## Why

The shorts are meant to bring new players to the Minecraft network, but nothing shows whether they do. A player who sees a short and later joins the network leaves no trace that links the join to the clip, the channel or the platform. All players join over one shared domain, so the address cannot tell channels apart, and a per-channel host was rejected by the maintainer. Menura therefore asks the players themselves. Once, and optionally, the network proxy asks a new player "Wo hast du uns gefunden?" with a fixed set of answers. Together with the time of the join, the answer shows which platform a new player names and which posts of the last 72 hours they could have seen, without Menura learning who the player is.

## What Changes

- One join domain is configured for the whole installation by an admin. It is a single lower-case host name, not a value per channel.
- The network proxy reports join events to Menura over an authenticated REST endpoint: an event id, a first-join flag, a timestamp and a pseudonym computed at the proxy with a keyed hash. Menura refuses any payload that carries a player UUID, a player name or an IP address, and treats repeated event ids as one event.
- The proxy may send one source answer per first join, within 24 hours of the join. The answer is one of `youtube`, `tiktok`, `instagram`, `twitch`, `friends` or `other`. Free text is refused.
- Menura reports measured first joins per day and week in total, per stated source and as unanswered, with the answer rate. Per-platform numbers come only from answers and are labelled "stated by players".
- A first join is estimated to have followed the posts of all channels published in the attribution window before it (72 hours by default, one installation-wide setting), split evenly. A join answered with a platform is restricted to that platform's posts in the window. Every attribution is marked as an estimate.
- Estimated joins are summed per series through the series of the posts.
- Join counts are written as internal metric points into the analytics store of `add-content-analytics`, so dashboards read them from rollups.
- The call-to-action text names the join domain, one text per language for all channels; the render bundle of `add-content-series` carries it.

The proxy side (sending the events and asking the in-game question) is its own change in the server repository. This change defines the Menura side only.

## Capabilities

### New Capabilities
- `join-attribution`: the join domain setting, join event ingestion with the privacy refusal, source answers, measured and stated new-player figures, estimated post and series attribution, join metrics in analytics and the call-to-action text.

### Modified Capabilities
- None.

## Impact

- PostgreSQL (CloudNativePG, PostgreSQL 17, standard image): a join domain setting, a join event table partitioned by month, a source answer table keyed by join event, and join count points in the analytics metric tables of `add-content-analytics`; no new database or extension.
- Server: the REST endpoints for join events and source answers on `api-menura.onelitefeather.net`, authenticated as a machine client with its own credential, rate-limited with 429 and Retry-After; REST reads for the attribution views; an admin endpoint for the join domain and the attribution window.
- Web: the join domain and attribution window settings and the attribution view with the answer rate.
- Proxy: a separate change in the server repository sends the events and asks the in-game question. Without it no join is recorded and every join stays unanswered.
- DNS: no change in this repository; the shared join domain already leads to the network.
- Jobs: no new job; the analytics rollup of join points is updated when an event or answer is accepted.
