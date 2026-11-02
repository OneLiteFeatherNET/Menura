# Proposal

Conventional Commits: `feat(auth)` (code PR). Phase: 3 (Hub-MCP). Depends on: `add-access-control`.
Strand: platform.

## Why

Clients other than the web app run on personal PCs, inside OBS, or in a remote Claude session. The desktop app and the
OBS plugin must sign in without a secret in the binary and keep their tokens bound to the device, so a copied token
is useless elsewhere. MCP clients such as Claude Code and Claude Desktop must find the authorization server on their
own and receive tokens that carry only the roles of the person who authorised them. Overlay browser sources need a
per-scene read token that the OBS plugin rotates without anyone copying it. The web sign-in and its roles are in
`add-access-control`; this change adds the flows and token lifecycle for every other client.

## What Changes

- Authorization code flow with PKCE over a loopback redirect for the desktop app and the OBS plugin; a code exchange
  with a wrong verifier issues no token.
- Proof-of-possession binding (DPoP) of desktop and OBS plugin tokens to a key the client holds; a token presented with
  another key is refused with 401.
- Client tokens and key material live in the operating system keychain; the authorization server marks token responses
  as not storable.
- Refresh tokens rotate on every use; presenting a used refresh token revokes the whole grant.
- Revocation endpoint (RFC 7009) that refuses an access token and the refresh tokens of its grant from then on.
- Each person sees and revokes their own device grants in the web app; an admin may revoke any of them.
- MCP authorization per the MCP specification: protected resource metadata (RFC 9728), authorization server metadata
  (RFC 8414), dynamic client registration (RFC 7591) and resource-bound tokens (RFC 8707).
- An MCP token carries the roles of the person who authorised it, never more. An MCP token cannot approve a distribution;
  approval requires an interactive web session of a person with the publisher role.
- Per-scene overlay read tokens: 256-bit, hashed at rest, valid only for their own scene's feed.
- Scene tokens are rotated by the OBS plugin, or by an operator; the old token is refused at once and the new token
  reaches the OBS plugin without being displayed.

The overlay feed itself (`add-overlay-system`) and the approval operation (`add-content-distribution`) are later
changes. This change provides the token and authorization checks they call.

## Capabilities

### New Capabilities

- `client-auth`: loopback PKCE sign-in for desktop and OBS plugin, proof-of-possession binding, token keeping on the
  client, refresh rotation with reuse detection, revocation, device grant listing, MCP authorization and its role
  limits, the approval block for MCP tokens, and per-scene overlay read tokens with rotation.

### Modified Capabilities

None (no existing capabilities).

## Impact

- Server: authorization endpoints for loopback PKCE, token and revocation endpoints, DPoP proof validation, grant store
  with rotation state, protected resource and authorization server metadata, dynamic client registration, scene token
  store and rotation endpoint, approval guard used by the distribution change.
- Web: device grant list and revoke page.
- Desktop app and OBS plugin: clients of these flows; their own changes (`add-local-transcription`, `add-obs-plugin`)
  implement the client side and verify the keychain rule.
- MCP: `add-hub-mcp-server` uses the metadata, registration and token checks defined here.
