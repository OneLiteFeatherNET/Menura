# Spec Delta

## Purpose

Schedules approved finished content to external platforms through one hub, with a human publisher approving every post before it leaves the organisation. Agents may draft, check and propose, but never publish, so that what reaches each channel is decided by a person.

## ADDED Requirements

### Requirement: Channels from platform integrations

(Must) The system SHALL offer channels only from platform integrations: YouTube, TikTok and Instagram for publishing, and Twitch for schedule, each connected through the platform's own consent as specified in `add-channel-connections`.

#### Scenario: Channel connected
- **GIVEN** an admin or publisher who completes the platform's consent for a YouTube account
- **WHEN** the connection completes
- **THEN** the channel is listed with its platform and account name

#### Scenario: Unsupported platform
- **GIVEN** a request to add a channel for a platform outside the first-stage list
- **WHEN** the request is submitted
- **THEN** the system rejects it with a validation error

### Requirement: Credentials never returned

(Must) The system SHALL never return channel credentials or tokens in any API response, MCP tool result, MCP resource, log line or error message.

#### Scenario: Channel read omits credentials
- **GIVEN** a configured channel with stored credentials
- **WHEN** any client reads the channel
- **THEN** the response contains the platform and account name and no credential or token value

#### Scenario: Error message omits credentials
- **GIVEN** a channel whose credentials were rejected by its platform
- **WHEN** a client reads the failure of a publication on that channel
- **THEN** the failure names the channel and the reason and contains no credential value

### Requirement: Centralised distribution

(Must) The system SHALL allow only finished assets recorded by `add-asset-upload` to be scheduled, and SHALL route every external publication through the hub.

#### Scenario: Finished asset scheduled
- **GIVEN** a finished asset recorded by `add-asset-upload`
- **WHEN** a post is created with that asset
- **THEN** the post is stored referencing the asset

#### Scenario: Local file rejected
- **GIVEN** a video file that exists only on a streamer's PC and was not uploaded as a finished asset
- **WHEN** a client tries to schedule it
- **THEN** the system rejects the post and states that only finished assets can be scheduled

### Requirement: Post content and status lifecycle

(Must) The system SHALL represent a post as one or more finished assets with per-channel text, hashtags, a cover and a scheduled time, and SHALL move each post through the statuses draft, in review, approved, scheduled, published or failed.

#### Scenario: Post moves through its lifecycle
- **GIVEN** a draft post with a scheduled time
- **WHEN** it is submitted for review, approved by a publisher, and its time arrives
- **THEN** its status changes in that order from draft to in review, approved, scheduled and published

#### Scenario: Status skip refused
- **GIVEN** a draft post
- **WHEN** a client attempts to set its status to published directly
- **THEN** the system rejects the change and the status stays draft

### Requirement: Drafts from people and agents

(Must) The system SHALL let a person with the editor role create a draft post and edit a draft that has not been approved, and SHALL record the author of each draft.

#### Scenario: Draft created by an editor
- **GIVEN** an editor and a finished asset recorded by `add-asset-upload`
- **WHEN** the editor creates a draft post for that asset with per-channel text
- **THEN** the draft is stored with status draft and the editor recorded as its author

#### Scenario: Draft created through MCP
- **GIVEN** the hub MCP server is available and an MCP client with the editor role and a finished asset
- **WHEN** the client creates a draft post for that asset with per-channel text
- **THEN** the draft is stored with status draft and the client recorded as its author

### Requirement: Human approval

(Must) The system SHALL allow only a person with the publisher role to approve a post, and only through the web application; no MCP tool SHALL approve or publish a post.

#### Scenario: Publisher approves in the web app
- **GIVEN** a post in review and a signed-in publisher
- **WHEN** the publisher approves it in the web application
- **THEN** the post status becomes approved and the approval records the publisher

#### Scenario: MCP client cannot approve
- **GIVEN** the hub MCP server is available and an MCP client holding a token for a post in review
- **WHEN** the client attempts to approve or publish the post
- **THEN** the system refuses the request, the post status is unchanged, and the refusal is audit-logged via MCP

#### Scenario: Editor cannot approve
- **GIVEN** a signed-in editor who is not a publisher
- **WHEN** the editor attempts to approve a post in review
- **THEN** the system refuses the request

### Requirement: Schedule dry run

(Must) The system SHALL validate each scheduled post before its time against the channel's format limits, the text and hashtag limits, the state of the channel's connection and platform audit status as reported by `add-channel-connections`, and collisions with other posts in the calendar, and SHALL show every problem in the calendar.

