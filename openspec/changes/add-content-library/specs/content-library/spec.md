# Spec Delta

## Purpose

Holds the team's stream content in one shared pool: raw stream recordings and the clips cut from them, with their transcripts, timelines and finished assets, with lineage between them, so that content can be found, recut and recycled. Retention decides what raw media is kept.

## ADDED Requirements

### Requirement: Pool item model

(Must) The system SHALL hold one shared pool of content items, each of kind raw (a stream recording) or clip (a time range on a raw item), each with a stable identifier, a stage of raw, cut or rendered, an uploader, and zero or more person tags and free tags. A finished asset registered under the finished asset requirement SHALL be listed in the pool as a clip at stage rendered that refers to its asset record.

#### Scenario: Items of both kinds listed
- **GIVEN** a raw recording uploaded by one member and a clip cut from it by another member
- **WHEN** a viewer lists the pool
- **THEN** both items are returned with their kind, stage and uploader

### Requirement: Clip definition

(Must) The system SHALL define a clip as a time range on one raw item with a title and zero or more free tags, and SHALL keep a clip at stage cut until a finished asset made from it is registered.

#### Scenario: Clip within a recording
- **GIVEN** a raw item of 3600 seconds
- **WHEN** an editor creates a clip from second 120 to second 180 with a title and tags
- **THEN** the clip is stored with that range, title and tags, refers to the raw item and has stage cut

#### Scenario: Clip range outside the recording
- **GIVEN** a raw item of 3600 seconds
- **WHEN** an editor creates a clip from second 3500 to second 3700
- **THEN** the system rejects the request with a validation error and stores no clip

### Requirement: Raw material upload

(Must) The system SHALL accept raw material from the web app through the presigned multipart upload of `add-asset-upload`, with parts of at most 95 MB, and SHALL register a verified upload as a raw item owned by the uploader and carrying the stream title and date given at upload. Where add-recording-ingest is available, recordings uploaded by the OBS plugin SHALL also be registered as raw items.

#### Scenario: Raw recording uploaded
- **GIVEN** a team member whose role permits content writes and a 40 GB stream recording
- **WHEN** the upload completes and its verification succeeds
- **THEN** a raw item owned by that member appears in the pool with the given stream title and date

#### Scenario: Interrupted upload resumes
- **GIVEN** a multipart upload of a raw recording interrupted after 30 parts
- **WHEN** the client resumes the upload
- **THEN** only the remaining parts are sent and the raw item is registered once the upload is verified

#### Scenario: Unverified upload not registered
- **GIVEN** a raw upload whose verification has failed
- **WHEN** the pool is listed
- **THEN** no raw item is shown for that upload

### Requirement: Transcript upload

(Must) The system SHALL accept a transcript for a raw item as a WebVTT or SRT file no larger than the configured transcript size limit, which defaults to 10 MB, SHALL parse it into timed segments with start, end and text, and SHALL refuse a file that cannot be parsed with an RFC 9457 Problem Details response that names the line that failed. The system SHALL NOT transcribe media itself; a raw item without an uploaded transcript has no segments.

#### Scenario: Transcript parsed into segments
- **GIVEN** a WebVTT file with three cues for a raw item
- **WHEN** the editor uploads the file
- **THEN** three segments with start, end and text are stored for that raw item

#### Scenario: Malformed cue refused
- **GIVEN** an SRT file whose timing line of cue 14 cannot be parsed
- **WHEN** the file is uploaded
- **THEN** the system rejects it with a Problem Details response that names line 14 and stores no segment

#### Scenario: Transcript above the size limit refused
- **GIVEN** a transcript file larger than the configured transcript size limit
- **WHEN** the file is uploaded
- **THEN** the system rejects it with a Problem Details response and stores no segment

#### Scenario: No transcript, no segments
- **GIVEN** a raw item for which no transcript was uploaded
- **WHEN** a viewer searches for a word spoken in that recording
- **THEN** no segment of that raw item is returned and no transcription is started

### Requirement: Transcript replacement

(Must) The system SHALL replace all segments of a raw item when a new transcript for that item is uploaded, and SHALL write one audit entry for the replacement that records the actor, the item, the time and the channel.

#### Scenario: Replacement replaces all segments
- **GIVEN** a raw item whose transcript has three segments
- **WHEN** an editor uploads a new transcript with two segments
- **THEN** only the two new segments are stored and an audit entry records the replacement

### Requirement: Speaker segments

(Should) The system SHALL accept a JSON segment file in which each segment names a speaker, and SHALL store the speaker on the segment; segments from WebVTT or SRT files SHALL carry no speaker.

#### Scenario: Segments with speakers
- **GIVEN** a JSON segment file in which segments name the speakers "streamer" and "guest"
- **WHEN** the file is uploaded
- **THEN** each segment stores its speaker and search results show it

