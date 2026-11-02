# Design

## Context

Menura has no authentication yet. Principle 6 fixes Entra ID as the identity of people. This change covers the people
who sign in to the web app. The desktop app, the OBS plugin and MCP clients share the same authorization server but
their flows, device binding and token lifecycle are decided in `add-client-and-mcp-auth`. Principle 11 sets the
standards the design must meet. See proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- One identity source (Entra ID) for people, with Menura controlling the tokens it issues and accepts.
- The browser never holds a token; a script injection cannot take a credential away.
- Hidden objects, identifiers and error answers reveal nothing an outsider could use.

**Non-Goals:**
- Sign-in, device-bound tokens, refresh, revocation, MCP authorization and scene tokens (see `add-client-and-mcp-auth`).
- Personal API keys for people and any local user database in Menura.
- Fine-grained per-object permissions beyond the five roles.
- Direct Entra ID tokens for any Menura client.
- Writing to external calendars (the feed is read-only).

## Decisions

- **Menura runs its own authorization server, federating Entra ID.** Menura issues tokens whose audience and lifetime
  it controls; Entra ID still authenticates people. The same server later gives the other clients and the MCP endpoint
  their flows (see `add-client-and-mcp-auth`), which an upstream identity provider cannot be relied on to provide.
  Rejected: Entra ID tokens used directly by the web app (no control over audience, lifetime or revocation).
- **Web app as backend-for-frontend.** The server holds the tokens and the browser holds only a `__Host-` session
  cookie (host-only, Secure, HttpOnly, SameSite=Strict). Tokens never reach JavaScript.
  Rejected: tokens in the single-page app (readable by any injected script).
- **Access tokens are validated before any claim is read.** The signature is checked against a fixed list of accepted
  algorithms, the expiry and the audience must match. The algorithm named in the token header is never trusted.
  Rejected: accepting whatever algorithm the token declares (algorithm confusion, RFC 8725).
- **Role check before any action.** The endpoint declares the roles it needs; the check runs before the handler, so a
  refused request performs no action at all.
  Rejected: checks inside each handler (one forgotten check exposes a write).
- **Secrets are hashed; comparison is constant time.** A database leak does not reveal usable calendar feed secrets,
  and the same rule applies to the device and scene secrets of `add-client-and-mcp-auth`.
  Rejected: encrypted-at-rest secrets (key management without a gain for values that only need comparison).
- **Calendar feed token is shown once and rotatable.** A calendar client cannot sign in through the browser, so it
  holds a read-only token; the person sees it once and can rotate or revoke it.
  Rejected: the feed behind the web session (calendar clients cannot run that sign-in).
- **Hidden objects answer 404, visible objects without the right answer 403.** A caller cannot learn that a hidden
  object exists.
  Rejected: 403 for everything (reveals which identifiers exist).
- **Random UUIDs (version 4) as identifiers.** RFC 9562 version 4 values come from a cryptographic random source and
  carry no time or counter, so neither order nor rate of creation shows.
  Rejected: UUIDv7 (embeds the creation time, which reveals order and rate) and database sequences (reveal count).
- **Rate limits and TLS at the gateway (Envoy Gateway).** Central enforcement per IP, and per person and client where
  the validated token claims are available at the gateway; the server counts where the gateway cannot see the claim.
  Rejected: per-application limits only (each service would need its own counters).
- **Host-bound CORS and cookies.** Credentialed cross-origin requests to the API are allowed only from the web app
  host; every other origin receives no cross-origin permission. The session cookie is bound to the API host.
  Rejected: a wildcard or reflected origin list (any page could then send credentialed requests).
- **Audit entry in the same transaction as the write.** A write without its audit entry cannot commit.
  Rejected: asynchronous audit events (a crash could lose the trace of a committed write).

## Risks / Trade-offs

- [Risk] Compromise of the authorization server issues tokens for anyone → Mitigation: signing keys in the cluster
  secret store, short token lifetime, audit log of every issuance and refusal.
- [Risk] Entra ID outage blocks new web sign-ins → Mitigation: existing web sessions keep working until they expire;
  only new sign-ins need Entra ID.
- [Risk] Session cookie theft → Mitigation: `__Host-` prefix, Secure, HttpOnly, SameSite=Strict, host-only, and the
  token never leaves the server.
- [Risk] Calendar feed URL leaks → Mitigation: revocable and rotatable token, read-only feed without personal data
  beyond planned posts.
- [Risk] Gateway rate limits by person need claims the gateway cannot read → Mitigation: the server applies the
  person-level budget itself (see Open Questions).

## Open Questions

- Which Entra ID group claims map to the five roles, and who maintains that mapping (decided before task 1.2).
- Whether the per-person budget lives at the gateway (claim-based descriptors) or in the server. Both satisfy the
  spec; the choice is recorded when the gateway configuration is written (task 4.6).
