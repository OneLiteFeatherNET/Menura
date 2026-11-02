# twitch-insights

## Purpose

Read-only Twitch integration: import the existing VODs, highlights and clips of a connected channel as external posts, record their view counts, and mark items Twitch removes. Menura references Twitch broadcasts and never copies their media.

## ADDED Requirements

### Requirement: Existing VODs, highlights and clips imported
(Must) The system SHALL import the existing VODs, highlights and clips of a connected Twitch channel as external posts through the official Twitch read operations, with platform, external identifier, URL, title, duration, publish time and format (VOD, highlight or clip), and SHALL NOT send any write request to Twitch for this feature.

#### Scenario: Items imported
- **GIVEN** a connected Twitch channel with an archived broadcast, a highlight and a clip
- **WHEN** the import runs
- **THEN** three external posts exist with formats VOD, highlight and clip, each with its platform, identifier, URL, title, duration and publish time

#### Scenario: Read only
- **GIVEN** a connected Twitch channel
- **WHEN** the import and the view count collection run
- **THEN** no request that creates, changes or deletes a video or clip is sent to Twitch

### Requirement: VODs and highlights as external posts
(Must) The system SHALL import the Twitch VODs and highlights of a connected channel as external posts that reference the broadcast with its metadata and playback link, and SHALL NOT copy their media into Menura's storage.

#### Scenario: VOD imported
- **GIVEN** a connected Twitch channel with a new archived broadcast
- **WHEN** the import runs
- **THEN** an external post of format VOD exists with the broadcast title, duration, start time and link, and no media is stored by Menura

#### Scenario: Highlight imported
- **GIVEN** a highlight on the connected channel
- **WHEN** the import runs
- **THEN** an external post of format highlight exists with its link and duration

#### Scenario: Import is repeatable
- **GIVEN** a broadcast already imported
- **WHEN** the import runs again
- **THEN** no second external post is created for the same broadcast

### Requirement: Clips linked to their broadcast
(Must) The system SHALL import the clips of a connected channel as external posts with their link and metadata, and SHALL link a clip to its broadcast external post when that broadcast is known.

#### Scenario: Clip linked to broadcast
- **GIVEN** a clip made during an imported broadcast
- **WHEN** the import runs
- **THEN** the clip's external post is linked to the broadcast's external post

#### Scenario: Clip without known broadcast
- **GIVEN** a clip whose broadcast is not among the external posts
- **WHEN** the import runs
- **THEN** the clip is imported as an external post without a broadcast link

### Requirement: View counts into content analytics
(Should) The system SHALL record the view counts of imported broadcasts, highlights and clips in content analytics, marked as provided by Twitch and collected at a known time.

#### Scenario: View count recorded
- **GIVEN** an imported clip with a reported view count
- **WHEN** the analytics job runs
- **THEN** the count is recorded with its collection time and the source Twitch

#### Scenario: View count not provided
- **GIVEN** an external post for which Twitch reports no view count
- **WHEN** the analytics job runs
- **THEN** the count is recorded as unavailable and not as zero

### Requirement: Removed Twitch items marked
(Must) When Twitch reports an external post of this channel as deleted or unavailable, the system SHALL mark it as removed on the platform and SHALL stop offering its link, as specified for external posts.

#### Scenario: Broadcast removed on platform
- **GIVEN** an imported VOD that Twitch reports as deleted
- **WHEN** the import or status check runs
- **THEN** the external post shows the broadcast as removed, its link is no longer offered, and its view counts are kept

### Requirement: Minimal read-only Twitch permissions
(Must) The system SHALL request for this feature only the read scopes that the Twitch read operations require, and SHALL NOT request any scope that allows channel updates, clip creation, schedule changes, chat, moderation, subscriptions or payments.

#### Scenario: Import-only consent
- **GIVEN** a Twitch channel connected for import only
- **WHEN** the consent is reviewed
- **THEN** no scope beyond the read scopes is requested
