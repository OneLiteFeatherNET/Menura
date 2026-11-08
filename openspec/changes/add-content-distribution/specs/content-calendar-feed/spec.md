# Spec Delta

## Purpose

Gives each person a private, read-only calendar subscription with the planned posts, approvals due and runway warnings they may see, so that the publishing schedule appears in the calendar app they already use. The feed never accepts writes.

## ADDED Requirements

### Requirement: Private subscription feed per person

(Must) The system SHALL give each person a private iCalendar subscription feed (RFC 5545) that lists the planned posts, the approvals due and the runway warnings that the person is allowed to read.

#### Scenario: Feed contains planned posts
- **GIVEN** a person who can read two channels, with planned posts on each
- **WHEN** the person's feed is fetched
- **THEN** the feed contains one calendar event per planned post on both channels

#### Scenario: Feed contains approvals due
- **GIVEN** a publisher and a post in review with a planned time next week
- **WHEN** the publisher's feed is fetched
- **THEN** the feed contains an event labelled approval due at that planned time

#### Scenario: Non-publisher sees no approvals
- **GIVEN** an editor who is not a publisher and the same post in review
- **WHEN** the editor's feed is fetched
- **THEN** the feed contains no approval due event for that post

### Requirement: Secret feed URL

(Must) The system SHALL identify a feed by a token of 256 bits generated from a cryptographically secure source, SHALL store only a hash of it, and SHALL let the person revoke the token so that the feed stops answering with that token.

#### Scenario: Revoked feed stops
- **GIVEN** a person with a working feed URL
- **WHEN** the person revokes that feed
- **THEN** fetching the old URL no longer returns the feed

#### Scenario: Rotated feed
- **GIVEN** a person who has revoked a feed
- **WHEN** the person requests a new feed
- **THEN** the new URL works and the old URL does not

### Requirement: Unknown tokens answer 404

(Must) The system SHALL answer 404 Not Found for an unknown, revoked or malformed feed token, without revealing whether the token ever existed.

#### Scenario: Unknown token
- **GIVEN** a random token that no person holds
- **WHEN** the feed is fetched with it
- **THEN** the response is 404 Not Found in Problem Details format

#### Scenario: Revoked token indistinguishable
- **GIVEN** a token that was revoked
- **WHEN** the feed is fetched with it
- **THEN** the response is identical to the response for an unknown token

### Requirement: Calendar event identity and updates

(Must) The system SHALL give every planned post, approval due and runway warning event a stable identifier, and SHALL update its revision marker when the underlying post changes, so that subscribing clients replace the event instead of duplicating it.

#### Scenario: Rescheduled post updates in place
- **GIVEN** a planned post already shown in a subscribing calendar
- **WHEN** the post is moved to another time
- **THEN** the next fetch returns the same event identifier with a higher revision marker and the new time

#### Scenario: Post removed
- **GIVEN** a planned post that is cancelled
- **WHEN** the next fetch happens
- **THEN** the feed no longer contains that event

### Requirement: Standard calendar clients

(Must) The system SHALL serve the feed as valid RFC 5545 content that calendar clients can subscribe to by URL and poll, without any client-specific extension.

#### Scenario: Subscription in a standard client
- **GIVEN** a feed URL added as an internet calendar subscription in Outlook, Google Calendar, Apple Calendar or Nextcloud
- **WHEN** the client refreshes the subscription
- **THEN** the client shows the planned posts from the feed

#### Scenario: Invalid content rejected by the validator
- **GIVEN** a generated feed
- **WHEN** it is checked against the RFC 5545 validator in the test suite
- **THEN** the check passes

### Requirement: Read-only feed

(Must) The system SHALL accept only read requests on a feed and SHALL NOT change planned posts, approvals or any calendar data through the feed.

#### Scenario: Write attempt refused
- **GIVEN** a feed URL
- **WHEN** a client sends a write request to it
- **THEN** the system refuses the request and changes nothing

### Requirement: Feed tokens kept out of logs

(Must) The system SHALL redact feed tokens from logs, traces and error reports, and SHALL send the feed response with a Referrer-Policy of no-referrer.

#### Scenario: Token redacted in logs
- **GIVEN** a feed fetch with a valid token
- **WHEN** the request is logged
- **THEN** the log line contains no token value

### Requirement: Feed rate limit

(Must) The system SHALL rate-limit feed requests per token and per IP address and SHALL answer excess requests with 429 Too Many Requests and a Retry-After header.

#### Scenario: Polling too often
- **GIVEN** a token that has exceeded its request limit
- **WHEN** another fetch arrives
- **THEN** the response is 429 with a Retry-After header
