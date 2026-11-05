# Spec Delta

## Purpose

Defines how the desktop app reports transcription results to the hub under the job lease, uploads the transcript and index, attaches them to the recording session, syncs offline work without duplicates, and shows and restores its job queue across restarts.

## ADDED Requirements

### Requirement: Upload transcript and index and attach them
(Must) When a transcription job is complete, the system SHALL upload its transcript and index and attach them to the recording session of the job.

#### Scenario: Attached to session
- **GIVEN** a completed transcription of a claimed job
- **WHEN** the desktop app uploads the transcript and index
- **THEN** they are attached to the recording session of that job

#### Scenario: Markers included
- **GIVEN** a recording with two markers
- **WHEN** its index is uploaded
- **THEN** the index contains both markers with their offsets

### Requirement: Results reported under the lease
(Must) The system SHALL report a completed job with the generation of the lease it holds, and SHALL discard the transcript when the hub refuses the report because the lease is no longer current.

#### Scenario: Current lease accepted
- **GIVEN** a completed job whose lease is current
- **WHEN** the result is reported
- **THEN** the job moves to done

#### Scenario: Stale lease refused
- **GIVEN** a completed job whose lease expired and was taken by another PC
- **WHEN** the result is reported
- **THEN** the hub refuses it and the desktop app discards its transcript

### Requirement: Offline operation and later sync
(Must) While the hub is unreachable, the system SHALL keep completed transcripts and indexes pending locally and SHALL report and upload them when the hub is reachable again.

#### Scenario: Sync after reconnect
- **GIVEN** three transcripts completed while offline
- **WHEN** the connection to the hub is restored
- **THEN** all three are reported and attached to their sessions

#### Scenario: No duplicate attach
- **GIVEN** a transcript uploaded once whose confirmation was lost
- **WHEN** the desktop app syncs again
- **THEN** the transcript is attached once to the session

### Requirement: Job queue view
(Must) The system SHALL show the desktop app's job queue with each job's state (waiting, claimed, transcribing, uploading, done or failed), its transcription progress and its upload progress.

#### Scenario: Queue entries
- **GIVEN** one job transcribing and one waiting
- **WHEN** the user opens the queue
- **THEN** both are listed with their current state and progress

### Requirement: Queue survives restart
(Must) When the desktop app restarts, the system SHALL restore its queue and pending results from local storage, and SHALL resume a job only while its lease is still valid, otherwise it SHALL release the job.

#### Scenario: Restart mid-transcription with valid lease
- **GIVEN** a job at 40 percent whose lease is still valid when the app restarts
- **WHEN** the app starts
- **THEN** the job continues from its local state

#### Scenario: Restart after lease expiry
- **GIVEN** a job whose lease expired while the app was closed
- **WHEN** the app starts
- **THEN** the job is released and its partial output is discarded
