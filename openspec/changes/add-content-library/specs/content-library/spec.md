# Spec Delta

## Purpose

Holds all stream content in one central library: recordings, transcripts, clips, timelines and finished assets, with lineage between them, so that content can be found, recut and recycled. Retention decides what raw media is kept.

## ADDED Requirements

### Requirement: Content item kinds

(Must) The system SHALL store content items of the kinds recording, transcript, clip, timeline and finished asset, each with a stable identifier and a reference to the stream it belongs to.

#### Scenario: Item kinds are listed
- **GIVEN** a recording, its transcript, a clip cut from it, a timeline and a rendered asset exist
- **WHEN** a viewer lists the library for that stream
- **THEN** all five items are returned with their kind and stream reference

### Requirement: Clip definition

(Must) The system SHALL define a clip as a time range on one recording with a title and zero or more tags.

#### Scenario: Clip within a recording
- **GIVEN** a recording of 3600 seconds
- **WHEN** an editor creates a clip from second 120 to second 180 with a title and tags
- **THEN** the clip is stored with that range, title and tags and refers to the recording

#### Scenario: Clip range outside the recording
- **GIVEN** a recording of 3600 seconds
- **WHEN** an editor creates a clip from second 3500 to second 3700
- **THEN** the system rejects the request with a validation error and stores no clip

### Requirement: Timeline as data

(Must) The system SHALL store a timeline as data consisting of an ordered list of source ranges, overlays, captions and a reference to a layout template, and SHALL make it readable by any person or client allowed to read the library.

#### Scenario: Timeline stored and reused
- **GIVEN** an editor has uploaded a timeline made from two clips
- **WHEN** another editor opens the library and requests that timeline
- **THEN** the ordered source ranges, overlays, captions and layout template reference are returned unchanged

#### Scenario: Timeline references an unknown layout template
- **GIVEN** a timeline that names a layout template that does not exist
- **WHEN** the timeline is uploaded
- **THEN** the system rejects the upload with a validation error

### Requirement: Finished asset registration

(Must) The system SHALL register a finished asset of kind rendered video, thumbnail or image, recorded by `add-asset-upload`, as a library item once its upload has completed and been verified.

#### Scenario: Rendered video registered
- **GIVEN** an editor has finished uploading a rendered 9:16 video
- **WHEN** the upload is verified
- **THEN** the video appears in the library as a finished asset of kind rendered video

#### Scenario: Unverified upload not registered
- **GIVEN** an upload of a finished asset whose verification has failed
- **WHEN** the library is listed
- **THEN** no finished asset item is shown for that upload

### Requirement: Lineage of finished assets

(Must) The system SHALL record for every finished asset the timeline, clips and recordings it was made from.

#### Scenario: Lineage recorded on upload
- **GIVEN** a finished asset is uploaded together with the timeline and clips it was rendered from
- **WHEN** the asset is registered
- **THEN** its lineage lists that timeline, those clips and the recordings they cut from

### Requirement: Lineage queries for recycling

(Must) The system SHALL answer which finished assets, timelines and clips were made from a given stream or recording.

#### Scenario: Find all assets from a stream
- **GIVEN** three finished assets were made from clips of one stream and one asset was made from another stream
- **WHEN** a viewer asks for the assets made from the first stream
- **THEN** exactly the three assets are returned together with their timelines and clips

#### Scenario: Re-cut an old clip
- **GIVEN** an existing clip and a new timeline that uses that clip in a different layout
- **WHEN** the new timeline is stored
- **THEN** the clip and its own lineage remain unchanged

### Requirement: Usage status

(Must) The system SHALL report for each clip, timeline and finished asset whether it is never used, used in a timeline or asset, or already published, and where it has been published.

#### Scenario: Never used clip
- **GIVEN** a clip that no timeline or asset refers to and that has no publication record
- **WHEN** its usage status is read
- **THEN** the status is never used

