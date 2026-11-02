# Spec Delta

## Purpose

Defines how a finished recording becomes a server-side recording session: announcement by the OBS plugin, metadata and marker storage, owner and contributors, resumable presigned multipart upload, checksum verification, session states, duplicate detection and the start of transcription jobs, while the server keeps media out of its own request path.

## ADDED Requirements

### Requirement: Announce a recording session
(Must) When the OBS plugin announces a finished recording, the system SHALL create a recording session in state announced and SHALL return its identifier together with the upload targets for each announced file.

#### Scenario: Announcement creates session
- **GIVEN** an OBS plugin holding a device-bound token of a person with the editor role
- **WHEN** it announces a recording with two files and their checksums
- **THEN** the system creates one session in state announced with both files

#### Scenario: Announcement without rights
- **GIVEN** a token of a person without the editor role
- **WHEN** it announces a recording
- **THEN** the system refuses the request with 403 and creates no session

### Requirement: Store announcement metadata with the session
(Must) The system SHALL store with each recording session the metadata announced with the recording: scene collection, start and end time, audio track layout, and stream title and category when known. Each track in the audio track layout SHALL carry its role, spoken or excluded, and for spoken tracks its language, taken from the person's settings in the hub.

#### Scenario: Metadata stored
- **GIVEN** an announcement with scene collection, start and end time, audio track layout and stream title
- **WHEN** the session is read back
- **THEN** all of these values are returned unchanged

#### Scenario: Track roles and languages stored
- **GIVEN** an announcement whose audio track layout marks the microphone track as spoken in German and the live-mix track as excluded
- **WHEN** the session is read back
- **THEN** the microphone track is returned as spoken with language German and the live-mix track as excluded

#### Scenario: Defaults from the hub
- **GIVEN** a person whose settings in the hub mark the microphone track as spoken in German and the live-mix track as excluded
- **WHEN** the OBS plugin announces a recording without changes to these settings
- **THEN** the announced audio track layout carries those roles and languages

#### Scenario: Optional stream fields absent
- **GIVEN** an announcement without stream title and category
- **WHEN** the session is created
- **THEN** the session is created and those fields are empty

### Requirement: Store markers with the session
(Must) The system SHALL store each marker added during the stream with the recording session it belongs to, including its time offset from the start of the recording.

#### Scenario: Markers travel with the recording
- **GIVEN** an announcement that includes three markers
- **WHEN** the session is read back
- **THEN** the three markers are returned with their offsets

### Requirement: Presigned multipart upload
(Must) The system SHALL issue presigned multipart upload URLs for each file of a recording session, and SHALL NOT receive media bytes through its own endpoints.

#### Scenario: Upload targets issued
- **GIVEN** a session in state announced with one file of 3 GB
- **WHEN** the OBS plugin requests upload targets
- **THEN** the system returns presigned URLs for each part and the session moves to uploading

### Requirement: Resumable upload
(Must) When an upload is interrupted by network loss or an OBS restart, the system SHALL report the parts already confirmed for each file so that the upload continues from the first missing part.

#### Scenario: Resume after network loss
- **GIVEN** an upload in which parts 1 to 40 of 80 are confirmed
- **WHEN** the connection is lost and restored
- **THEN** the system reports parts 1 to 40 as confirmed and the upload continues with part 41

#### Scenario: Resume after OBS restart
- **GIVEN** an upload interrupted by an OBS restart
- **WHEN** the plugin requests the session after restart
- **THEN** the system returns the same session and its confirmed parts

### Requirement: Checksum verification on completion
(Must) When all parts of a file are uploaded, the system SHALL compare the checksum of the stored file with the checksum announced for it and SHALL move the session to uploaded only if all files match.

#### Scenario: Checksum matches
- **GIVEN** a session whose files are all uploaded
- **WHEN** the stored checksums match the announced checksums
- **THEN** the session moves to uploaded

#### Scenario: Checksum mismatch
- **GIVEN** a session whose stored file checksum differs from the announced checksum
- **WHEN** the completion is verified
- **THEN** the session moves to failed and the mismatch is returned to the OBS plugin

### Requirement: Recording session states
(Must) The system SHALL keep each recording session in exactly one of the states announced, uploading, uploaded, indexed or failed, and SHALL allow only the transitions announced to uploading, uploading to uploaded, uploaded to indexed, and announced or uploading to failed.

#### Scenario: Index after upload
- **GIVEN** a session in state uploaded
- **WHEN** the system finishes indexing its metadata into the library
- **THEN** the session moves to indexed

#### Scenario: Forbidden transition
- **GIVEN** a session in state announced
- **WHEN** a request tries to move it directly to indexed
- **THEN** the system refuses the transition and keeps the state announced

