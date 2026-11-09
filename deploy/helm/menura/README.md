# Menura chart: edge policies

The chart renders the three public routes and the rate limits that Envoy Gateway applies to them. The gateway is the
shared `eg` Gateway of the cluster repository; the chart does not own it. `deploy/check-helm.sh` asserts the rendered
resources.

| Resource | Target | What it does |
|---|---|---|
| `HTTPRoute` (x3) | host | Forwards to the Service; `ResponseHeaderModifier` sets HSTS, `Referrer-Policy: no-referrer` and `nosniff` |
| `BackendTrafficPolicy` (x3) | route | Local rate limits per client address, and per `Authorization` token on the API and MCP routes; the gateway's 429 answers Problem Details with `Retry-After` |

Deliberately not rendered:

- **No `ClientTrafficPolicy`.** TLS 1.3 only and the client address from `CF-Connecting-IP` apply to the whole shared
  `https` listener, so they affect every host on it, not only Menura. They are a prerequisite that the cluster
  repository sets (see "Shared listener").
- **No `SecurityPolicy` for CORS.** CORS is answered only by the server (`micronaut.server.cors`, `MENURA_WEB_ORIGIN`),
  so the browser receives one `Access-Control-Allow-Origin` and it comes from the code that also checks the session.

The OLF cluster already uses `BackendTrafficPolicy` on other routes. Rate limits are the only edge policy the chart
owns.

## Rate limits

Principle 11 asks for limits per IP, per person and per client, with `429` and `Retry-After`.

- **Per IP and per client: the gateway.** `type: Local` rules with `Distinct` selectors. Each client address gets its
  own budget (IPv4 and IPv6 are separate rules), and each `Authorization` token gets one on the API and MCP routes.
  The web route has no token (the browser holds only the session cookie), so it has the per-IP budget alone.
- **Per person: the server.** The person budget is `PersonRateLimiter` in `server/.../web`, with `menura.ratelimit.person`
  in `application.yml`. Only the server sees the person behind a validated token; a gateway key on the token would be
  per token, not per person, and the gateway would need JWT validation to read the person at all.

With `httpRoute.enabled=false`, for example when the hosts are exposed through a Cloudflare Tunnel Ingress instead, the
chart renders no route and therefore no gateway rate limit: the server's per-person limit is the only limit left.

Caveats for the cluster owner:

- **Local, not global.** The cluster runs no Envoy rate limit service with Redis, so the limits are local to each Envoy
  proxy. The gateway runs as a DaemonSet, so a client that reaches several nodes gets one budget per node. The limits
  are a floor against abuse, not an exact quota.
- **Client address.** Public hosts sit behind the Cloudflare proxy, so the TCP peer is never the visitor. The
  client address comes from `CF-Connecting-IP` on the shared listener (see "Shared listener"). Without it, all
  visitors share one address and the per-IP budget becomes a global one.
- **Token keys.** The `Authorization` value is the key of the per-client budget. Envoy keeps it in memory only, and
  nothing logs it, but each distinct token adds a counter.
- **Gateway 429 body.** Envoy's own refusal is plain text. The `responseOverride` replaces it with Problem Details
  and `Retry-After: 60`, the length of one budget window. The override matches `text/plain` 429s only, so the server's
  Problem Details with its exact `Retry-After` pass through. This matching is an assumption to confirm on the cluster.

## Shared listener (prerequisite, cluster repository)

Menura's security requirements (principle 11) need two settings on the shared `eg` `https` listener in namespace
`envoy`. They apply to every host on that listener, including products that are not Menura, so they are set in the
cluster repository and not by this chart. **Menura's deployment depends on them; set them before the first release
that reaches the public hosts:**

1. **TLS 1.3 only** on the shared `https` listener (`ClientTrafficPolicy` `tls.minVersion: "1.3"`,
   `maxVersion: "1.3"`), per RFC 8446 and RFC 9325;
