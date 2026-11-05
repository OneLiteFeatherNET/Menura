# Spec Delta

## Purpose

Publishes approved finished content to Instagram as Reels through Instagram's own publishing interface, so that each Reel is checked before it is due, waits for the platform until it is ready, respects the account's daily allowance, and is never published twice.

## ADDED Requirements

### Requirement: Time-limited media link for the platform fetch
(Must) When the system prepares a Reel, it SHALL hand the platform a signed, read-only link to exactly the one asset (and its cover), valid only for the platform's processing window and at most one hour, and SHALL NOT make any storage object, bucket listing or other asset publicly reachable; the link SHALL NOT appear in logs, traces, API or MCP output.

#### Scenario: Platform fetches the asset in time
- **GIVEN** an approved Reel whose asset lives in private object storage
- **WHEN** the system prepares the Reel
- **THEN** the platform receives a signed link to that single asset that expires within one hour
- **AND** the asset is published successfully

#### Scenario: Link used after expiry
- **GIVEN** a signed link handed to the platform more than one hour ago
- **WHEN** anyone requests the asset with that link
- **THEN** the storage refuses the request

#### Scenario: Link kept out of logs
- **GIVEN** a prepared Reel
- **WHEN** the access logs and traces of the preparation are inspected
- **THEN** they contain no signed link and no signature

### Requirement: Professional accounts only

(Must) The system SHALL accept only an Instagram professional account (business or creator) as a channel that publishes, and SHALL refuse to enable publishing for any other account type when the account is connected.

#### Scenario: Professional account connected
- **GIVEN** an Instagram account of professional type that a publisher connects
- **WHEN** the connection completes
- **THEN** the channel is enabled for publishing and shows the account type

#### Scenario: Personal account refused
- **GIVEN** an Instagram account that is not professional
- **WHEN** a publisher connects it as a channel
- **THEN** the channel is not enabled for publishing and the system states that a professional account is required

### Requirement: Reels only

(Must) The system SHALL publish Instagram content as Reels built from a finished asset recorded by `add-asset-upload`, and SHALL refuse to schedule any other Instagram content type in this integration.

#### Scenario: Reel scheduled from a finished asset
- **GIVEN** a finished video asset recorded by `add-asset-upload` and a post for an Instagram channel
- **WHEN** a publisher schedules the post
- **THEN** the post is stored as a Reel referencing that asset

#### Scenario: Other content type refused
- **GIVEN** a post for an Instagram channel that uses an image or a carousel
- **WHEN** a publisher schedules it
- **THEN** the system refuses the scheduling and states that only Reels are published

### Requirement: Two-step Reel publication with waiting

(Must) When an Instagram Reel becomes due, the system SHALL prepare the Reel on the platform, SHALL wait until the platform reports it ready within a bounded window, and SHALL then publish it.

#### Scenario: Reel published after the platform reports it ready
- **GIVEN** a due Reel whose preparation completes on the platform within the window
- **WHEN** the publication job waits for readiness
- **THEN** the job publishes the Reel and the post becomes published with the platform identifier and URL

#### Scenario: Preparation fails before publication
- **GIVEN** a due Reel whose preparation reports an error before anything is published
- **WHEN** the failure is read
- **THEN** the system prepares the Reel again within the configured retry limit and records each attempt

#### Scenario: Preparation expires unpublished
- **GIVEN** a prepared Reel that was not published within the platform's expiry period
- **WHEN** the job runs
- **THEN** the system prepares it again, and the calendar records the expiry as an attempt

#### Scenario: Unknown publish outcome
- **GIVEN** a publish call whose outcome is not confirmed before the claim lease expires
- **WHEN** the lease expires
- **THEN** the post is shown as unknown outcome and is not published again automatically

### Requirement: Cover from the asset

(Must) The system SHALL set the cover of each Reel from a cover image of the asset when one is given, and otherwise from a frame of the video chosen by the publisher.

#### Scenario: Cover image given
- **GIVEN** a Reel asset with a cover image
- **WHEN** the Reel is prepared
- **THEN** the cover image is used as the Reel's cover

#### Scenario: No cover image
- **GIVEN** a Reel asset without a cover image and a frame offset chosen by the publisher
- **WHEN** the Reel is prepared
- **THEN** the chosen frame is used as the cover

### Requirement: Caption and media limits checked

(Must) The system SHALL check each Instagram post in the schedule dry run against the caption length, the hashtag and mention limits, the video format, the file size and the duration limits, and SHALL show every violation on the post in the calendar.

#### Scenario: Caption over the limit
- **GIVEN** an Instagram post whose caption exceeds the caption length limit or uses more hashtags than allowed
- **WHEN** the dry run is performed
- **THEN** the calendar shows a caption problem on the post naming the limit

#### Scenario: Video outside the duration limit
- **GIVEN** an Instagram post whose video is shorter than the minimum or longer than the maximum duration
- **WHEN** the dry run is performed
- **THEN** the calendar shows a duration problem on the post

#### Scenario: Unsupported format
- **GIVEN** an Instagram post whose video is not in an allowed container format
- **WHEN** the dry run is performed
- **THEN** the calendar shows a format problem on the post

### Requirement: Daily publishing allowance respected

(Must) The system SHALL read the publishing allowance of each Instagram account for the rolling 24 hours, SHALL NOT schedule or publish more Reels than the allowance allows, and SHALL show the allowance and any post that would exceed it in the calendar.

#### Scenario: Schedule within the allowance
- **GIVEN** an account with remaining allowance for the next 24 hours and a scheduled Reel within it
- **WHEN** the post is scheduled
- **THEN** the post is accepted and the remaining allowance decreases by one

#### Scenario: Schedule beyond the allowance
- **GIVEN** an account whose remaining allowance is zero for the day of a planned Reel
- **WHEN** the dry run is performed
- **THEN** the calendar shows an allowance problem on that Reel and the Reel is not published that day

#### Scenario: Allowance read from the platform
- **GIVEN** an account whose platform-reported allowance differs from the value the server last stored
- **WHEN** the allowance is next read
- **THEN** the platform's value is used

### Requirement: Deletion not offered through the integration

(Must) The system SHALL report deletion as unavailable for Instagram channels that connect through the integration's standard connection, and SHALL tell a publisher to remove a Reel in Instagram.

#### Scenario: Publisher asks to delete a Reel
- **GIVEN** a published Reel that a publisher wants to remove
- **WHEN** the publisher asks Menura to delete it
- **THEN** Menura states that the Reel must be removed in Instagram and changes nothing on Instagram

### Requirement: Adapter capabilities reported

(Must) The system SHALL report the Instagram adapter's capabilities to the channel and the calendar, including the account type, the allowance and the availability of deletion.

#### Scenario: Channel shows its capabilities
- **GIVEN** a connected professional Instagram channel
- **WHEN** a publisher opens the channel
- **THEN** the channel shows the account type, the remaining allowance and that deletion is not available