#### Scenario: Published asset
- **GIVEN** a finished asset that has a publication record on one channel
- **WHEN** its usage status is read
- **THEN** the status is already published and names that channel

### Requirement: Suggested clips

(Should) The system SHALL store AI-suggested highlight candidates as clips with status `suggested`, whether the suggestion is created by a person or by an MCP client.

#### Scenario: Suggestion from an MCP client
- **GIVEN** an MCP client proposes a highlight range on a recording
- **WHEN** the suggestion is submitted
- **THEN** a clip with status `suggested` and the proposed range is stored and listed as suggested

### Requirement: Retention of raw recordings

(Must) The system SHALL move or delete raw recordings according to a configured retention policy while keeping clips, transcripts, timelines, finished assets and their lineage available.

#### Scenario: Raw recording moved to cold storage
- **GIVEN** a retention policy that moves recordings older than its limit to cold storage
- **WHEN** a recording exceeds that limit
- **THEN** its raw media is moved to cold storage and its clips, transcript and finished assets remain available

#### Scenario: Lineage survives deletion of raw media
- **GIVEN** a finished asset made from a recording whose raw media was deleted by policy
- **WHEN** the asset's lineage is read
- **THEN** the lineage still names that recording and marks its raw media as removed

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

### Requirement: Ownership and contributors

(Must) The system SHALL record an owner and zero or more contributors on every content item, and SHALL set the person who created the item as its owner.

#### Scenario: Owner recorded on creation
- **GIVEN** a team member with a role that allows content writes
- **WHEN** that member creates a clip
- **THEN** the clip records that member as owner

#### Scenario: Contributor added
- **GIVEN** a clip owned by one member and a second member who worked on it
- **WHEN** the owner adds the second member as contributor
- **THEN** the clip lists both members as owner and contributor

### Requirement: Contribution by team members

(Must) The system SHALL allow any team member whose role permits content writes to contribute their own recordings, clips and finished assets to the library.

#### Scenario: Member contributes a finished asset
- **GIVEN** an editor who has rendered a short from a stream
- **WHEN** the editor uploads the finished asset
- **THEN** the asset is added to the library with that editor as owner

#### Scenario: Viewer cannot contribute
- **GIVEN** a team member whose role is viewer
- **WHEN** that member attempts to upload a clip
- **THEN** the system rejects the request and stores no clip

### Requirement: Filter by contributor

(Must) The system SHALL filter the library listing by owner or contributor.

#### Scenario: Items of one contributor
- **GIVEN** clips and assets created by two different members
- **WHEN** a viewer lists the library filtered by the first member
- **THEN** only items on which that member is owner or contributor are returned

### Requirement: Editing follows roles, not ownership

(Must) The system SHALL allow a content item to be edited only by a person whose role permits editing content, regardless of whether that person is the owner.

#### Scenario: Editor edits another member's clip
- **GIVEN** a clip owned by another member and an editor
- **WHEN** the editor changes the clip title
- **THEN** the change is stored

#### Scenario: Owner without editor role
- **GIVEN** a clip created by a member whose role has since been reduced to viewer
- **WHEN** that member attempts to change the clip title
- **THEN** the system rejects the request and the title is unchanged

### Requirement: Lineage of imported broadcasts to recordings

(Must) The system SHALL link each imported Twitch broadcast to the recording of the same channel whose time span overlaps it by more than the configured share, and SHALL show a broadcast without a matching recording as unlinked.

#### Scenario: Matching recording found
- **GIVEN** an imported broadcast whose time span overlaps a recording of the same channel by more than the configured share
- **WHEN** the matching runs
- **THEN** the broadcast is linked to that recording

#### Scenario: No matching recording
- **GIVEN** an imported broadcast with no recording of the same channel
- **WHEN** the matching runs
- **THEN** the broadcast is shown as unlinked and nothing else changes

#### Scenario: Ambiguous match
- **GIVEN** an imported broadcast whose span overlaps two recordings
- **WHEN** the matching runs
- **THEN** no link is made and a person is asked to choose the recording