#### Scenario: Clean dry run
- **GIVEN** a scheduled post whose asset, text, hashtags and channel connection all meet the channel's rules and no other post collides with it
- **WHEN** the dry run is performed
- **THEN** the post shows no problems in the calendar

#### Scenario: Text too long
- **GIVEN** a scheduled post whose text exceeds the channel's length limit
- **WHEN** the dry run is performed
- **THEN** the calendar shows a problem naming the channel and the length limit

#### Scenario: Expired connection
- **GIVEN** a scheduled post on a channel whose connection has expired or was revoked
- **WHEN** the dry run is performed
- **THEN** the calendar shows a connection problem for that channel with a reconnect action

#### Scenario: Collision with another post
- **GIVEN** two posts scheduled on the same channel at the same time
- **WHEN** the dry run is performed
- **THEN** the calendar shows a collision problem on both posts

### Requirement: Content calendar

(Must) The system SHALL show planned and published posts per channel in a content calendar, and, where the hub MCP server is available, SHALL make the same data readable by MCP clients.

#### Scenario: Calendar per channel
- **GIVEN** two channels with planned and published posts
- **WHEN** a publisher opens the calendar filtered to one channel
- **THEN** only the planned and published posts of that channel are shown

### Requirement: Recycling with lineage

(Must) The system SHALL let a publisher re-schedule an existing finished asset to other channels or re-post it with new text, and SHALL store the link from each new post to the original post.

#### Scenario: Asset re-scheduled to another channel
- **GIVEN** a published post on one channel
- **WHEN** a publisher creates a new post with the same asset for another channel
- **THEN** the new post references the original post as its source

#### Scenario: Asset re-posted with new text
- **GIVEN** a published post whose text is outdated
- **WHEN** a publisher creates a new post for the same channel with new text
- **THEN** the new post references the original post and the original post is unchanged

### Requirement: Publication results

(Must) The system SHALL store the external identifier and URL of every published post, and SHALL record each published post as an external post of `add-external-post-import`.

#### Scenario: Published post stores its external reference
- **GIVEN** a scheduled post that the platform accepts
- **WHEN** the publication succeeds
- **THEN** the post status is published, it stores the platform identifier and the public URL, and an external post record with the same identifier and URL exists

### Requirement: Retries and visible failures

(Must) The system SHALL retry a failed publication with increasing delays up to a configured limit, and SHALL show each failure with its attempt count and reason until the post is published or marked failed.

#### Scenario: Transient failure retried
- **GIVEN** a scheduled post whose first publication attempt fails with a temporary platform error
- **WHEN** the retry delay has passed
- **THEN** the system publishes again and records both attempts

#### Scenario: Retry limit reached
- **GIVEN** a post whose publication has failed on every attempt up to the configured limit
- **WHEN** the last attempt fails
- **THEN** the post status becomes failed and the failure is visible in the calendar with its reason

### Requirement: Optimisation proposals as revisions

(Must) The system SHALL accept a proposed revision of a draft or scheduled post from a person with the editor role or, where the hub MCP server is available, from an MCP client, consisting of any of title, text, hashtags, cover, time slot and channel choice with a rationale, and SHALL store it as a revision that a publisher accepts or rejects.

#### Scenario: Revision proposed by an editor
- **GIVEN** a scheduled post and an editor
- **WHEN** the editor proposes a new time slot with a rationale
- **THEN** a pending revision is stored with the rationale and the post itself is unchanged

#### Scenario: Revision proposed by an agent
- **GIVEN** the hub MCP server is available, a scheduled post and an MCP client with the editor role
- **WHEN** the client proposes a new time slot with a rationale
- **THEN** a pending revision is stored with the rationale and the post itself is unchanged

#### Scenario: Publisher rejects a revision
- **GIVEN** a pending revision
- **WHEN** a publisher rejects it
- **THEN** the revision is marked rejected and the post is unchanged

#### Scenario: Accepted revision of an approved post re-enters approval
- **GIVEN** an approved post and a pending revision that changes its text
- **WHEN** a publisher accepts the revision
- **THEN** the post content is updated and its status returns to in review

### Requirement: Latest performance numbers on the post

(Should) The system SHALL show with each published post in the calendar its latest views and likes as collected by `add-content-analytics`, with the time of collection, and SHALL NOT collect them a second time.

#### Scenario: Numbers shown from analytics
- **GIVEN** a published post for which content analytics collected views and likes
- **WHEN** a person opens the post in the calendar
- **THEN** the latest views and likes from content analytics are shown with the time of collection

### Requirement: Best-time suggestions

(Could) The system SHALL suggest a posting time per channel based on that channel's performance numbers collected by `add-content-analytics`, and SHALL show the basis of each suggestion.