#### Scenario: WebVTT segments without speaker
- **GIVEN** a WebVTT transcript
- **WHEN** the file is uploaded
- **THEN** its segments store no speaker

### Requirement: Free and person tags

(Must) The system SHALL store on each item free tags that match `^[a-z0-9-]{2,30}$`, at most 20 per item, and person tags that refer to hub people or accounts, and SHALL allow tags to be changed only by a person whose role permits editing content.

#### Scenario: Free tag accepted
- **GIVEN** a clip and an editor
- **WHEN** the editor adds the free tag "speedrun"
- **THEN** the tag is stored on the clip

#### Scenario: Invalid free tag refused
- **GIVEN** an editor
- **WHEN** the editor adds the tag "Speed Run" with an upper-case letter and a space
- **THEN** the system rejects the tag with a validation error and stores nothing

#### Scenario: Person tag added
- **GIVEN** a raw item and a hub person who appears in the recording
- **WHEN** an editor adds that person as a person tag
- **THEN** the raw item lists that person

#### Scenario: Viewer cannot tag
- **GIVEN** a team member whose role is viewer
- **WHEN** that member adds a free tag to a clip
- **THEN** the system rejects the request and the clip keeps its tags

### Requirement: Uploader of items

(Must) The system SHALL record the uploader of every pool item as its owner when the item is created, and SHALL keep that uploader unchanged when other members edit the item.

#### Scenario: Uploader recorded on creation
- **GIVEN** a team member whose role permits content writes
- **WHEN** that member creates a clip
- **THEN** the clip records that member as uploader

#### Scenario: Uploader unchanged by edits
- **GIVEN** a clip uploaded by one member and later retitled by an editor who is another member
- **WHEN** the clip is read
- **THEN** the uploader is still the first member

### Requirement: Contribution by team members

(Must) The system SHALL allow any team member whose role permits content writes to add their own raw material, clips and finished assets to the pool, owned by that member.

#### Scenario: Member contributes a finished asset
- **GIVEN** an editor who has rendered a short from a stream
- **WHEN** the editor uploads the finished asset
- **THEN** the asset is added to the pool with that editor as uploader

#### Scenario: Viewer cannot contribute
- **GIVEN** a team member whose role is viewer
- **WHEN** that member attempts to upload a clip
- **THEN** the system rejects the request and stores no clip

### Requirement: Pool listing filters

(Must) The system SHALL filter the pool listing by uploader ("mine" for the caller or a named member), by person tag, by free tag and by stage, and SHALL apply all given filters together.

#### Scenario: Items of the caller
- **GIVEN** items uploaded by two different members
- **WHEN** a member lists the pool with the filter "mine"
- **THEN** only items that member uploaded are returned

#### Scenario: Combined filters
- **GIVEN** raw items of which one is uploaded by member A, tagged with person B and at stage raw, and another is uploaded by member A at stage cut
- **WHEN** a viewer lists the pool filtered by uploader A, person B and stage raw
- **THEN** only the first raw item is returned

### Requirement: Timeline as data

(Must) The system SHALL store a timeline as data consisting of an ordered list of source ranges, overlays, captions and a reference to a layout template, SHALL refuse a timeline that references a layout template that does not exist, and SHALL make the timeline readable by any person or client allowed to read the pool; where add-brand-kit is available, the system SHALL also refuse a timeline that references a layout template that does not exist.

#### Scenario: Timeline stored and reused
- **GIVEN** an editor has uploaded a timeline made from two clips
- **WHEN** another editor requests that timeline
- **THEN** the ordered source ranges, overlays, captions and layout template reference are returned unchanged

#### Scenario: Timeline references an unknown layout template
- **GIVEN** add-brand-kit is available and a timeline that names a layout template that does not exist
- **WHEN** the timeline is uploaded
- **THEN** the system rejects the upload with a validation error

### Requirement: Finished asset registration

(Must) The system SHALL register a finished asset of kind rendered video, thumbnail or image, recorded by `add-asset-upload`, in the pool once its upload has completed and been verified, and SHALL move every clip that the asset's timeline uses to stage rendered.

#### Scenario: Rendered video registered
- **GIVEN** an editor has finished uploading a rendered 9:16 video made from a timeline with two clips
- **WHEN** the upload is verified
- **THEN** the video appears in the pool at stage rendered and both clips are at stage rendered

#### Scenario: Unverified upload not registered
- **GIVEN** an upload of a finished asset whose verification has failed
- **WHEN** the pool is listed
- **THEN** no finished asset item is shown for that upload

#### Scenario: Asset with a missing timeline rejected
- **GIVEN** a finished asset that names a timeline which does not exist
- **WHEN** the registration is attempted
- **THEN** the system rejects the registration and the asset is not listed

### Requirement: Lineage of finished assets

(Must) The system SHALL record for every finished asset the timeline, clips and raw items it was made from.

