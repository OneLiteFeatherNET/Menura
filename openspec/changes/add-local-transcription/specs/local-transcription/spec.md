# Spec Delta

## Purpose

Defines how a GPU-equipped desktop app transcribes recordings it claims from the hub: GPU detection and refusal, device and model choice, consented model placement, transcription of spoken tracks with word-level timing and glossary, use of a local original when the checksum matches, and work that stays within the claim's lease.

## ADDED Requirements

### Requirement: GPU required for media work
(Must) On first start the desktop app SHALL detect whether the PC has a usable GPU, and WHILE no usable GPU is present the desktop app SHALL refuse media work and SHALL say why.

#### Scenario: GPU present
- **GIVEN** a PC with a usable NVIDIA, AMD, Intel or Apple Silicon GPU
- **WHEN** the desktop app starts for the first time
- **THEN** the GPU is recorded as usable and media work is enabled

#### Scenario: No usable GPU
- **GIVEN** a PC without a usable GPU
- **WHEN** the desktop app tries to claim a job
- **THEN** the desktop app does not claim it and shows that no usable GPU was found

### Requirement: Device and model choice per PC
(Should) The system SHALL let the user choose the GPU device and the speech model for each PC, and SHALL store that choice on the PC only.

#### Scenario: Choice saved
- **GIVEN** a user who selects a GPU device and a model on one PC
- **WHEN** the desktop app restarts on that PC
- **THEN** transcription uses the saved device and model

#### Scenario: Choices are local
- **GIVEN** two PCs with different choices
- **WHEN** each transcribes a job
- **THEN** each uses its own choice

### Requirement: Consented model placement
(Must) The system SHALL download or place a speech model only after the person has clicked to consent, and SHALL NOT download any model silently.

#### Scenario: Missing model offered
- **GIVEN** the configured model is not present on the PC
- **WHEN** the setup check runs
- **THEN** the desktop app offers a one-click download and starts nothing until the person clicks

#### Scenario: No model, no transcription
- **GIVEN** the configured model is not present
- **WHEN** a job is claimed
- **THEN** the job is not started and the message names the missing model

### Requirement: Transcribe spoken tracks only
(Must) The system SHALL transcribe each audio track that the recording's metadata from the hub marks as spoken and SHALL NOT transcribe any track that the metadata marks as excluded.

#### Scenario: Spoken track transcribed
- **GIVEN** a recording whose metadata marks its microphone track as spoken and its game track as excluded
- **WHEN** the job runs on a PC that did not make the recording
- **THEN** a transcript is produced for the microphone track and none for the game track

#### Scenario: Excluded track skipped
- **GIVEN** a recording whose metadata marks the live-mix track as excluded
- **WHEN** the job runs
- **THEN** no transcript is produced for the live-mix track

### Requirement: Spoken track language from metadata
(Must) The system SHALL transcribe each spoken track in the language that the recording's metadata gives for it and SHALL detect the language only when the metadata gives none.

#### Scenario: Metadata language used
- **GIVEN** a recording whose metadata gives the microphone track the language German
- **WHEN** the microphone track is transcribed
- **THEN** it is recognized as German without language detection

#### Scenario: Missing language detected
- **GIVEN** a spoken track whose metadata gives no language
- **WHEN** it is transcribed
- **THEN** the language is detected and recorded with the transcript

### Requirement: Word-level timestamps
(Must) The system SHALL produce each transcript as segments that carry word-level start and end times.

#### Scenario: Word timings present
- **GIVEN** a spoken track with two sentences
- **WHEN** the transcript is produced
- **THEN** every word has a start and an end time within the recording

### Requirement: Glossary of expected terms
(Must) The system SHALL fetch the central glossary of expected names and terms, which the hub serves next to the editing rules, together with the claimed job, and SHALL bias recognition toward it.

#### Scenario: Glossary term recognized
- **GIVEN** the glossary served with the job contains a game name
- **WHEN** a spoken track contains that name
- **THEN** the transcript spells it as in the glossary

#### Scenario: Hub glossary used instead of a local one
- **GIVEN** a PC with a local glossary file that differs from the glossary served with the job
- **WHEN** the job runs
- **THEN** recognition is biased toward the served glossary and the local file is not used

#### Scenario: Empty glossary
- **GIVEN** the hub serves an empty glossary
- **WHEN** a track is transcribed
- **THEN** the transcription runs without glossary input

### Requirement: Use the local original when the checksum matches
(Must) When the claim names a source checksum and the PC holds an original with that checksum, the system SHALL transcribe the local original and SHALL NOT download the source.

#### Scenario: Local original transcribed
- **GIVEN** a claimed job and a local original with the named checksum
- **WHEN** the job runs
- **THEN** no download of the source takes place

#### Scenario: Local original differs
- **GIVEN** a claimed job and a local file whose checksum differs
- **WHEN** the job runs
- **THEN** the source is downloaded and the local file is not used

### Requirement: Work only on claimed jobs within the lease
(Must) The system SHALL transcribe only jobs it holds a lease for, SHALL send heartbeats while it works, and SHALL stop work on a job whose lease it no longer holds.

#### Scenario: Heartbeats while working
- **GIVEN** a job transcribing for ten minutes
- **WHEN** the lease interval elapses
- **THEN** the desktop app has sent heartbeats that kept the lease

#### Scenario: Lease lost
- **GIVEN** a job whose lease expired and was taken by another PC
- **WHEN** the desktop app's next heartbeat is refused
- **THEN** the desktop app stops working on the job and discards its partial output

### Requirement: Transcription works offline for a claimed job
(Must) The system SHALL complete the transcription of a claimed job from the local original without a network connection.

#### Scenario: Offline completion
- **GIVEN** a claimed job with a local original and no network
- **WHEN** the transcription runs
- **THEN** the transcript is produced locally and kept for sync

### Requirement: Transcript and index are produced locally
(Must) The system SHALL produce for each transcribed track a transcript and a local index with segments, track and markers, and SHALL write them before any upload starts.

#### Scenario: Index before upload
- **GIVEN** a completed transcription
- **WHEN** upload is about to start
- **THEN** the local index is already on disk with all segments and markers