#### Scenario: Suggestion shown
- **GIVEN** a channel with collected performance numbers for past posts
- **WHEN** a publisher plans a post on that channel
- **THEN** the calendar shows a suggested time slot with the basis for the suggestion

#### Scenario: No suggestion without numbers
- **GIVEN** a channel with no collected performance numbers
- **WHEN** a publisher plans a post on that channel
- **THEN** the calendar shows no suggested time slot

### Requirement: Read-only website feed

(Could) The system SHALL offer a read-only feed of approved content to the OLF website, listing posts with their finished assets and text, and SHALL include only content whose status is approved or published.

#### Scenario: Website reads approved content
- **GIVEN** a website client with a valid consumer token and an approved post
- **WHEN** it reads the feed
- **THEN** the post appears in the feed with its finished asset

#### Scenario: Draft content excluded
- **GIVEN** a draft post and its finished asset
- **WHEN** the website client reads the feed
- **THEN** neither the draft nor its asset appears in the feed

### Requirement: Publication by scheduled jobs

(Must) The system SHALL record the due time of each scheduled post, and SHALL let scheduled Kubernetes jobs claim due posts and publish them, so that a post is claimed by at most one job run at a time.

#### Scenario: Due post published by a job
- **GIVEN** a scheduled post whose time has arrived and a job run that starts
- **WHEN** the job claims the post
- **THEN** the job publishes it and the post status becomes published

#### Scenario: Overlapping job runs
- **GIVEN** a due post already claimed by one job run
- **WHEN** a second job run starts
- **THEN** the second run does not publish that post

#### Scenario: Job stops mid-publication
- **GIVEN** a job run that claimed a post and stopped before recording the platform outcome
- **WHEN** its claim lease expires
- **THEN** the post is marked failed with an unknown outcome and is not published again automatically

### Requirement: Pipeline view per channel

(Must) The system SHALL show, per channel, the posts in the pipeline by stage (draft, in review, approved and scheduled), listing each post with its planned time, and, where the hub MCP server is available, SHALL make the same view readable by MCP clients.

#### Scenario: Pipeline counts per stage
- **GIVEN** a channel with two drafts, one post in review, one approved post and three scheduled posts
- **WHEN** a publisher opens the pipeline view for that channel
- **THEN** the view shows two drafts, one post in review, one approved post and three scheduled posts

#### Scenario: Published posts excluded
- **GIVEN** a channel with published posts only in the past
- **WHEN** the pipeline view is opened
- **THEN** no published post appears in the pipeline

### Requirement: Runway forecast per channel

(Must) The system SHALL forecast, per channel, the date on which the pipeline is expected to run empty, computed from the scheduled posts and the channel's configured posting cadence as the first cadence slot after now that has no scheduled post.

#### Scenario: Runway ends at first unfilled slot
- **GIVEN** a channel with a cadence of three posts per week and scheduled posts filling the next two weeks' slots except the slot on 2026-11-20
- **WHEN** the forecast is read
- **THEN** the expected empty date is 2026-11-20

#### Scenario: No cadence configured
- **GIVEN** a channel without a configured posting cadence
- **WHEN** the forecast is read
- **THEN** no empty date is forecast and the response states that the cadence is missing

### Requirement: Runway warning threshold

(Must) The system SHALL warn on a channel whose forecast empty date is closer than a configured threshold, with a default of seven days, and SHALL let the threshold be set globally and overridden per channel.

#### Scenario: Warning below threshold
- **GIVEN** a channel whose expected empty date is five days away and a threshold of seven days
- **WHEN** the calendar is shown
- **THEN** the channel shows a runway warning with the expected empty date

#### Scenario: No warning above threshold
- **GIVEN** a channel whose expected empty date is twenty days away
- **WHEN** the calendar is shown
- **THEN** the channel shows no runway warning

#### Scenario: Per-channel threshold
- **GIVEN** a global threshold of seven days and a channel that sets its own threshold to fourteen days
- **WHEN** that channel's expected empty date is ten days away
- **THEN** the channel shows a runway warning

### Requirement: Revision and recycling comparisons
(Should) The hub SHALL provide comparisons of the metrics of an external post before and after an accepted revision, and between a recycled post and its original.

#### Scenario: Before and after an accepted revision
- **GIVEN** a post whose revision was accepted on a given day
- **WHEN** a person compares its metrics
- **THEN** the comparison shows the metric values for the periods before and after that day

#### Scenario: Recycled post against original
- **GIVEN** a recycled post with lineage to an original post
- **WHEN** a person compares them
- **THEN** the comparison shows both posts' metrics side by side
