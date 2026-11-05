# client-auth

## Purpose

Sign-in and tokens for clients other than the web app: desktop app, OBS plugin, MCP clients and overlay browser sources.

## ADDED Requirements

### Requirement: Loopback PKCE sign-in for desktop and OBS plugin
(Must) The desktop app and the OBS plugin SHALL sign in with the authorization code flow using PKCE over a loopback redirect, and the system SHALL reject a code exchange whose code verifier does not match the challenge.

#### Scenario: Successful loopback sign-in
- **GIVEN** a client that starts sign-in with a PKCE challenge and a loopback redirect
- **WHEN** the browser returns the code and the client presents the matching verifier
- **THEN** the system issues tokens to that client

#### Scenario: Wrong verifier
- **GIVEN** an authorization code issued for a challenge
- **WHEN** the exchange presents a different verifier
- **THEN** the system refuses the exchange and issues no token

### Requirement: Proof-of-possession binding of device tokens
(Must) The system SHALL bind each desktop and OBS plugin token to the client's key pair and SHALL refuse a token presented without a valid proof of possession for that key.

#### Scenario: Token used with its key
- **GIVEN** a desktop token bound to a client key
- **WHEN** the client calls the API with a proof signed by that key
- **THEN** the request is accepted

#### Scenario: Token replayed from another key
- **GIVEN** a desktop token bound to one client key
- **WHEN** the token is presented with a proof from a different key
- **THEN** the system refuses the request with 401

### Requirement: Token keeping on the client
(Must) The desktop app and the OBS plugin SHALL keep their tokens and key material in the operating system keychain and SHALL NOT write them to configuration or log files.

#### Scenario: Token not in config file
- **GIVEN** a signed-in desktop app
- **WHEN** its configuration and log files are searched for the token value
- **THEN** the token value is not found

### Requirement: Refresh token rotation with reuse detection
(Must) Each refresh SHALL issue a new refresh token and invalidate the previous one, and when a previous refresh token is presented again the system SHALL revoke the whole grant.

#### Scenario: Normal rotation
- **GIVEN** a client holding refresh token R1
- **WHEN** it refreshes
- **THEN** it receives R2 and R1 no longer works

#### Scenario: Reuse detected
- **GIVEN** a client that has refreshed from R1 to R2
- **WHEN** R1 is presented again
- **THEN** the system revokes the grant, so R2 also stops working

### Requirement: Token revocation
(Must) The system SHALL revoke a token when a revocation request for it is accepted, and SHALL revoke the refresh tokens of the same grant with it.

#### Scenario: Revocation request
- **GIVEN** an active access token and its refresh token
- **WHEN** the client sends a revocation request for the access token
- **THEN** both tokens are refused afterwards

### Requirement: Device tokens listed and revocable
(Must) The system SHALL list each person's active device grants in the web app and SHALL let that person, or an admin, revoke any one of them.

#### Scenario: Revoked device refused
- **GIVEN** an active device grant of a person
- **WHEN** the person revokes that device in the web app
- **THEN** every later request from that device is refused with 401

#### Scenario: Other devices keep working
- **GIVEN** two active device grants of one person
- **WHEN** one of them is revoked
- **THEN** the other grant remains accepted

### Requirement: MCP authorization per the MCP specification
(Must) MCP clients SHALL authorize through protected resource metadata, authorization server metadata, resource indicators and dynamic client registration, and the system SHALL bind MCP tokens to the MCP resource.

#### Scenario: Client discovers the server
- **GIVEN** an MCP client that receives a 401 from the MCP endpoint
- **WHEN** it fetches the protected resource metadata and the authorization server metadata
- **THEN** it can register dynamically and obtain a token for the MCP resource

#### Scenario: Token for another resource
- **GIVEN** a token issued for a resource other than the MCP endpoint
- **WHEN** it is presented to the MCP endpoint
- **THEN** the system refuses it with 401

### Requirement: MCP tokens limited to the person's roles
(Must) The system SHALL limit an MCP token to the roles of the person who authorised it.

#### Scenario: Editor's MCP client
- **GIVEN** a remote Claude client authorised by a person with the editor role
- **WHEN** the client searches the library and creates a draft timeline
- **THEN** both calls are accepted

#### Scenario: Calls beyond the role refused
- **GIVEN** a remote Claude client authorised by a person with the editor role only
- **WHEN** it calls an operation that approves a distribution
- **THEN** the system refuses the call with 403

### Requirement: MCP tokens never approve publication
(Must) The system SHALL NOT accept an MCP token as the approval of a distribution and SHALL require an interactive web session of a person with the publisher role for every approval.

#### Scenario: Approval through MCP token
- **GIVEN** an MCP token whose person holds the publisher role
- **WHEN** the token is used to approve a post
- **THEN** the system refuses the approval and records no approval

#### Scenario: Approval through web session
- **GIVEN** a person with the publisher role in an interactive web session
- **WHEN** the person approves a post
- **THEN** the system records the approval

### Requirement: Per-scene overlay read token
(Must) The system SHALL give each overlay scene its own 256-bit read-only token, used in the scene's browser-source URL, that grants read access to that scene's feed only.

#### Scenario: Scene feed read
- **GIVEN** a scene with its token in the browser-source URL
- **WHEN** the browser source requests that scene's feed
- **THEN** the system returns the feed

#### Scenario: Token does not reach other scenes
- **GIVEN** the token of scene A
- **WHEN** it is used to read scene B or to change any state
- **THEN** the system refuses the request with 403

### Requirement: Scene token rotation managed by the OBS plugin
(Must) When a scene token is rotated, by the OBS plugin or by a person with the operator role, the system SHALL issue a new token for that scene and SHALL refuse the previous token immediately, and the OBS plugin SHALL receive the new token without displaying it.

#### Scenario: Old token after rotation
- **GIVEN** a scene whose token was rotated
- **WHEN** a browser source uses the previous token
- **THEN** the system refuses the request with 401

#### Scenario: Plugin receives new token
- **GIVEN** the OBS plugin rotates a scene token
- **WHEN** rotation completes
- **THEN** the plugin holds the new token and no UI shows it
