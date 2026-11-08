## Purpose

External posts are the hub's record of posts and videos that exist on a connected platform channel, whether imported from the channel's history or published by Menura later. Each record holds the platform, the external identifier, the URL, the title, the publish time and the format. Records are kept in sync with the platform, are never deleted when the platform removes an item, and give content analytics one shape for every post.

## ADDED Requirements

### Requirement: Import existing posts and videos of every connected channel
(Must) The system SHALL import the existing published posts and videos of every connected channel as external post records, each with its platform, external identifier, URL, title, publish time and format, obtained through the listing operation of the platform adapter.

#### Scenario: Existing items imported
- **GIVEN** a connected channel with published videos and a past stream archive
- **WHEN** the first import runs for that channel
- **THEN** each published item is one external post record with its platform, external identifier, URL, title, publish time and format

#### Scenario: Every connected channel imported
- **GIVEN** two connected channels on different platforms
- **WHEN** the import runs
- **THEN** the published items of both channels are recorded, and each record names the channel it belongs to

#### Scenario: Channel needing reconnection skipped
- **GIVEN** a channel whose connection needs reconnection
- **WHEN** the import runs
- **THEN** no record is changed for that channel and the channel shows that its import is waiting for reconnection

#### Scenario: Media not copied
- **GIVEN** an imported external post
- **WHEN** the record is stored
- **THEN** the record holds metadata and the platform URL only, and no media file of the item is copied into Menura's storage

### Requirement: Periodic re-sync adds new posts and updates changed ones
(Must) While a channel is connected, the system SHALL re-sync the published items of that channel on a schedule, SHALL add the items that are new since the previous run, and SHALL update the stored fields of items whose platform data changed.

#### Scenario: New post added
- **GIVEN** a channel imported earlier and a video published since the previous run
- **WHEN** the re-sync runs
- **THEN** one external post record is added for the video and the existing records keep their identifiers

#### Scenario: Changed title updated
- **GIVEN** an imported post whose title was changed on the platform
- **WHEN** the re-sync runs
- **THEN** the record shows the new title and no second record is created

#### Scenario: Unchanged channel
- **GIVEN** a channel whose published items did not change
- **WHEN** the re-sync runs twice
- **THEN** no record is added or duplicated

#### Scenario: Platform unreachable
- **GIVEN** a channel whose platform cannot be reached during the re-sync
- **WHEN** the re-sync fails
- **THEN** the existing records are unchanged and the failure is recorded for that channel

### Requirement: Removed posts marked, never deleted
(Must) When a previously imported post is no longer listed by the platform in a complete listing, the system SHALL mark the external post as removed on the platform with the time the removal was seen, SHALL keep its record and its metric history, and SHALL NOT delete the record.

#### Scenario: Post deleted on the platform
- **GIVEN** an imported post that was listed by the previous run
- **WHEN** a complete listing no longer contains it
- **THEN** the record is marked as removed on the platform, its link is no longer offered as live, and its metric points remain stored

#### Scenario: Removed post listed again
- **GIVEN** an external post marked as removed
- **WHEN** a later complete listing contains it again
- **THEN** the removed mark is cleared and the record is live again

#### Scenario: Incomplete listing marks nothing
- **GIVEN** a listing that fails part-way through the channel's items
- **WHEN** the re-sync processes that listing
- **THEN** no post is marked as removed on the basis of that listing

### Requirement: Channel overview lists imported posts
(Must) The web app SHALL show, for each channel, its external posts with title, format, publish time, link and status (live or removed), together with the time of the last successful re-sync of that channel.

#### Scenario: Overview lists posts
- **GIVEN** a channel with imported and published external posts
- **WHEN** a person with the viewer role opens the channel
- **THEN** the posts are listed with title, format, publish time, link and status, and no edit control is shown

#### Scenario: Removed post shown
- **GIVEN** an external post marked as removed on the platform
- **WHEN** the channel overview is shown
- **THEN** the post is listed as removed and its link is not offered

#### Scenario: Channel outside the caller's roles
- **GIVEN** a channel the caller may not see
- **WHEN** the caller requests its overview over the API
- **THEN** the hub answers 404 as a Problem Details error

### Requirement: Posts published by Menura recorded alike
(Should) Where content distribution is available, the system SHALL record each post that Menura publishes to a channel as an external post of the same kind, with the same fields, so that content analytics treats imported and published posts alike.

#### Scenario: Published post recorded
- **GIVEN** Menura publishes a post and the platform returns its identifier and URL
- **WHEN** the publication completes
- **THEN** an external post record exists with its platform, external identifier, URL, title, publish time and format

#### Scenario: Imported and published posts analysed alike
- **GIVEN** one imported external post and one published by Menura, on the same channel
- **WHEN** content analytics collects their metrics
- **THEN** both are collected through the same path and stored in the same form
