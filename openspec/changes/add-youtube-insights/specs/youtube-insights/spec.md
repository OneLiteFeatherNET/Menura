# youtube-insights

## Purpose

Read-only YouTube integration: import the existing videos and Shorts of a connected channel as external posts through the official YouTube APIs, and collect their native statistics. This capability never uploads, changes or deletes anything on the platform.

## ADDED Requirements

### Requirement: Existing videos and Shorts imported
(Must) The system SHALL import the existing public and unlisted videos and Shorts of a connected YouTube channel as external posts through the official YouTube Data API read operations, recording each one's format as Short or video, and SHALL NOT send any write request to YouTube for this feature.

#### Scenario: Video and Short imported
- **GIVEN** a connected YouTube channel with a long video and a Short, both public
- **WHEN** the import runs
- **THEN** two external posts exist, one with format video and one with format Short, each with platform YouTube, video identifier, URL, title and publish time

#### Scenario: Private video not imported
- **GIVEN** a private video on the connected channel
- **WHEN** the import runs
- **THEN** no external post is created for that video

#### Scenario: Read only
- **GIVEN** a connected YouTube channel
- **WHEN** the import and the statistics collection run
- **THEN** no request that creates, changes or deletes a video, playlist or comment is sent to YouTube

### Requirement: Native statistics of external posts
(Must) The system SHALL collect the statistics that YouTube provides for each external YouTube post and SHALL record them in content analytics, marking each metric YouTube does not provide for the channel as unavailable.

#### Scenario: Statistics collected
- **GIVEN** an external YouTube post
- **WHEN** the analytics collection runs
- **THEN** views, watch time, likes and comments are recorded with the collection time

#### Scenario: Metric unavailable
- **GIVEN** a metric that YouTube does not provide for this channel
- **WHEN** the analytics collection runs
- **THEN** the metric is recorded as unavailable and not as zero

### Requirement: Minimal read-only YouTube permissions
(Must) The system SHALL request for this feature only the read-only YouTube permission needed to list the channel's videos and read their statistics, and SHALL NOT request any permission that allows upload, edit, delete, live stream control or comment moderation.

#### Scenario: Statistics-only consent
- **GIVEN** a YouTube channel connected for statistics only
- **WHEN** the consent screen is shown
- **THEN** only the read-only permission is requested

#### Scenario: Upload permission not requested
- **GIVEN** a YouTube channel whose publishing feature is disabled
- **WHEN** the consent is reviewed
- **THEN** no upload permission is requested for that channel