### Requirement: Transcription job on upload
(Must) When a recording session reaches state uploaded, the system SHALL create one transcription job for it in the media job queue.

#### Scenario: Job created
- **GIVEN** a session that has just been verified as uploaded
- **WHEN** the state changes to uploaded
- **THEN** exactly one transcription job for that session is queued

### Requirement: No server-side media processing
(Must) The system SHALL NOT transcode, render or otherwise process media bytes of a recording on the server.

#### Scenario: Stored file unchanged
- **GIVEN** a verified upload of a recording
- **WHEN** the stored file is compared byte-for-byte with the uploaded file
- **THEN** the two are identical

### Requirement: Duplicate detection by checksum
(Should) When a recording is announced whose checksum equals that of an existing session of the same person, the system SHALL flag the new session as a duplicate and SHALL report the existing session.

#### Scenario: Duplicate announced
- **GIVEN** an existing session of a person with checksum X
- **WHEN** the same person announces a recording with checksum X
- **THEN** the new session is flagged as duplicate of the existing session

#### Scenario: Different checksum
- **GIVEN** an existing session of a person with checksum X
- **WHEN** the same person announces a recording with checksum Y
- **THEN** the new session carries no duplicate flag

### Requirement: Optional live chunk upload
(Could) Where live chunk upload is enabled, the system SHALL accept media parts of a recording while its stream is still running, using the same session and upload mechanism as post-stream upload.

#### Scenario: Chunk during stream
- **GIVEN** live chunk upload is enabled and a stream is running
- **WHEN** the OBS plugin uploads a chunk of the recording
- **THEN** the system stores the chunk as a part of the same session

#### Scenario: Disabled by default
- **GIVEN** live chunk upload is not enabled
- **WHEN** the OBS plugin attempts to upload a chunk during a stream
- **THEN** the system refuses the chunk and the recording follows the post-stream upload

### Requirement: Ingest limited to the editor right
(Must) The system SHALL accept recording ingest calls only from a device-bound token or a person with the editor role.

#### Scenario: Viewer cannot ingest
- **GIVEN** a person with only the viewer role
- **WHEN** the person calls an ingest endpoint
- **THEN** the system refuses the request with 403

### Requirement: Owner and contributors of a session
(Must) The system SHALL record the person whose token announced a recording session as its owner, SHALL record every contributor added to it, and SHALL return owner and contributors with the session so that the library can filter by contributor.

#### Scenario: Owner recorded on announcement
- **GIVEN** a person with the editor role announces a recording with a device-bound token
- **WHEN** the session is read back
- **THEN** the person is returned as owner of the session

#### Scenario: Contributor added
- **GIVEN** a session owned by one person and a person with the editor role
- **WHEN** the editor adds a second person as contributor
- **THEN** the session returns both people as contributors and the owner unchanged

### Requirement: Any team member contributes own recordings
(Must) The system SHALL allow any team member holding the editor role to contribute their own recordings, without requiring ownership by any other person or team.

#### Scenario: Two team members contribute
- **GIVEN** two team members, each holding the editor role
- **WHEN** each announces a recording of their own
- **THEN** each session is owned by the member who announced it

### Requirement: Changes to another person's session follow roles
(Must) The system SHALL allow a change to a session owned by another person only to a person holding the editor role, and SHALL NOT grant that change on ownership alone.

#### Scenario: Editor changes another's session
- **GIVEN** a session owned by person A and a person B with the editor role
- **WHEN** B changes its metadata
- **THEN** the system accepts the change and records B as the actor in the audit log

#### Scenario: Owner without editor role
- **GIVEN** a session owned by person A, where A has lost the editor role
- **WHEN** A attempts to change the session
- **THEN** the system refuses the request with 403

### Requirement: Upload independent of a GPU
(Must) The system SHALL accept announcements and uploads from any OBS plugin, whether or not the PC has a GPU, and SHALL leave transcription to the media job queue.

#### Scenario: Recording from PC without GPU
- **GIVEN** a streaming PC without a usable GPU and no desktop app
- **WHEN** a recording is announced and uploaded
- **THEN** the session reaches uploaded and a transcription job is queued

### Requirement: Upload parts fit through the CDN proxy
(Must) The system SHALL issue presigned multipart uploads with parts of at most 95 MB, because the object storage endpoint is reached through a CDN proxy that refuses request bodies of 100 MB and more.

#### Scenario: Large recording split into allowed parts
- **GIVEN** a finished recording of 12 GB
- **WHEN** the OBS plugin requests upload URLs
- **THEN** every part URL is for a part of at most 95 MB
- **AND** the recording completes as one object after all parts are uploaded
