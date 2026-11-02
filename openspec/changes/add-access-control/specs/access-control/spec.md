# Spec Delta

## Purpose

Defines who may use Menura and how: sign-in through Menura's own authorization server federating Entra ID, the backend-for-frontend web session, device-bound and proof-of-possession tokens for the desktop app and OBS plugin, refresh and revocation, MCP authorization, roles, scene and calendar tokens, secret handling, object visibility, rate limits, transport security and audit logging.

## ADDED Requirements

### Requirement: Sign-in through Menura's authorization server
(Must) The system SHALL authenticate every person through Menura's own OAuth authorization server, which SHALL use Entra ID as its upstream identity provider and SHALL NOT issue client tokens from the upstream token alone.

#### Scenario: Unauthenticated visitor
- **GIVEN** a visitor without a valid web session
- **WHEN** the visitor opens a protected page of the web app
- **THEN** the visitor is redirected to sign-in through the authorization server, which federates to Entra ID

#### Scenario: Successful sign-in
- **GIVEN** a person who signs in through Entra ID and holds Menura roles
- **WHEN** the sign-in completes
- **THEN** the authorization server issues Menura tokens carrying exactly those roles

### Requirement: Web app as backend-for-frontend
(Must) The web app SHALL hold its tokens on the server side and SHALL give the browser only a session cookie with the `__Host-` prefix, never an access or refresh token.

#### Scenario: No token in browser
- **GIVEN** a signed-in person using the web app
- **WHEN** the browser storage and responses are inspected
- **THEN** no access or refresh token is present, and only a `__Host-` session cookie is set

### Requirement: Five roles
(Must) The system SHALL assign each person one or more of the roles viewer, editor, publisher, operator and admin, and SHALL grant only the rights of the assigned roles.

#### Scenario: Viewer cannot write
- **GIVEN** a person whose only role is viewer
- **WHEN** the person attempts to create or change a content item
- **THEN** the system refuses the request with 403

#### Scenario: Operator switches a module
- **GIVEN** a person with the operator role
- **WHEN** the person switches a module in a live overlay scene
- **THEN** the system accepts the change

### Requirement: Role check on every request
(Must) When a request reaches a protected endpoint, the system SHALL check the caller's roles against the rights required by that endpoint before performing any action.

#### Scenario: Missing right
- **GIVEN** a caller whose roles do not include the right an endpoint requires
- **WHEN** the caller invokes that endpoint
- **THEN** the system performs no action and refuses the request with 403

### Requirement: Access token validation
(Must) The system SHALL validate every access token's signature with a fixed set of accepted algorithms, its expiry and its audience before using any of its claims.

#### Scenario: Wrong audience
- **GIVEN** a token issued for a different resource
- **WHEN** it is presented to Menura
- **THEN** the system refuses it with 401

### Requirement: Calendar feed token
(Must) The system SHALL give each person a 256-bit, read-only calendar feed token for the iCalendar subscription, shown once when created, and SHALL let the person rotate or revoke it.

#### Scenario: Feed with token
- **GIVEN** a person's current calendar feed token
- **WHEN** a calendar client fetches the feed with it
- **THEN** the system returns the feed

#### Scenario: Revoked feed token
- **GIVEN** a feed token the person has revoked
- **WHEN** a calendar client fetches the feed with it
- **THEN** the system refuses the request with 401

### Requirement: Secrets stored hashed and compared in constant time
(Must) The system SHALL generate every long-lived secret as a 256-bit value from a cryptographically secure random source, SHALL store only its hash, and SHALL compare presented secrets in constant time.

#### Scenario: Stored secret not recoverable
- **GIVEN** a stored device, scene or feed secret
- **WHEN** the database row is read
- **THEN** only a hash of the secret is present

### Requirement: Secrets never in output
(Must) The system SHALL keep tokens, secrets and scene tokens out of logs, traces, error reports and API and MCP responses.

#### Scenario: Scene URL in log
- **GIVEN** a request that carries a scene token in its URL
- **WHEN** the request is logged
- **THEN** the token is redacted in the log entry

