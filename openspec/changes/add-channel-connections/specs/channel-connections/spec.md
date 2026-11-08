## Purpose

Connection of external platform channels to Menura through each platform's own consent, with minimal scopes, protected tokens, automatic refresh, visible health and a common adapter contract for publishing and analytics.

## ADDED Requirements

### Requirement: Connect through platform consent
(Must) When an administrator or publisher starts a channel connection, the system SHALL send them to the platform's own consent screen and SHALL accept the connection only after the platform returns a successful grant to Menura.

#### Scenario: Successful connection
- **GIVEN** a publisher is signed in to the web app
- **WHEN** the publisher starts a connection for a YouTube channel and approves the requested scopes on the platform's consent screen
- **THEN** the channel appears in the channel list as connected with the scopes that were granted

#### Scenario: Consent refused
- **GIVEN** a publisher starts a connection
- **WHEN** the platform reports that consent was refused or cancelled
- **THEN** no channel is created or changed and the publisher sees the refusal reason

#### Scenario: Credentials cannot be entered
- **GIVEN** a publisher opens the channel creation screen
- **WHEN** the publisher looks for a field to type or paste a token, key or password
- **THEN** no such field exists and the only connection action is the platform consent

### Requirement: Minimal scopes per platform
(Must) The system SHALL request only the scopes that the platform adapter declares as needed for the features the channel is enabled for, and SHALL NOT request additional scopes at connection time.

#### Scenario: Feature enabled later
- **GIVEN** a channel is connected with the scopes for publishing only
- **WHEN** an administrator enables a feature that needs an additional scope
- **THEN** the system asks for that scope through a new consent before the feature is used, and does not use it before the grant

#### Scenario: Scopes shown
- **GIVEN** a connected channel
- **WHEN** a publisher opens the channel view
- **THEN** the granted scopes are listed and any missing scope for an enabled feature is flagged

### Requirement: Tokens protected at rest and never returned
(Must) The system SHALL store access and refresh tokens encrypted at rest and SHALL NOT return, log or export any token through an API, an MCP tool, a feed or a log line.

#### Scenario: Channel read by API
- **GIVEN** a connected channel
- **WHEN** a client reads the channel through the API or an MCP tool
- **THEN** the response contains connection status, scopes and expiry times, and no token value or token fragment

#### Scenario: Error output
- **GIVEN** a token refresh fails
- **WHEN** the failure is logged or reported to error tracking
- **THEN** the token value is absent from the log entry and the report

### Requirement: Automatic token refresh
(Must) While a connection is active, the system SHALL refresh the access token before it expires and SHALL store a rotated refresh token whenever the platform issues one.

#### Scenario: Refresh before expiry
- **GIVEN** an access token that expires within the refresh margin
- **WHEN** the scheduled health check runs
- **THEN** the access token is refreshed and the new expiry time is stored

#### Scenario: Rotated refresh token
- **GIVEN** the platform returns a new refresh token with a refresh response
- **WHEN** the refresh succeeds
- **THEN** the new refresh token replaces the previous one and the previous one is no longer used

### Requirement: Expired and revoked connections are detected and shown
(Must) If a refresh is rejected or the platform reports a revoked grant, then the system SHALL mark the connection as needing reconnection, SHALL show it on the channel and in the content calendar, and SHALL stop using it for new publications.

#### Scenario: Revoked by the user at the platform
- **GIVEN** a connected channel whose grant was removed on the platform
- **WHEN** the next refresh is rejected
- **THEN** the connection is marked as needing reconnection and the channel shows a reconnect action

#### Scenario: Connection state readable for planning
- **GIVEN** a channel that needs reconnection
- **WHEN** another part of the hub reads the channel's connection state
- **THEN** it receives "needs reconnection" together with the reason

### Requirement: Reconnect without losing channel history
(Should) When a publisher reconnects a channel that needs reconnection, the system SHALL keep the channel identity, its posts and its published-post records and SHALL replace only the grant.

#### Scenario: Reconnect keeps history
- **GIVEN** a channel with published posts that needs reconnection
- **WHEN** a publisher completes a new consent for the same platform account
- **THEN** the channel's posts and records are unchanged and the status returns to connected

#### Scenario: Different account
- **GIVEN** a channel that needs reconnection
- **WHEN** the publisher consents with a different platform account
- **THEN** the system refuses the grant and asks the publisher to connect a new channel instead

### Requirement: Disconnect revokes and deletes
(Must) When a channel is disconnected, the system SHALL revoke its grant at the platform where the platform offers revocation and SHALL delete the stored tokens in both cases.

#### Scenario: Disconnect with revocation support
- **GIVEN** a connected channel on a platform that supports revocation
- **WHEN** an administrator disconnects it
- **THEN** the platform revocation is requested and the stored tokens are deleted

#### Scenario: Revocation fails
- **GIVEN** the platform revocation request fails
- **WHEN** the disconnect completes
- **THEN** the stored tokens are still deleted and the record shows that the platform grant may still exist

### Requirement: Quota and rate-limit state visible per connection
(Must) The system SHALL show, for each connection, the quota or rate-limit state the platform reports or Menura measures, and SHALL show when a limit blocked or delayed a publication.

#### Scenario: Quota nearly used
- **GIVEN** a connection whose daily quota use is above the configured warning level
- **WHEN** a publisher opens the channel view
- **THEN** the remaining quota and its reset time are shown with a warning

#### Scenario: Limit blocked a publication
- **GIVEN** a publication delayed by a platform rate limit
- **WHEN** the publisher views the post
- **THEN** the delay and the time of the next attempt are shown

### Requirement: Platform app audit status shown and checked
(Must) The system SHALL show, per channel type, the audit status of the platform app that Menura uses, SHALL show the restrictions that status implies, and SHALL provide status and restrictions to other parts of the hub, such as the schedule dry run.

#### Scenario: Unaudited app
- **GIVEN** a channel type whose platform app is not audited
- **WHEN** a publisher opens the channel or the hub reads the channel's restrictions
- **THEN** the channel view shows "unaudited: uploads private only" and the restrictions read include "public visibility not available"

#### Scenario: Audit status unknown
- **GIVEN** the audit status of a platform app has not been recorded
- **WHEN** a publisher opens the channel view
- **THEN** the status is shown as unknown and no restriction is assumed to be lifted

### Requirement: Common adapter contract for platforms
(Must) Every platform channel type SHALL provide an adapter that exposes the same operations: publish, status, delete, capabilities, listing of the channel's existing published items and reading metrics for those items, and SHALL report the platform features it supports through its capabilities.

#### Scenario: Existing items listed
- **GIVEN** a connected channel whose platform account has published videos
- **WHEN** the adapter's listing operation is called for that channel
- **THEN** each published item is returned with its platform identifier, URL, title, publish time and format, and no item that the platform does not list is returned

#### Scenario: Unsupported feature
- **GIVEN** a platform adapter that does not support scheduled publishing
- **WHEN** a publisher schedules a post on that channel
- **THEN** the scheduling is refused with the reason that the platform does not support it

#### Scenario: Metrics absent
- **GIVEN** an adapter that does not expose a metric for an item
- **WHEN** analytics collection reads the metrics of that item
- **THEN** the metric is recorded as unavailable for that platform and not as zero

### Requirement: Platform adapters only use official APIs
(Must) The system SHALL reach each platform only through the platform's official API and SHALL NOT route publications or metrics through a third-party aggregation service.

#### Scenario: Adapter selection
- **GIVEN** a channel of a given type
- **WHEN** the system publishes to it
- **THEN** the adapter for that platform's official API is used and no other host is contacted
