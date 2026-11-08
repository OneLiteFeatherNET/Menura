# tiktok-insights

## Purpose

Read-only TikTok integration: import the existing public videos of a connected account as external posts and collect their native metrics. Private videos are outside what the official read operation returns, and this capability never publishes, uploads or changes anything on TikTok.

## ADDED Requirements

### Requirement: Existing videos imported
(Must) The system SHALL import the existing public videos of a connected TikTok account as external posts through the official TikTok video list read operation, with platform, external identifier, URL, title, publish time and format, and SHALL NOT send any write request to TikTok for this feature.

#### Scenario: Public video imported
- **GIVEN** a connected TikTok account with a public video
- **WHEN** the import runs
- **THEN** an external post exists with platform TikTok, the video identifier, URL, title, publish time and format

#### Scenario: Private post not imported
- **GIVEN** a private post on the connected account, which the video list does not return
- **WHEN** the import runs
- **THEN** no external post is created for it and it is not marked as removed

#### Scenario: Coverage stated
- **GIVEN** a connected TikTok account
- **WHEN** its channel view is shown
- **THEN** the view states that the import covers public videos only

### Requirement: Native metrics of TikTok posts
(Should) The system SHALL collect views, likes, comments and shares of external TikTok posts from the platform's native video data and SHALL pass them to content analytics; metrics the platform does not report for a post SHALL be recorded as unavailable, not as zero.

#### Scenario: Public post metrics collected
- **GIVEN** an imported public TikTok post
- **WHEN** the analytics collection runs
- **THEN** the views, likes, comments and shares are stored as metric points for that post

#### Scenario: Metric not reported
- **GIVEN** an external TikTok post for which the platform reports no watch time
- **WHEN** the analytics collection runs
- **THEN** the watch time is recorded as unavailable and no value is stored as zero

### Requirement: Minimal read-only TikTok permissions
(Must) The system SHALL request for this feature only the video list and basic account information scopes, and SHALL NOT request any scope that allows publishing, uploading or drafting videos.

#### Scenario: Import-only consent
- **GIVEN** a TikTok account connected for import only
- **WHEN** the consent is reviewed
- **THEN** only the video list and basic account information scopes are requested