### Requirement: Objects invisible to the caller answer 404
(Must) When a caller may not see an object, the system SHALL answer 404 as if the object did not exist.

#### Scenario: Hidden recording
- **GIVEN** a recording the caller may not see
- **WHEN** the caller requests it
- **THEN** the system answers 404

### Requirement: Non-sequential identifiers
(Must) The system SHALL issue identifiers as UUIDs per RFC 9562 that reveal neither order nor count of objects.

#### Scenario: Consecutive creations
- **GIVEN** two recordings created one after the other
- **WHEN** their identifiers are compared
- **THEN** the identifiers do not follow a visible sequence

### Requirement: Rate limits with Retry-After
(Must) The system SHALL limit request rates per IP address, per person and per client, and SHALL answer exceeded limits with 429 and a Retry-After header.

#### Scenario: Limit exceeded
- **GIVEN** a client that exceeds its request rate limit
- **WHEN** it sends another request
- **THEN** the system answers 429 with Retry-After

### Requirement: Dedicated hosts and strict cross-origin rules
(Must) The system SHALL serve the web app only from menura.onelitefeather.net, the API, backend-for-frontend, authorization server, feeds and job queue only from api-menura.onelitefeather.net, and the hub MCP only from mcp-menura.onelitefeather.net, and SHALL allow credentialed cross-origin requests to the API only from the web app host.

#### Scenario: Web app calls the API
- **GIVEN** a signed-in person in the web app on menura.onelitefeather.net
- **WHEN** the web app calls the API on api-menura.onelitefeather.net with its session cookie
- **THEN** the API answers and allows exactly that origin

#### Scenario: Foreign origin refused
- **GIVEN** a page on any other origin
- **WHEN** it sends a credentialed request to the API
- **THEN** the response carries no cross-origin permission for that origin and the browser blocks it

#### Scenario: Session cookie bound to the API host
- **GIVEN** a successful web sign-in
- **WHEN** the backend-for-frontend sets the session cookie
- **THEN** the cookie is host-only on api-menura.onelitefeather.net with the __Host- prefix, Secure, HttpOnly and SameSite=Strict

#### Scenario: MCP discovery points to the API host
- **GIVEN** an MCP client without a token
- **WHEN** it calls mcp-menura.onelitefeather.net
- **THEN** the protected resource metadata names the authorization server on api-menura.onelitefeather.net

### Requirement: Transport security
(Must) The system SHALL accept connections only over TLS 1.3, SHALL send Strict-Transport-Security on web and API responses, and SHALL send Referrer-Policy no-referrer on web pages.

#### Scenario: Old TLS refused
- **GIVEN** a client that offers only TLS 1.2
- **WHEN** it connects
- **THEN** the handshake fails

#### Scenario: HSTS sent
- **GIVEN** a response from the web app
- **WHEN** the headers are read
- **THEN** Strict-Transport-Security and Referrer-Policy no-referrer are present

### Requirement: Audit log of every write
(Must) The system SHALL record an audit entry for every write, containing the acting person, the action, the time and the channel used (web, desktop, OBS plugin or MCP).

#### Scenario: Write from OBS plugin
- **GIVEN** an OBS plugin with a token of a person with the editor role
- **WHEN** it creates a recording session
- **THEN** one audit entry records that person, the action, the time and the channel OBS plugin

#### Scenario: Write from MCP
- **GIVEN** an MCP client acting for a person
- **WHEN** the client creates a clip
- **THEN** one audit entry records the channel MCP

### Requirement: Anonymous access limited to health and API docs
(Should) The system SHALL allow anonymous access to the health endpoint and the OpenAPI documentation only.

#### Scenario: Anonymous library request
- **GIVEN** a request without credentials
- **WHEN** it calls a library endpoint
- **THEN** the system refuses the request with 401

### Requirement: Errors as Problem Details
(Should) When a request is refused for missing credentials, missing rights or rate limits, the system SHALL answer 401, 403 or 429 respectively, with a body in RFC 9457 Problem Details format.

#### Scenario: Missing credentials
- **GIVEN** a request to a protected endpoint without a token
- **WHEN** it is processed
- **THEN** the system answers 401 with a Problem Details body
