# Design

## Context

`add-access-control` gives Menura its own authorization server, federating Entra ID, and the backend-for-frontend
session of the web app. This change adds the clients that cannot use that session: the desktop app and the OBS plugin
on personal PCs, MCP clients (Claude Code, Claude Desktop, remote sessions), and overlay browser sources in OBS. It
implements the requirements of capability `client-auth` (see `specs/client-auth/spec.md`). Principle 6 fixes the
flows (authorization code with PKCE over loopback, DPoP, the MCP authorization specification). Principle 4 fixes that
nothing is approved by an agent. Principle 11 fixes the secret and token standards (RFC 9700, RFC 8725, RFC 7009).

## Goals / Non-Goals

**Goals:**
- A token stolen from one PC, browser or MCP client is useless on another device.
- Every standard that MCP clients depend on is met without custom protocol work.
- An MCP client can never do what its person cannot, and can never approve or publish.

**Non-Goals:**
- The desktop app and OBS plugin client code. They are implemented in `add-local-transcription` and `add-obs-plugin`;
  this change ships the server side and the checks they rely on.
- The overlay feed endpoint (`add-overlay-system`) and the approval operation (`add-content-distribution`). This change
  ships the scene token check and the approval guard they call; the tests call the guards directly.
- The MCP tool surface (`add-hub-mcp-server`).

## Decisions

- **Authorization code with PKCE (S256) over a loopback redirect for desktop and OBS plugin.** Both clients can open a
  browser for sign-in and hold no secret, so a public-client flow suffices. The server accepts only loopback redirect
  addresses for these clients.
  Rejected: device authorization flow (the clients can open a browser, and it adds a code the person must type).
- **DPoP proof of possession (RFC 9449).** Each device token is bound to a key pair the client generates and keeps in
  the keychain. A proof signed by that key must accompany every call, so a copied token alone does not work.
  Rejected: plain bearer tokens (a copied token works anywhere until it expires).
- **Token keeping in the operating system keychain.** Tokens and key material never go to configuration or log files.
  The server answers token responses with `Cache-Control: no-store`, and the rule becomes a conformance criterion that
  `add-local-transcription` and `add-obs-plugin` verify on their side.
  Rejected: an encrypted file next to the configuration (the key would sit beside it, and a reinstall loses it).
- **Refresh rotation with reuse detection.** Each refresh issues a new refresh token and retires the previous one. A
  retired token presented again means theft, so the whole grant is revoked.
  Rejected: long-lived non-rotating refresh tokens.
- **Revocation per RFC 7009 revokes the grant.** An accepted revocation request refuses the access token and every
  refresh token of the same grant. Revoking an already revoked token still answers success, as the RFC requires.
- **Device grants listed and revocable in the web app.** The grant is the unit a person can see and revoke, because the
  person knows the device, not the token. An admin may revoke any grant.
  Rejected: revoking by token identifier only (people cannot tell which token belongs to which PC).
- **Secrets are hashed; comparison is constant time.** Device grant references, scene tokens and feed tokens are stored
  as hashes and compared in constant time, as decided in `add-access-control`. Scene and device secrets are random
  values of 256 bits.
  Rejected: encrypted-at-rest secrets (key management without a gain for values that only need comparison).
- **MCP authorization uses the standards and the authorization server of `add-access-control`.** The MCP endpoint
  answers 401 with a pointer to its protected resource metadata; the metadata names the authorization server; clients
  register dynamically (RFC 7591) and obtain a token whose resource indicator (RFC 8707) names the MCP endpoint. Menura
  is the one issuer, so no client talks to Entra ID directly.
  Rejected: a separate authorization server for MCP (a second place to revoke and audit).
- **MCP token limited to the authorising person's roles.** The token carries the person's roles and a channel marker
  `mcp`. Role checks treat the marker as a restriction, never as a grant.
  Rejected: MCP tokens with a fixed client role (an editor's client could then act as a viewer's or an admin's would).
- **No MCP token can approve.** The approval guard requires the interactive web session channel and the publisher
  role. The guard refuses any token with the `mcp` marker, whatever its roles.
  Rejected: an approval tool that refuses MCP tokens by name (a new tool or a misconfigured role would bypass it).
- **Scene token per overlay scene, managed by the OBS plugin.** Each scene has its own 256-bit read token that is used
  only in the browser-source URL and reads only its feed. The OBS plugin writes the URL and rotates the token itself
  (D5), so no person copies a token into OBS.
  Rejected: manual copy of the scene URL into OBS (people paste it into chats and screenshots).
- **Rotation answers the new token only to the rotating caller.** The rotation response goes to the OBS plugin session
  or the operator who rotated; the token is never written to an audit entry or a log line.
  Rejected: showing the new token in the web app (a viewer of the page could copy it).

## Risks / Trade-offs

- [Risk] A loopback port is in use and blocks sign-in on a PC → Mitigation: the client tries a small set of ports and
  reports the port problem in its setup wizard; the server accepts any loopback port in the registered set.
- [Risk] Key loss after reinstalling a PC → Mitigation: the person revokes the device grant in the web app; signing in
  again creates a new key.
- [Risk] Stolen refresh token used in parallel with the real client → Mitigation: reuse detection revokes the whole
  grant on the first replay; the person signs in again.
- [Risk] Refresh grants keep working while the upstream identity is disabled → Mitigation: access tokens have a short
  lifetime, and an admin revokes the grant in the web app; the server checks the person's roles on each request.
  (Assumption, to be confirmed in the Open Questions.)
- [Risk] A scene token leaks from a browser-source URL → Mitigation: it reads one scene's feed only, it is rotated at
  once, and the plugin rotates it on request.
- [Trade-off] Every desktop and OBS request carries a proof, which costs one signature per call; accepted because the
  signature is cheap next to the media work.

## Open Questions

- Access token lifetime and refresh lifetime (idle and absolute) are set at apply time and recorded here; the spec does
  not fix them.
- Whether refresh grants are re-checked against Entra ID at each refresh or only at sign-in. The design assumes only at
  sign-in plus the role check on each request; confirm before task 3.2.
- Client identifiers for Claude Code and Claude Desktop: dynamic registration covers them, but the list of allowed
  redirect URIs for those clients is confirmed before task 5.2.