2. **`CF-Connecting-IP` as the client address** (`clientIPDetection.customHeader`) for every route on that listener.
   Public hosts sit behind the Cloudflare proxy, so without it all visitors share the proxy's address and the per-IP
   budget becomes global. This is safe only while Envoy is reachable solely through the Cloudflare tunnel.

Envoy Gateway requires a `ClientTrafficPolicy` to live in its target's namespace, and only one policy may target the
listener, so the cluster owner writes it once for the whole listener. The Menura chart carries no copy: until the
cluster sets 1 and 2, the public hosts do not meet principle 11.

## Other edge settings

- **CORS only in the server.** `micronaut.server.cors` in `application.yml` allows credentialed requests from the web
  app host only (`MENURA_WEB_ORIGIN` in local development). The gateway adds no CORS headers, so no duplicate
  `Access-Control-Allow-Origin` can be sent. Do not add a CORS `SecurityPolicy` on the shared gateway.
- **HSTS** is `max-age=31536000` without `includeSubDomains`, because other hosts under `onelitefeather.net` are not
  all HTTPS-only.
- **Hosts** are fixed by `check-helm.sh`: exactly the three public hosts and no other host in the chart.

## Object storage (library uploads)

- `storage.*` names the S3 endpoint (`https://s3.onelitefeather.net`), region and the private bucket. The S3 keys come
  from `storage.existingSecret` (keys `MENURA_S3_ACCESS_KEY_ID` and `MENURA_S3_SECRET_ACCESS_KEY`), created outside the
  chart and injected into the server pods and the jobs that read object storage (publication and retention).
- The bucket's CORS rule must allow the web app origin (`https://menura.onelitefeather.net`) for `PUT` and `GET` only,
  with the `x-amz-checksum-sha256` header allowed. No other origin, wildcard or credentialed CORS is allowed (principle 14).
- `library.partSizeBytes` must not exceed 99614720 (95 MiB, principle 15); `check-helm.sh` enforces it.

## Channel connections (operator steps)

Channels are connected through each platform's own consent (change `add-channel-connections`). The chart holds no
secret: the server and the `channel-health` CronJob read them from `channels.existingSecret`, a Secret created outside
the chart with these keys:

| Key                                | Required | Purpose                                                              |
|------------------------------------|----------|----------------------------------------------------------------------|
| `MENURA_TOKEN_ENCRYPTION_KEY`      | yes      | AES-256 key, base64 of 32 bytes. The server does not start without it. |
| `MENURA_YOUTUBE_CLIENT_ID`         | for YouTube | OAuth client id of the YouTube app.                              |
| `MENURA_YOUTUBE_CLIENT_SECRET`     | for YouTube | OAuth client secret of the YouTube app.                          |
| `MENURA_TOKEN_ENCRYPTION_KEY_PREVIOUS` | rotation only | The old key, read for decryption only, while a key rotation runs. |

## Join attribution (operator steps)

The network proxy authenticates to `POST /join-events` with its own credential. The server holds only its SHA-256 hex
digest, read from `joinAttribution.existingSecret` (a Secret created outside the chart) with the key
`MENURA_JOIN_PROXY_CREDENTIAL_HASH`. Create the credential with `openssl rand -base64 32`, put its digest
(`printf '%s' "$SECRET" | sha256sum`) into the Secret, and give the secret itself only to the proxy. Until the key is set,
every join call answers 401. The request budget of the proxy is 600 per minute in the server, and the gateway limits the
`Authorization` token of the API route as well.

**Create the platform app (YouTube).** In Google Cloud, create an OAuth client of type *Web application*. Register the
redirect URI `https://api-menura.onelitefeather.net/channel-connections/callback` (it must equal
`channels.youtube.redirectUri`). Enable the YouTube Data API v3 for the project. Put the client id and secret into the
Secret above. Until a compliance audit of the project passes, Google limits its quota; record that status (next step).

