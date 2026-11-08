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
  chart and injected into the server pods only.
- The bucket's CORS rule must allow the web app origin (`https://menura.onelitefeather.net`) for `PUT` and `GET` only,
  with the `x-amz-checksum-sha256` header allowed. No other origin, wildcard or credentialed CORS is allowed (principle 14).
- `library.partSizeBytes` must not exceed 99614720 (95 MiB, principle 15); `check-helm.sh` enforces it.
