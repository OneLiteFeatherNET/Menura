# Tasks

## 1. Loopback PKCE sign-in

- [ ] 1.1 Write failing tests that a sign-in started with an S256 challenge and a loopback redirect yields a code, that the exchange with the matching verifier issues tokens, that a wrong verifier issues no token, and that a non-loopback redirect is refused; verify they fail
- [ ] 1.2 Implement the authorization code flow with PKCE (S256) and loopback redirect validation for desktop and OBS plugin, and verify the tests of 1.1 pass

## 2. Proof of possession and token keeping

- [ ] 2.1 Write failing tests that a token used with a proof signed by its own key is accepted, that a proof from another key is refused with 401, and that a proof with a stale time or a reused identifier is refused, using an injected clock and injected keys; verify they fail
- [ ] 2.2 Implement DPoP proof validation and key binding of desktop and OBS plugin tokens, and verify the tests of 2.1 pass
- [ ] 2.3 Write failing tests that token responses carry `Cache-Control: no-store` and that no token value appears in server logs or traces during sign-in and refresh; verify they fail
- [ ] 2.4 Implement the no-store header and token redaction in the token flow, and verify the tests of 2.3 pass
- [ ] 2.5 Record the keychain and no-token-in-files criteria as the acceptance checklist of `add-local-transcription` and `add-obs-plugin` in the pull request description, and verify the checklist is present there

## 3. Refresh rotation and revocation

- [ ] 3.1 Write failing tests that a refresh returns a new refresh token R2 and that R1 is refused afterwards, and that presenting R1 again revokes the grant so that R2 is refused too, using an injected clock; verify they fail
- [ ] 3.2 Implement refresh rotation with reuse detection, and verify the tests of 3.1 pass
- [ ] 3.3 Write failing tests that a revocation request refuses the access token and every refresh token of its grant, that revoking an already revoked token answers success, and that a token of another grant keeps working; verify they fail
- [ ] 3.4 Implement the revocation endpoint (RFC 7009) for access and refresh tokens, and verify the tests of 3.3 pass

## 4. Device grants in the web app

- [ ] 4.1 Write failing tests that the web app lists a person's active device grants, that revoking one refuses its later requests with 401 while the other grant keeps working, and that an admin can revoke another person's grant; verify they fail
- [ ] 4.2 Implement device grant listing and revocation in the API and the web app, and verify the tests of 4.1 pass

## 5. MCP authorization and approval block

- [ ] 5.1 Write failing tests that an MCP request without a token answers 401 with a pointer to the protected resource metadata, that the metadata leads to the authorization server metadata with a registration endpoint, and that a token whose resource indicator names another resource is refused with 401; verify they fail
- [ ] 5.2 Implement protected resource metadata (RFC 9728), authorization server metadata (RFC 8414), dynamic client registration (RFC 7591) and resource-bound tokens (RFC 8707), and verify the tests of 5.1 pass
- [ ] 5.3 Write failing tests that an MCP token carries the roles of the authorising person, that an editor's MCP client is refused a publisher operation with 403, and that a publisher's MCP token is refused by the approval guard with no approval recorded; verify they fail
- [ ] 5.4 Implement the MCP token role mapping and the approval guard that requires an interactive web session with the publisher role, and verify the tests of 5.3 pass

## 6. Per-scene overlay tokens

- [ ] 6.1 Write failing tests that a scene token is 256 bits, that it is stored only as a hash, that it reads its own scene's feed, that using it on another scene or for any state change answers 403, and that a rotated token answers 401 at once; verify they fail
- [ ] 6.2 Implement per-scene 256-bit read tokens with the scope guard for the feed, and verify the scope tests of 6.1 pass
- [ ] 6.3 Write failing tests that an OBS plugin session or an operator can rotate a scene token, that the new token is returned only to the rotating caller, and that neither the audit log nor any log line contains the token value; verify they fail
- [ ] 6.4 Implement scene token rotation with immediate refusal of the previous token, and verify the tests of 6.3 pass

## 7. Integration and delivery

- [ ] 7.1 Run the integration tests tagged `integration` against PostgreSQL and a fake authorization client under the heavy-job lock, and verify loopback PKCE exchange, DPoP-bound token use, refresh reuse revocation, MCP discovery and scene rotation end to end
- [ ] 7.2 Open the code pull request titled `feat(auth): add client and mcp auth`
