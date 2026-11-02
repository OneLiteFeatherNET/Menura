# Tasks

## 1. Authorization server and web sign-in

- [ ] 1.1 Write failing tests that an unauthenticated request to a protected web page is redirected to sign-in and that a federated Entra ID identity (fake upstream provider) receives exactly the roles its mapping gives; verify they fail
- [ ] 1.2 Implement the authorization server with Entra ID as upstream provider and the role mapping, and verify the tests of 1.1 pass
- [ ] 1.3 Write failing tests that a web response carries only a `__Host-` session cookie and no access or refresh token, and that the session state lives on the server; verify they fail
- [ ] 1.4 Implement the backend-for-frontend session with server-held tokens, and verify the tests of 1.3 pass

## 2. Roles and token validation

- [ ] 2.1 Write failing tests that a viewer's write is refused with 403 and changes nothing, and that a caller missing the role an endpoint declares is refused before the handler runs; verify they fail
- [ ] 2.2 Implement the five roles and the role check on every protected endpoint, and verify the tests of 2.1 pass
- [ ] 2.3 Write failing tests that tokens with a wrong audience, an algorithm outside the accepted list (including `none`) or an expired expiry are refused with 401, using an injected clock; verify they fail
- [ ] 2.4 Implement access token validation with pinned algorithms, expiry and audience check before any claim is read, and verify the tests of 2.3 pass

## 3. Calendar feed tokens and secrets

- [ ] 3.1 Write failing tests that a calendar feed token is shown once, returns the feed while valid, and is refused with 401 after revocation or rotation; verify they fail
- [ ] 3.2 Implement 256-bit calendar feed tokens with rotation and revocation, and verify the tests of 3.1 pass
- [ ] 3.3 Write failing tests that stored secrets contain only a hash, that generation uses a cryptographically secure random source with 256 bits, and that comparison uses constant-time equality; verify they fail
- [ ] 3.4 Implement hashed secret storage, secret generation and constant-time comparison, and verify the tests of 3.3 pass

## 4. Visibility, identifiers, limits and transport

- [ ] 4.1 Write failing tests that a hidden object answers 404 and a visible object without the required role answers 403, both as Problem Details; verify they fail
- [ ] 4.2 Implement the visibility rule for object reads, and verify the tests of 4.1 pass
- [ ] 4.3 Write failing tests that identifiers are RFC 9562 version 4 UUIDs from an injected secure random source and that consecutive creations show no visible sequence or time component; verify they fail
- [ ] 4.4 Implement version 4 UUID identifiers for all resources, and verify the tests of 4.3 pass
- [ ] 4.5 Write failing tests that an exceeded rate limit answers 429 with a Retry-After header as Problem Details; verify they fail
- [ ] 4.6 Configure per-IP, per-person and per-client rate limits at the gateway (recording where the person-level budget is enforced, see design.md), and verify the tests of 4.5 pass
- [ ] 4.7 Write failing tests that logs and traces redact the session cookie, the calendar feed token and other secrets, and that a TLS 1.2-only client cannot complete a handshake; verify they fail
- [ ] 4.8 Implement log redaction, TLS 1.3 only, Strict-Transport-Security and Referrer-Policy no-referrer, and verify the tests of 4.7 pass
- [ ] 4.9 Write failing tests that the API allows credentialed cross-origin requests only from menura.onelitefeather.net, that the session cookie is host-only with the `__Host-` prefix on api-menura.onelitefeather.net, and that the authorization server issuer on the API host is the one named in the configuration; verify they fail
- [ ] 4.10 Implement the host-bound CORS policy and cookie attributes, configure the CORS, cookie and TLS policy of the three HTTPRoutes created in `add-project-skeleton`, and verify the tests of 4.9 pass

## 5. Audit log and anonymous endpoints

- [ ] 5.1 Write failing tests that every write produces exactly one audit entry with person, action, time and channel in the same transaction, and that a rolled-back write leaves no audit entry; verify they fail
- [ ] 5.2 Implement the audit log table and the audit write for every write endpoint, and verify the tests of 5.1 pass
- [ ] 5.3 Write failing tests that health and the OpenAPI document are anonymous, that a protected test endpoint without credentials answers 401 as Problem Details, and that 401, 403 and 429 bodies are Problem Details (library endpoints arrive later); verify they fail
- [ ] 5.4 Implement the anonymous allow-list and the Problem Details responses for 401, 403, 404 and 429, and verify the tests of 5.3 pass

## 6. Integration and delivery

- [ ] 6.1 Run the integration tests tagged `integration` against PostgreSQL and a fake Entra ID under the heavy-job lock, and verify sign-in, role mapping, the backend-for-frontend cookie, host-bound CORS and calendar feed revocation end to end
- [ ] 6.2 Open the code pull request titled `feat(auth): add access control`
