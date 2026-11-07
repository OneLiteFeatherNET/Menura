# Proposal

Conventional Commits: `feat(attribution)` (code PR). Phase: 1 (Recognition & Measurability). Depends on: `add-access-control`, `add-channel-connections`, `add-external-post-import`, `add-content-analytics`, `add-content-series`.

Strand: content.

## Why

The shorts are meant to bring new players to the Minecraft network, but nothing shows whether they do. A player who sees a short and later joins the network leaves no trace that links the join to the clip, the channel or the series. Each channel can therefore be given its own join host, such as `yt.onelitefeather.net`, which the call-to-action of its shorts names. When a player first joins through that host, Menura can estimate which posts of that channel came before the join, without learning who the player is.

## What Changes

- Each connected channel can have exactly one join host under the configured base domain. Editors set the hosts; they are unique across channels.
- The network proxy reports join events to Menura over an authenticated REST endpoint: an event id, the join host as the player typed it, a first-join flag, a timestamp and a pseudonym computed at the proxy with a keyed hash. Menura refuses any payload that carries a player UUID, a player name or an IP address, and treats repeated event ids as one event.
- A join via a host that belongs to no channel counts as "direct".
- New players (first joins) are shown per channel and platform, per day and per week.
- A first join via a channel's host is attributed to the posts of that channel published in the attribution window before the join (72 hours by default, configurable per channel). The join is split evenly across those posts and is always marked as an estimate. A join with no post in the window stays attributed to the channel only.
- Estimated first joins are summed per series through the series of the posts.
- Join counts are written as internal metric points into the analytics store of `add-content-analytics`, so dashboards read them from rollups.
- Each channel has a call-to-action text per language that names its join host; the render bundle of `add-content-series` carries it.

The proxy side (sending the events and the wildcard DNS for the join hosts) is its own change in the server repository. This change defines the Menura side only.

## Capabilities

### New Capabilities
- `join-attribution`: join hosts per channel, join event ingestion with the privacy refusal, freshness, direct joins, platform and post and series attribution as estimates, join metrics in analytics and the call-to-action text.

### Modified Capabilities
- None.

## Impact

- PostgreSQL (CloudNativePG, PostgreSQL 17, standard image): a join host table, a join event table partitioned by month, and join count points in the analytics metric tables of `add-content-analytics`; no new database or extension.
- Server: the REST endpoint for join events on `api-menura.onelitefeather.net`, authenticated as a machine client with its own credential, rate-limited with 429 and Retry-After; REST reads for the attribution views.
- Web: join host settings per channel and the attribution view.
- Proxy: a separate change in the server repository sends the events. Without it no join is recorded.
- DNS: wildcard records under the base domain are configured outside Menura.
- Jobs: no new job; the analytics rollup of join points is updated when an event is accepted.
