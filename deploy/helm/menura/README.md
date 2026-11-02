# Menura chart: edge policies

The chart renders the three public routes and the policies that Envoy Gateway applies to them. The gateway is the
shared `eg` Gateway of the cluster repository; the chart does not own it. `deploy/check-helm.sh` asserts the rendered
policies.

| Resource | Target | What it does |
|---|---|---|
| `HTTPRoute` (x3) | host | Forwards to the Service; `ResponseHeaderModifier` sets HSTS, `Referrer-Policy: no-referrer` and `nosniff` |
| `BackendTrafficPolicy` (x3) | route | Local rate limits per client address, and per `Authorization` token on the API and MCP routes; the gateway's 429 answers Problem Details with `Retry-After` |
| `SecurityPolicy` (x1) | API route | Credentialed CORS for `https://menura.onelitefeather.net` only |
| `ClientTrafficPolicy` (x1) | shared `https` listener | TLS 1.3 only; client address from the `CF-Connecting-IP` header |

Envoy Gateway resource kinds (`gateway.envoyproxy.io/v1alpha1`): `BackendTrafficPolicy`, `SecurityPolicy` and
`ClientTrafficPolicy`. The OLF cluster installs all three CRDs and already uses `BackendTrafficPolicy` and
`SecurityPolicy` on other routes. Nothing there uses `ClientTrafficPolicy` or a rate limit yet, so those two are new
to the cluster.

## Rate limits

Principle 11 asks for limits per IP, per person and per client, with `429` and `Retry-After`.

- **Per IP and per client: the gateway.** `type: Local` rules with `Distinct` selectors. Each client address gets its
  own budget (IPv4 and IPv6 are separate rules), and each `Authorization` token gets one on the API and MCP routes.
  The web route has no token (the browser holds only the session cookie), so it has the per-IP budget alone.
- **Per person: the server.** The person budget is `PersonRateLimiter` in `server/.../web`, with `menura.ratelimit.person`
  in `application.yml`. Only the server sees the person behind a validated token; a gateway key on the token would be
  per token, not per person, and the gateway would need JWT validation to read the person at all.

Caveats for the cluster owner:

- **Local, not global.** The cluster runs no Envoy rate limit service with Redis, so the limits are local to each Envoy
  proxy. The gateway runs as a DaemonSet, so a client that reaches several nodes gets one budget per node. The limits
  are a floor against abuse, not an exact quota.
- **Client address.** Public hosts sit behind the Cloudflare proxy, so the TCP peer is never the visitor. The
  `ClientTrafficPolicy` reads `CF-Connecting-IP` (`failClosed` is off, so a request without it falls back to the peer
  address). This is only safe while Envoy is reachable solely through the Cloudflare tunnel. Without it, all visitors
  share one address and the per-IP budget becomes a global one.
- **Token keys.** The `Authorization` value is the key of the per-client budget. Envoy keeps it in memory only, and
  nothing logs it, but each distinct token adds a counter.
- **Gateway 429 body.** Envoy's own refusal is plain text. The `responseOverride` replaces it with Problem Details
  and `Retry-After: 60`, the length of one budget window. The override matches `text/plain` 429s only, so the server's
  Problem Details with its exact `Retry-After` pass through. This matching is an assumption to confirm on the cluster.

## Shared listener

The `ClientTrafficPolicy` targets the shared `eg` `https` listener, so it applies to every host on that listener,
including products that are not Menura. Envoy Gateway requires a policy to live in its target's namespace, so the chart
renders it into the gateway namespace (`envoy`), not the release namespace. The cluster owner must agree to:

1. TLS 1.3 only on the shared `https` listener;
2. `CF-Connecting-IP` as the client address for every route on that listener;
3. one `ClientTrafficPolicy` per listener. A second one would conflict.

Until 1 to 3 are agreed, the policy belongs in the cluster repository. The chart's copy is the spec.

## Other edge settings

- **CORS in two places.** The server answers CORS (`micronaut.server.cors` in `application.yml`) and so does the
  `SecurityPolicy`. Both allow the same origin only. Check on the cluster that the gateway and the server do not send
  two `Access-Control-Allow-Origin` headers for the same request.
- **HSTS** is `max-age=31536000` without `includeSubDomains`, because other hosts under `onelitefeather.net` are not
  all HTTPS-only.
- **Hosts** are fixed by `check-helm.sh`: exactly the three public hosts and no other host in the chart.