#### Scenario: Lineage recorded on upload
- **GIVEN** a finished asset is uploaded together with the timeline and clips it was rendered from
- **WHEN** the asset is registered
- **THEN** its lineage lists that timeline, those clips and the raw items they cut from

### Requirement: Lineage queries for recycling

(Must) The system SHALL answer which finished assets, timelines and clips were made from a given raw item.

#### Scenario: Find all assets from a stream
- **GIVEN** three finished assets were made from clips of one raw item and one asset was made from another raw item
- **WHEN** a viewer asks for the assets made from the first raw item
- **THEN** exactly the three assets are returned together with their timelines and clips

#### Scenario: Re-cut an old clip
- **GIVEN** an existing clip and a new timeline that uses that clip in a different layout
- **WHEN** the new timeline is stored
- **THEN** the clip and its own lineage remain unchanged

### Requirement: Usage status

(Must) The system SHALL report for each clip, timeline and finished asset whether it is never used or used in a timeline or asset; where add-content-distribution is available, the system SHALL also report whether it is already published and where, using the publication records of that change.

#### Scenario: Never used clip
- **GIVEN** a clip that no timeline or asset refers to and that has no publication record
- **WHEN** its usage status is read
- **THEN** the status is never used

#### Scenario: Published asset
- **GIVEN** add-content-distribution is available and a finished asset that has a publication record on one channel
- **WHEN** its usage status is read
- **THEN** the status is already published and names that channel

### Requirement: Suggested clips

(Could) The system SHALL store highlight candidates that a person suggests as clips with status `suggested`, and listed as suggested. Where add-hub-mcp-server is available, the system SHALL also store suggestions created by an MCP client in the same way.

#### Scenario: Suggestion from a person
- **GIVEN** an editor who marks a highlight range on a raw item
- **WHEN** the suggestion is saved
- **THEN** a clip with status `suggested` and the marked range is stored and listed as suggested

#### Scenario: Suggestion from an MCP client
- **GIVEN** an MCP client proposes a highlight range on a raw item and hub MCP is available
- **WHEN** the suggestion is submitted
- **THEN** a clip with status `suggested` and the proposed range is stored and listed as suggested

### Requirement: Retention of raw recordings

(Must) The system SHALL move or delete raw media according to a configured retention policy while keeping clips, transcripts, timelines, finished assets and their lineage available.

#### Scenario: Raw recording moved to cold storage
- **GIVEN** a retention policy that moves recordings older than its limit to cold storage
- **WHEN** a recording exceeds that limit
- **THEN** its raw media is moved to cold storage and its clips, transcript and finished assets remain available

#### Scenario: Lineage survives deletion of raw media
- **GIVEN** a finished asset made from a raw item whose raw media was deleted by policy
- **WHEN** the asset's lineage is read
- **THEN** the lineage still names that raw item and marks its raw media as removed

### Requirement: Explicit and audited deletion

(Must) The system SHALL delete a content item only on an explicit deletion request, and SHALL write an audit entry for every deletion that records who deleted what, when, and through which channel.

#### Scenario: Deletion request audited
- **GIVEN** an editor with the right to delete a clip
- **WHEN** the editor deletes that clip through the web app
- **THEN** the clip is removed and an audit entry records the editor, the clip, the time and the web channel

#### Scenario: No implicit deletion
- **GIVEN** a clip that is no longer used by any timeline
- **WHEN** no deletion request has been made
- **THEN** the clip is kept

### Requirement: Editing follows roles, not ownership

(Must) The system SHALL allow a content item to be edited only by a person whose role permits editing content, regardless of whether that person is the uploader.

#### Scenario: Editor edits another member's clip
- **GIVEN** a clip uploaded by another member and an editor
- **WHEN** the editor changes the clip title
- **THEN** the change is stored

#### Scenario: Uploader without editor role
- **GIVEN** a clip created by a member whose role has since been reduced to viewer
- **WHEN** that member attempts to change the clip title
- **THEN** the system rejects the request and the title is unchanged

### Requirement: Lineage of imported broadcasts to recordings

(Must) Where add-twitch-insights is available, the system SHALL link each imported Twitch broadcast to the raw item of the same channel whose time span overlaps it by more than the configured share, and SHALL show a broadcast without a matching raw item as unlinked.

#### Scenario: Matching recording found
- **GIVEN** an imported broadcast whose time span overlaps a raw item of the same channel by more than the configured share
- **WHEN** the matching runs
- **THEN** the broadcast is linked to that raw item

#### Scenario: No matching recording
- **GIVEN** an imported broadcast with no raw item of the same channel
- **WHEN** the matching runs
- **THEN** the broadcast is shown as unlinked and nothing else changes

#### Scenario: Ambiguous match
- **GIVEN** an imported broadcast whose span overlaps two raw items
- **WHEN** the matching runs
- **THEN** no link is made and a person is asked to choose the raw item
