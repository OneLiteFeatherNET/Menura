## Purpose

Native integration of a connected Twitch channel with Menura: clips are created during live broadcasts, title, category and schedule follow the plan, and the Twitch channel is never a target for video upload. Imports of Twitch broadcasts, highlights and clips and their view counts are specified in `add-twitch-insights`; the lineage of imported broadcasts to recordings is specified in `add-content-library`.

## ADDED Requirements

### Requirement: Create clips during a live broadcast
(Should) While the connected channel is live, a publisher SHALL be able to create a clip, and the system SHALL store the clip's identifier and link on the planned stream once the platform confirms it.

#### Scenario: Clip created while live
- **GIVEN** the connected channel is live and clips are enabled on it
- **WHEN** a publisher requests a clip
- **THEN** the clip is created and its identifier and link are stored on the planned stream

#### Scenario: Channel offline
- **GIVEN** the connected channel is not live
- **WHEN** a publisher requests a clip
- **THEN** the request is refused with the reason that clips can only be created during a live broadcast

#### Scenario: Clip not confirmed
- **GIVEN** a clip request for which the platform does not confirm the clip within the allowed wait
- **WHEN** the system checks the outcome
- **THEN** the request is shown as failed and no identifier is stored

### Requirement: Title and category follow the planned stream
(Must) When a planned stream starts, or when a publisher requests it, the system SHALL set the connected channel's stream title and category from the planned stream, and SHALL show the values the platform reports after the change.

#### Scenario: Title and category set at start
- **GIVEN** a planned stream with a title and category and a connected channel
- **WHEN** the planned stream starts
- **THEN** the channel's title and category are set to the planned values and the change is shown

#### Scenario: Change rejected
- **GIVEN** the platform rejects the title or category change
- **WHEN** the change is attempted
- **THEN** the old values are kept and the publisher sees the rejection reason

### Requirement: Stream schedule synced from the plan
(Must) The system SHALL mirror Menura's planned streams into the connected channel's stream schedule, SHALL mirror changes and cancellations, and SHALL report any planned stream the platform does not accept.

#### Scenario: Planned stream published to schedule
- **GIVEN** a planned stream in the future for a connected channel that may create schedule segments
- **WHEN** the schedule sync runs
- **THEN** a schedule segment with the planned start, duration and title exists on the channel's schedule

#### Scenario: Planned stream moved
- **GIVEN** a schedule segment mirrored from a planned stream
- **WHEN** the planned stream's time changes
- **THEN** the segment is updated to the new time

#### Scenario: Channel may not create segments
- **GIVEN** a connected channel whose platform type does not allow the creation of non-recurring segments
- **WHEN** the schedule sync runs
- **THEN** the planned stream is reported as not synced with the reason, and no segment is created

### Requirement: No video upload to Twitch
(Must) The system SHALL NOT upload, publish or replace video files on Twitch, and SHALL NOT offer any action that would do so.

#### Scenario: Finished asset cannot be scheduled for Twitch
- **GIVEN** a finished asset recorded by `add-asset-upload`
- **WHEN** a publisher looks for a Twitch channel as a publication target
- **THEN** no Twitch channel is offered as a target for the asset, and the reason is shown

### Requirement: Minimal Twitch permissions
(Must) The system SHALL request only the Twitch permissions needed for the features the channel has enabled, and SHALL NOT request permission to manage chat, moderation, subscriptions or payments.

#### Scenario: Feature disabled
- **GIVEN** a connected Twitch channel with the schedule feature disabled
- **WHEN** the consent is reviewed
- **THEN** no schedule permission is requested for that channel
