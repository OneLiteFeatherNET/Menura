# Spec Delta

## Purpose

Defines what the OBS plugin does when a recording or stream ends: it detects the stop, signs in with its own identity, announces the finished files with metadata, uploads them itself with resume after interruptions, shows upload state in its dock and lets the streamer add markers during the stream. The desktop app is not involved.

## ADDED Requirements

### Requirement: Detect end of recording and stream
(Must) When an OBS recording stops or an OBS stream stops, the plugin SHALL announce the finished file or files of that recording or stream together with their metadata.

#### Scenario: Recording stopped
- **GIVEN** a signed-in OBS plugin
- **WHEN** the streamer stops an OBS recording
- **THEN** the plugin announces the finished file or files with the metadata

#### Scenario: Stream stopped
- **GIVEN** a stream that was recorded locally
- **WHEN** the stream stops
- **THEN** the announcement contains the stream start and end time, the scene collection and the title and category if known

### Requirement: Plugin uploads its own recordings
(Must) The plugin SHALL upload the finished files of its announced recordings itself to the hub, without any other application running on the PC.

#### Scenario: Upload without desktop app
- **GIVEN** no desktop app is installed on the PC
- **WHEN** a recording is announced
- **THEN** the plugin uploads the file to the hub

### Requirement: Resumable upload persisted by the plugin
(Must) The plugin SHALL persist the upload progress of each file in operating-system-permissioned local storage and SHALL resume an interrupted upload from the first part the server has not confirmed, including after an OBS restart.

#### Scenario: Restart mid-upload
- **GIVEN** an upload that was at part 40 of 80 when OBS closed
- **WHEN** OBS starts again and the plugin resumes
- **THEN** the upload continues from the first unconfirmed part

#### Scenario: Retry after network loss
- **GIVEN** an upload that fails because the network is lost
- **WHEN** the network returns
- **THEN** the plugin retries with increasing delays and continues the upload

### Requirement: Checksum before announcement
(Must) The plugin SHALL compute the checksum of each finished file before announcing it.

#### Scenario: Checksum announced
- **GIVEN** a finished recording file
- **WHEN** the plugin announces it
- **THEN** the announcement contains the checksum of the file as it is on disk

### Requirement: Sign-in of the plugin
(Must) The plugin SHALL sign in with its own identity through the loopback PKCE sign-in of the access-control capability and SHALL hold its device-bound token itself.

#### Scenario: First start
- **GIVEN** a plugin without a token
- **WHEN** the streamer starts sign-in
- **THEN** the plugin receives a device-bound token for the person and uses it for announcements and uploads

### Requirement: Highlight marker hotkey
(Should) When the streamer presses the highlight hotkey during a stream, the plugin SHALL add a marker with its time offset from the start of the stream and SHALL include all markers in the announcement.

#### Scenario: Marker added
- **GIVEN** a running stream started 1 hour ago
- **WHEN** the streamer presses the highlight hotkey
- **THEN** a marker at offset 01:00:00 is added to the stream

#### Scenario: Markers in announcement
- **GIVEN** two markers added during a stream
- **WHEN** the stream stops
- **THEN** the announcement contains both markers

### Requirement: Dock shows upload state
(Should) The plugin dock SHALL show for each recording whether it is pending, uploading, uploaded or failed.

#### Scenario: Uploading state
- **GIVEN** a recording whose upload is in progress
- **WHEN** the streamer opens the dock
- **THEN** the dock shows that recording as uploading with its progress

#### Scenario: Failed state
- **GIVEN** a recording whose checksum verification failed on the hub
- **WHEN** the streamer opens the dock
- **THEN** the dock shows that recording as failed
