# instagram-insights

## Purpose

Read-only Instagram integration: import the existing Reels of a connected professional account as external posts and collect their insights. Figures Instagram has not yet reported are collected again later, and this capability never publishes, uploads or changes anything on Instagram.

## ADDED Requirements

### Requirement: Existing Reels imported
(Must) The system SHALL import the existing Reels of a connected Instagram professional account as external posts through the official Instagram Graph API read operations, with platform, external identifier, URL, caption as title, publish time and format Reel, and SHALL NOT send any write request to Instagram for this feature.

#### Scenario: Reel imported
- **GIVEN** a connected Instagram account with a published Reel
- **WHEN** the import runs
- **THEN** an external post exists with platform Instagram, the media identifier, URL, caption as title, publish time and format Reel

#### Scenario: Other media not imported
- **GIVEN** a connected Instagram account with a Reel and a feed photo
- **WHEN** the import runs
- **THEN** only the Reel becomes an external post

### Requirement: Reel insights into content analytics
(Should) The system SHALL collect the insights of external Instagram Reels from the platform (views, reach, likes, comments, shares, saves and total interactions where reported) and SHALL pass them to content analytics; figures that the platform has not yet reported SHALL be recorded as unavailable, not as zero.

#### Scenario: Insights collected
- **GIVEN** an external Reel with reported insights
- **WHEN** the analytics collection runs
- **THEN** the reported insights are stored as metric points for that Reel

#### Scenario: Figures delayed by the platform
- **GIVEN** a Reel published less than two days ago
- **WHEN** the analytics collection runs
- **THEN** figures not yet reported are recorded as unavailable and collected again on a later run

### Requirement: Minimal read-only Instagram permissions
(Must) The system SHALL request for this feature only the basic account and insights permissions, `instagram_business_basic` and `instagram_business_manage_insights`, and SHALL NOT request the publishing permission or any permission that changes media.

#### Scenario: Import and insights consent
- **GIVEN** an Instagram account connected for import and insights
- **WHEN** the consent is reviewed
- **THEN** the basic account and insights permissions are requested and no publishing permission is requested
