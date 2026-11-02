# Proposal

Conventional Commits: `feat(auth)` (code PR). Phase: 0 (Foundation). Depends on: `add-project-skeleton`.
Strand: platform.

## Why

Menura holds recordings, transcripts, distribution credentials and live overlay control on one hub. Every person
who uses the web app needs a verified identity and a role that limits what they may do, and every write must be
traceable. People sign in with Entra ID, and the browser must never hold a token. Sign-in and tokens for the
desktop app, the OBS plugin and MCP clients are a separate change, `add-client-and-mcp-auth`, which builds on this one.

## What Changes

- Menura runs its own OAuth authorization server that federates Entra ID as upstream identity provider. Menura issues
  the tokens it accepts; an Entra ID token alone never grants access.
- The web app is a backend-for-frontend: the browser holds only a `__Host-` session cookie, never a token.
- Five roles: viewer, editor (content and brand), publisher (approves distribution), operator (overlay live control),
  admin. Every protected endpoint checks the caller's roles before it performs any action.
- Every access token is validated for signature with a fixed set of accepted algorithms, its expiry and its audience
  before any of its claims is used.
- Calendar feed tokens for the iCalendar subscription (D7 of the product decisions) are 256-bit, shown once, revocable
  and read-only.
- Long-lived secrets are 256-bit random values stored hashed and compared in constant time.
- Objects a caller may not see answer 404; identifiers are random UUIDs that reveal neither order nor count; rate limits
  answer 429 with Retry-After, enforced per IP, per person and per client at the gateway.
- Hosts and transport: the web app, the API, the backend-for-frontend, the authorization server, feeds and the job queue
  and the hub MCP each sit on their own host; credentialed cross-origin requests are allowed only from the web app host;
  TLS 1.3 only, Strict-Transport-Security and Referrer-Policy no-referrer.
- Every write is audit-logged with who, what, when and the channel (web, desktop, OBS plugin, MCP).
- Health and OpenAPI documentation stay anonymous; everything else answers 401 or 403 as Problem Details.

## Capabilities

### New Capabilities

- `access-control`: web sign-in through Menura's authorization server with Entra ID upstream, the backend-for-frontend
  session, roles and role checks, access token validation, calendar feed tokens, secret handling, visibility and
  identifiers, rate limits, host and cross-origin rules, transport, audit logging, anonymous endpoints and Problem
  Details. Device-bound tokens, refresh and revocation, MCP authorization and scene tokens are specified in
  `client-auth` (change `add-client-and-mcp-auth`).

### Modified Capabilities

None (no existing capabilities).

## Impact

- Server: authorization server endpoints for the web sign-in and its metadata, backend-for-frontend session handling,
  access token validation, role checks, audit log table, Problem Details responses, host-bound CORS and cookie
  attributes, rate limit configuration at the gateway.
- Web: sign-in redirect and session cookie only.
- Helm chart: the three HTTPRoutes are created by `add-project-skeleton`; this change sets their CORS, cookie and TLS
  policy.
- Dependencies: an Entra ID app registration for Menura's authorization server.
