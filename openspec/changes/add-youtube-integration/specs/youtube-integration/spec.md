## Purpose

Native publishing of finished videos and Shorts to YouTube channels connected through the platform consent, with the platform's own features (playlists, thumbnails and scheduled publication), and quota awareness. Chapter lines from timeline marks are specified in `add-content-library`; native statistics are specified in `add-youtube-insights`.

## ADDED Requirements

### Requirement: Upload long videos and Shorts
(Must) When an approved YouTube post is due, the system SHALL upload its finished asset (recorded by `add-asset-upload`) to the channel as a resumable upload, SHALL send the bytes directly from storage to the platform without passing them through the server, and SHALL record the platform video identifier and URL on the post.

#### Scenario: Long video published
- **GIVEN** an approved post for a long-form asset on a connected YouTube channel
- **WHEN** the post becomes due
- **THEN** the video is uploaded, and the post records the platform video identifier and URL

#### Scenario: Interrupted upload resumes
- **GIVEN** an upload that was interrupted after part of the bytes were sent
- **WHEN** the publication job retries
- **THEN** the upload resumes from the last confirmed byte and does not start again from the beginning

#### Scenario: Short format
- **GIVEN** an approved post for a vertical short asset
- **WHEN** the post is published
- **THEN** the video is published as a Short of the channel

### Requirement: Metadata from the post
(Must) The system SHALL set the title, description and tags of the platform video from the approved post.

#### Scenario: Metadata sent
- **GIVEN** an approved long-form post with a title, a description and tags
- **WHEN** the video is uploaded
- **THEN** the platform receives the title, description and tags of that post

### Requirement: Playlists
(Should) The system SHALL add a published video to the playlists selected on the post, and SHALL only use playlists that belong to the connected channel.

#### Scenario: Playlist added
- **GIVEN** a post with one selected playlist of the channel
- **WHEN** the video is published
- **THEN** the video appears in that playlist

#### Scenario: Foreign playlist refused
- **GIVEN** a post that selects a playlist the channel does not own
- **WHEN** the post is scheduled
- **THEN** scheduling is refused and the playlist is named as unavailable

### Requirement: Custom thumbnail from the asset
(Must) The system SHALL set a custom thumbnail from the thumbnail asset selected on the post when the channel is allowed to set custom thumbnails, and SHALL report the thumbnail as not set when it is not allowed.

#### Scenario: Thumbnail set
- **GIVEN** a post with a thumbnail asset and a channel allowed to set thumbnails
- **WHEN** the video is published
- **THEN** the thumbnail is set and the post records it

#### Scenario: Thumbnail not allowed
- **GIVEN** a channel that is not allowed to set custom thumbnails
- **WHEN** the schedule dry run runs for a post with a thumbnail
- **THEN** the dry run warns that the thumbnail will not be applied and names the reason

### Requirement: Scheduled publication
(Must) When a publisher schedules a YouTube post, the system SHALL upload the video as not yet public with a publish time equal to the scheduled time, and SHALL show the time at which the platform makes it public.

#### Scenario: Scheduled at a future time
- **GIVEN** an approved post scheduled for a future time
- **WHEN** the video is uploaded
- **THEN** the platform is given the scheduled time as the publish time and the post shows the time at which the video will become public

#### Scenario: Time in the past
- **GIVEN** a post whose scheduled time has already passed when it is approved
- **WHEN** the publisher schedules it
- **THEN** the scheduling is refused and the publisher is asked to choose a future time or publish now

### Requirement: Visibility and made-for-kids declaration
(Must) A YouTube post SHALL carry an explicit visibility and an explicit made-for-kids declaration, and the system SHALL refuse to schedule or publish a post that lacks either.

#### Scenario: Missing declaration
- **GIVEN** a post without a made-for-kids declaration
- **WHEN** a publisher tries to approve it for scheduling
- **THEN** approval for scheduling is refused and the missing field is named

#### Scenario: Declaration sent
- **GIVEN** a post with visibility public and a made-for-kids declaration of no
- **WHEN** the video is uploaded
- **THEN** the platform receives both values

### Requirement: Applied visibility read back
(Must) After an upload, the system SHALL read back the visibility the platform applied and SHALL show any difference from the requested visibility as a publication problem.

#### Scenario: Visibility forced to private
- **GIVEN** a post requested as public on a channel whose app is restricted
- **WHEN** the platform reports the video as private after upload
- **THEN** the post is shown as published with a problem stating that the visibility differs from the request

#### Scenario: Visibility as requested
- **GIVEN** a post requested as unlisted
- **WHEN** the platform reports unlisted after upload
- **THEN** the post shows no problem

### Requirement: Daily quota budgeting
(Must) The system SHALL estimate the daily quota a channel's planned uploads and updates need, using the costs the platform reports, and the schedule dry run SHALL warn when the planned amount for a day exceeds the remaining quota.

#### Scenario: Quota exceeded
- **GIVEN** planned posts for one day whose estimated cost exceeds the remaining quota
- **WHEN** the schedule dry run runs
- **THEN** the dry run warns with the estimated and remaining amounts and names the posts that do not fit

#### Scenario: Quota reset
- **GIVEN** a publication delayed because the daily quota was used up
- **WHEN** the platform quota resets
- **THEN** the delayed publication is attempted again at the next job run

### Requirement: Platform quotas, audits and limits visible
(Must) The system SHALL show the audit status of the platform app and the quota state of the channel, and SHALL state that an upload from an app without a completed audit can be restricted as the platform reports.

#### Scenario: Unaudited app
- **GIVEN** a YouTube channel whose app audit status is unaudited
- **WHEN** a publisher views the channel
- **THEN** the view states the restriction on public uploads and links to the audit status shown by the channel connection

### Requirement: Minimal YouTube permissions
(Must) The system SHALL request only the platform permissions needed for uploads, playlists, thumbnails and the read-back of applied settings, and SHALL NOT request permissions for live streams, comments or account management.

#### Scenario: Live stream permission not requested
- **GIVEN** a YouTube channel connection
- **WHEN** the consent screen is shown
- **THEN** no permission for live stream control or comment moderation is requested
