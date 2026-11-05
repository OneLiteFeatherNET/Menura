# Spec Delta

## Purpose

Publishes approved finished content to TikTok through TikTok's own posting API, respecting the privacy and interaction options each creator offers, the restriction on unaudited apps, and the outcomes the platform reports, so that a post appears on TikTok exactly as a person approved it and is never published twice.

## ADDED Requirements

### Requirement: Creator check before each post

(Must) When a TikTok post becomes due, the system SHALL read the connected creator's current posting options immediately before publishing, and SHALL NOT publish with any privacy or interaction setting that those options do not offer.

#### Scenario: Options read before publishing
- **GIVEN** an approved TikTok post whose time has arrived
- **WHEN** the publication job starts the publication
- **THEN** the creator's posting options are read first and used for that publication

#### Scenario: Requested privacy not offered
- **GIVEN** a TikTok post set to public visibility and a creator whose current options do not include public visibility
- **WHEN** the schedule dry run or the publication job checks the post
- **THEN** the post is reported with a visibility problem naming the channel, and nothing is published

### Requirement: Privacy and interaction settings

(Must) The system SHALL let the publisher choose, per post, the privacy level and the comment, duet and stitch settings that the creator's options offer, and SHALL send them unchanged to the platform.

#### Scenario: Interactions disabled by the creator
- **GIVEN** a creator who has disabled comments on their account
- **WHEN** a publisher schedules a TikTok post with comments enabled
- **THEN** the dry run shows a problem on the post and the comment setting cannot be enabled

#### Scenario: Setting sent as chosen
- **GIVEN** a post with a privacy level and interaction settings that the creator's options offer
- **WHEN** the post is published
- **THEN** the platform receives exactly the privacy level and settings the publisher chose

### Requirement: Unaudited app restriction

(Must) While the TikTok app of the platform connection is not audited by TikTok, the system SHALL restrict every TikTok post to private visibility, SHALL show the restriction on the channel, and SHALL refuse to schedule a TikTok post with any other visibility.

#### Scenario: Public post refused while unaudited
- **GIVEN** a TikTok channel whose app is not audited and a post set to public visibility
- **WHEN** a publisher schedules it
- **THEN** the system refuses the scheduling and states that the app is unaudited and that only private posts are allowed

#### Scenario: Restriction shown on the channel
- **GIVEN** an unaudited TikTok app
- **WHEN** a publisher opens the TikTok channel
- **THEN** the channel view states that posts are private until the app is audited

#### Scenario: Restriction lifted after audit
- **GIVEN** a TikTok app that TikTok has audited and that the channel is marked audited
- **WHEN** a publisher schedules a post with public visibility offered by the creator
- **THEN** the system accepts the scheduling

### Requirement: Direct post and inbox draft modes

(Must) The system SHALL publish a TikTok post either as a direct post or as a draft sent to the creator's TikTok inbox, as set on the channel, and SHALL show a post sent to the inbox as waiting for the creator until the platform reports it complete.

#### Scenario: Direct post published
- **GIVEN** a TikTok channel in direct post mode and a due post
- **WHEN** the publication job publishes it
- **THEN** the platform publishes the post and the post becomes published when the platform reports it complete

#### Scenario: Inbox draft waits for the creator
- **GIVEN** a TikTok channel in inbox mode and a due post
- **WHEN** the publication job sends it to the creator's inbox
- **THEN** the post is shown as waiting for the creator and stays scheduled until the platform reports it complete

#### Scenario: Inbox draft not completed
- **GIVEN** a post waiting in the creator's inbox
- **WHEN** the platform reports the post as neither complete nor failed after the window configured for inbox drafts
- **THEN** the post is shown as unfinished for a publisher to decide, and it is not published again automatically

### Requirement: Publication outcomes and retries

(Must) The system SHALL read the outcome of each TikTok publication from the platform and SHALL retry a failure only for reasons that are safe to retry; every other failure SHALL be shown with its reason and not retried.

#### Scenario: Temporary platform error retried
- **GIVEN** a TikTok publication that fails with a temporary platform error
- **WHEN** the retry delay has passed
- **THEN** the system publishes again and records both attempts

#### Scenario: Account restriction not retried
- **GIVEN** a TikTok publication that fails because the creator is banned from posting
- **WHEN** the failure is read
- **THEN** the post is marked failed with the reason and is not retried

#### Scenario: Daily posting cap reached
- **GIVEN** a TikTok publication that fails because the creator reached the daily cap of API posts
- **WHEN** the failure is read
- **THEN** the post is marked failed with the reason, the calendar shows the cap, and the post is not retried automatically

#### Scenario: Ambiguous outcome
- **GIVEN** a publication whose outcome the platform does not report before the job stops
- **WHEN** the claim lease expires
- **THEN** the post is shown as unknown outcome and is not published again automatically

### Requirement: Platform limits checked before publication

(Must) The system SHALL check each TikTok post in the schedule dry run against the creator's maximum video length and the platform's title length limit, and SHALL show every violation in the calendar.

#### Scenario: Video longer than the creator's maximum
- **GIVEN** a TikTok post whose video is longer than the creator's maximum video length
- **WHEN** the dry run is performed
- **THEN** the calendar shows a length problem on the post naming the maximum

#### Scenario: Title too long
- **GIVEN** a TikTok post whose title exceeds the platform's title length limit
- **WHEN** the dry run is performed
- **THEN** the calendar shows a title problem on the post

### Requirement: Rate limits respected

(Must) The system SHALL keep the requests of each TikTok connection within the platform's per-connection request limits and SHALL back off when the platform reports a rate limit.

#### Scenario: Rate limit reported
- **GIVEN** a TikTok connection whose request is answered with a rate limit
- **WHEN** the adapter receives that answer
- **THEN** it waits before the next request and the job run records the delay

### Requirement: Adapter capabilities reported

(Must) The system SHALL report the TikTok adapter's capabilities to the channel and the calendar, including that deleting a post is not available through the integration.

#### Scenario: Deletion not offered
- **GIVEN** a published TikTok post that a publisher wants to remove
- **WHEN** the publisher asks Menura to delete it
- **THEN** Menura states that the post must be removed in the TikTok app and changes nothing on TikTok

### Requirement: Commercial content disclosure

(Should) Where a TikTok post is flagged as commercial content, the system SHALL send the disclosure settings the platform offers for it and SHALL show the disclosure in the dry run.

#### Scenario: Branded post disclosed
- **GIVEN** a TikTok post flagged as branded content
- **WHEN** the post is published
- **THEN** the disclosure setting the platform offers for branded content is sent with it