**Record the audit status.** Each platform app has an audit status that an administrator records, with its source,
through `PUT /platforms/{platform}/audit-status` (body `{"status": "unaudited", "source": "<where the decision came
from>"}`; `status` is `unknown`, `unaudited` or `audited`). The web app and the publication dry run show it. An app that
is `unknown` or `unaudited` cannot use public visibility, so record `audited` only after the audit letter. Every
decision is dated and audited.

**Rotate the encryption key.** 1. Generate a new key with `openssl rand -base64 32`. 2. Set the old key as
`MENURA_TOKEN_ENCRYPTION_KEY_PREVIOUS` and the new one as `MENURA_TOKEN_ENCRYPTION_KEY` in the Secret, and roll the
server and the jobs. 3. The next `channel-health` run (every 15 minutes by default) rewrites every grant with the new
key; grants are readable with either key meanwhile. 4. After one successful run, remove `MENURA_TOKEN_ENCRYPTION_KEY_PREVIOUS`
and roll again. A lost key means every channel must be reconnected; the reconnect flow keeps channel history.

**Check the connection.** `kubectl get cronjob menura-channel-health` shows the schedule, and a finished run prints
`channel-health checked=… refreshed=… needs_reconnection=… unavailable=…`. Channels that need reconnection show their
reason in the channel list.

## External posts (operator steps)

The `external-post-sync` CronJob imports the existing posts and videos of every connected channel and re-syncs them
(change `add-external-post-import`). It runs at `jobs.externalPostSync.schedule` (every six hours by default) and skips a
channel whose last complete sync is newer than `jobs.externalPostSync.interval`. It reads the same `channels.existingSecret`
as the channel health job and needs no further key.

**What "removed" means.** A post is marked removed when a complete listing from the platform no longer contains it. The
time of the removal is the time the sync saw it. A removed post keeps its record and its metric history and is never
deleted; if the platform lists it again, it becomes live again.

**An incomplete listing marks nothing.** A listing that fails part-way, is rate limited or reaches its limit of 5000
items adds the items it lists but marks no post removed. The channel overview shows the failure of the last sync.

**Platform support.** A platform adapter that does not list published items reports the channel as failed with the reason
"not listed in this build". The YouTube listing comes with `add-youtube-insights`; until then the YouTube channels show
that reason.

The `metric-collection` CronJob reads the metrics of every external post of the connected channels and takes the snapshots at
fixed offsets after publication (change `add-content-analytics`). It runs at `jobs.metricCollection.schedule` (every 30 minutes
by default), so the +1 hour snapshot is at most half an hour late, and it needs no further key beyond `channels.existingSecret`.
A platform that does not report metrics is recorded as `not_supported`; nothing is written for it.

The `metric-rollup` CronJob rebuilds the daily, weekly and equal-age rollups of every day the collection marked. It runs at
`jobs.metricRollup.schedule` (every quarter hour by default), needs the database only, and prints `metric-rollup days=… weeks=…`.
The dashboard reads the rollups, so it lags the points by at most one run; a day corrected by a platform is rebuilt by the
next run that finds it marked.

### Content analytics storage (operator notes)

Metric points live in PostgreSQL 17 (the standard image, no extension) in `menura.metric_point`, partitioned by the month of
collection. The partition of the next month is created before its first point, by the collection. Raw points are kept; no
retention is defined yet, which is open for the operator. The rollups (`metric_rollup_day`, `metric_rollup_week`) and the
equal-age groups (`metric_offset_day`) are rebuilt by the rollup job, so the dashboard aggregates over two years answer from
them within a second without scanning the points.

Decided against, with the reason: ClickHouse (a second stateful system to operate on the cluster, with slower cold-start
recovery; the rollups meet the speed budget), TimescaleDB (not in the standard OLF PostgreSQL image, so a custom image and
extension upgrades), and unpartitioned tables (they grow for two years without per-month pruning, and every aggregate scans
them).

**Check the import.** `kubectl get cronjob menura-external-post-sync` shows the schedule, and a finished run prints
`external-post-sync checked=… synced=… skipped=… incomplete=… failed=… waiting=…`. Channels that need reconnection are
counted as `waiting` and are not read.
