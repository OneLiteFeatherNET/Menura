# Spec Delta

## Purpose

Lets editors put finished videos and images into the hub's private object storage from the web app, so that they can be listed, previewed and scheduled. Media bytes go directly from the browser to storage; the server only issues links, checks checksums and keeps the records.

## ADDED Requirements

### Requirement: Presigned multipart upload of finished assets

(Must) The system SHALL let an editor upload a finished video or image asset from the web application directly into the private Menura bucket through presigned multipart upload, with parts of at most 95 MB, and SHALL NOT receive or store media bytes on any of its own endpoints.

#### Scenario: Upload targets issued
- **GIVEN** an editor who starts the upload of a 3 GB rendered video
- **WHEN** the editor's client requests the upload
- **THEN** the system creates an upload session in state uploading and returns presigned URLs for each part, none larger than 95 MB

#### Scenario: Upload limited to the editor role
- **GIVEN** a signed-in viewer
- **WHEN** the viewer requests an upload session
- **THEN** the system refuses the request with 403 and creates no session

#### Scenario: No media endpoint
- **GIVEN** the deployed server
- **WHEN** its endpoints are listed
- **THEN** no endpoint accepts media bytes as its request body

### Requirement: Presigned multipart upload of raw material

(Must) The system SHALL let an editor upload a raw stream recording from the web application through the same presigned multipart upload, with parts of at most 95 MB, and SHALL record each verified upload as raw material with the stream title and date given by the editor.

#### Scenario: Raw recording upload targets issued
- **GIVEN** an editor who starts the upload of a 40 GB stream recording with its stream title and date
- **WHEN** the editor's client requests the upload
- **THEN** the system creates an upload session in state uploading, returns presigned URLs for each part, none larger than 95 MB, and records the upload as raw material with that title and date

### Requirement: Raw material size limit

(Must) The system SHALL refuse a raw recording larger than the configured raw material size limit, which defaults to 50 GB.

#### Scenario: Raw recording above the size limit refused
- **GIVEN** a raw recording of 60 GB and the configured raw material size limit of 50 GB
- **WHEN** the editor's client requests the upload
- **THEN** the system refuses the request with a Problem Details response that names the limit and creates no session

### Requirement: Automatic ingest stays with add-recording-ingest

(Must) Where add-recording-ingest is available, recordings made by the OBS plugin SHALL be uploaded and recorded by that change, not by this one.

#### Scenario: OBS recording handled by its own change
- **GIVEN** a recording made by the OBS plugin on a streaming PC
- **WHEN** the recording is uploaded
- **THEN** the upload is performed and recorded by `add-recording-ingest`, and this change creates no session for it

### Requirement: Resumable upload

(Must) When an upload is interrupted, the system SHALL report the parts already confirmed so that the upload continues from the first part that is not confirmed.

#### Scenario: Resume after connection loss
- **GIVEN** an upload in which parts 1 to 40 of 80 are confirmed
- **WHEN** the browser reconnects and asks for the session
- **THEN** the system reports parts 1 to 40 as confirmed and the upload continues with part 41

#### Scenario: Resume after a page reload
- **GIVEN** an upload session whose editor reloaded the web application mid-upload
- **WHEN** the editor opens the same upload again
- **THEN** the system returns the same session and its confirmed parts, and no second session is created

### Requirement: Checksum verification on completion

(Must) When all parts of an upload are uploaded, the system SHALL compare the checksum of the stored object with the checksum declared at the start of the upload, and SHALL list the asset as available only if both match.

#### Scenario: Checksum matches
- **GIVEN** an upload session whose parts are all confirmed
- **WHEN** the stored checksum equals the declared checksum
- **THEN** the asset is recorded as available and listed

#### Scenario: Checksum mismatch
- **GIVEN** an upload session whose stored checksum differs from the declared checksum
- **WHEN** the completion is verified
- **THEN** the upload is marked failed, the asset is not listed as available, and the editor sees the mismatch

### Requirement: Asset record

(Must) The system SHALL store for every finished asset its kind (video or image), format, duration for videos, resolution, size in bytes, checksum and uploader, and SHALL refuse to record an asset whose required fields are missing.

#### Scenario: Record complete
- **GIVEN** a finished video uploaded by an editor
- **WHEN** the upload is recorded as available
- **THEN** the record holds its format, duration, resolution, size, checksum and the uploader's identity

#### Scenario: Image has no duration
- **GIVEN** a finished image uploaded by an editor
- **WHEN** the upload is recorded as available
- **THEN** the record holds its format, resolution, size, checksum and uploader, and no duration

#### Scenario: Missing field refused
- **GIVEN** an upload declaration without a resolution
- **WHEN** the declaration is submitted
- **THEN** the system refuses it and names the missing field

### Requirement: Short-lived signed links for listing and preview

(Must) The system SHALL list assets to people who may see them and SHALL give preview and download access only through signed links that expire after a configured short time, and SHALL answer 404 for an asset the caller may not see.

#### Scenario: Preview with a signed link
- **GIVEN** an asset the editor may see
- **WHEN** the editor opens its preview
- **THEN** the preview loads from a signed link that expires within the configured lifetime

#### Scenario: Expired link refused
- **GIVEN** a signed link issued earlier than the configured lifetime ago
- **WHEN** the link is used
- **THEN** the storage refuses the request and the editor is shown a new link on the next preview

#### Scenario: Hidden asset answers not found
- **GIVEN** an asset that a signed-in member may not see
- **WHEN** the member requests the asset
- **THEN** the system answers 404 without revealing that the asset exists

### Requirement: Explicit and audited deletion

(Must) The system SHALL delete an asset only when an editor explicitly requests it, SHALL refuse the deletion while a post still references the asset, and SHALL write exactly one audit entry with the actor, the asset, the time and the channel for each deletion.

#### Scenario: Deletion audited
- **GIVEN** an editor who deletes an asset that no post references
- **WHEN** the deletion completes
- **THEN** the asset is removed and one audit entry names the editor, the asset, the time and the channel

#### Scenario: Asset in use refused
- **GIVEN** an asset referenced by a scheduled post
- **WHEN** an editor requests its deletion
- **THEN** the system refuses the deletion, names the referencing post, and deletes nothing

#### Scenario: No deletion by any other path
- **GIVEN** a stored asset that is no longer listed
- **WHEN** no editor has requested its deletion
- **THEN** the asset and its record remain unchanged

### Requirement: Duplicate detection by checksum

(Should) When an upload declares a checksum that is already recorded for an asset of the same uploader, the system SHALL flag the new upload with a reference to the existing asset and SHALL let the editor continue or cancel.

#### Scenario: Duplicate flagged
- **GIVEN** an asset already recorded with checksum X by an editor
- **WHEN** the same editor declares an upload with checksum X
- **THEN** the upload is flagged with a reference to the existing asset

#### Scenario: Duplicate confirmed
- **GIVEN** a flagged upload
- **WHEN** the editor confirms that the file is intended
- **THEN** the upload continues and a second record with the same checksum is created
