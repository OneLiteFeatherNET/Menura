# Spec Delta

## Purpose

Defines the media job queue on the hub: which jobs exist, how GPU-equipped team PCs claim and hold them through leases and heartbeats, how failed or abandoned work is retried, how results are accepted from the current holder only, how a claimant reuses an original file it already holds, and how jobs are audited and kept out of reach of people who may not see them.

## ADDED Requirements

### Requirement: Transcription job for each uploaded recording
(Must) When a recording session reaches uploaded, the system SHALL queue exactly one transcription job for that session.

#### Scenario: One job per recording
- **GIVEN** a session that reaches uploaded
- **WHEN** the state change is recorded
- **THEN** exactly one transcription job for the session is in state queued

#### Scenario: No duplicate job on repeated event
- **GIVEN** a transcription job already queued for a session
- **WHEN** the uploaded transition is recorded again
- **THEN** no second job is created

### Requirement: Cut-proposal jobs on request
(Should) When a person or an MCP client requests a cut proposal for a recording or clip, the system SHALL queue a cut-proposal job for it.

#### Scenario: Cut requested through MCP
- **GIVEN** an MCP client acting for a person with the editor role
- **WHEN** the client requests a cut proposal for a clip
- **THEN** a cut-proposal job is queued for that clip

### Requirement: Job states
(Must) The system SHALL keep each job in exactly one of the states queued, claimed, done or failed, and SHALL allow only the transitions queued to claimed, claimed to queued on lease expiry, claimed to done, and claimed or queued to failed.

#### Scenario: Forbidden transition
- **GIVEN** a job in state queued
- **WHEN** a request tries to set it to done
- **THEN** the system refuses the transition and keeps the job queued

### Requirement: Claim with a lease
(Must) When a claimant claims a queued job, the system SHALL grant a lease with an expiry time and SHALL allow at most one holder of a job at any time.

#### Scenario: Single holder
- **GIVEN** a job claimed by one PC with an active lease
- **WHEN** a second PC tries to claim the same job
- **THEN** the second claim is refused and the job stays with the first holder

### Requirement: Claim carries the job context
(Must) When a claimant is granted a transcription job, the system SHALL return with the lease everything needed to work on a recording from another PC: the source checksum, the audio track roles and spoken-track languages from the recording's metadata, and the current central glossary.

#### Scenario: Job for a recording from a PC without a GPU
- **GIVEN** a recording uploaded from a PC without a GPU, with track 1 spoken in German and track 3 excluded as music
- **WHEN** a GPU PC claims its transcription job
- **THEN** the claim response contains the checksum, track 1 as spoken in German, track 3 as excluded, and the central glossary

### Requirement: Heartbeat renews the lease
(Must) While a claimant works on a job, it SHALL renew the lease with heartbeats, and the system SHALL extend the lease on each accepted heartbeat.

#### Scenario: Heartbeat accepted
- **GIVEN** a job claimed with a lease expiring in 2 minutes
- **WHEN** the holder sends a heartbeat
- **THEN** the lease expiry moves forward and the holder keeps the job

### Requirement: Expired lease returns the job to the queue
(Must) If a lease passes its expiry without a heartbeat, the system SHALL return the job to state queued and SHALL count the abandoned attempt.

#### Scenario: Holder disappears
- **GIVEN** a job claimed by a PC that stops sending heartbeats
- **WHEN** the lease expiry time has passed
- **THEN** the job is queued again and another claimant can claim it

### Requirement: Results accepted from the current holder only
(Must) The system SHALL accept a completion or failure report only from the holder of the current lease, and SHALL refuse a report from a holder whose lease has expired.

#### Scenario: Late result refused
- **GIVEN** a job whose lease expired and was claimed by another PC
- **WHEN** the first PC reports completion
- **THEN** the system refuses the report and keeps the result of the second holder

#### Scenario: Current holder completes
- **GIVEN** a job claimed by a PC with an active lease
- **WHEN** that PC reports completion with its results attached
- **THEN** the job moves to done

### Requirement: Retry with increasing delays and a limit
(Must) When an attempt fails, the system SHALL queue the job again after an increasing delay, and SHALL mark it failed and visible to its owner once the number of attempts reaches the configured limit.

#### Scenario: Retry before limit
- **GIVEN** a job whose first attempt failed and the limit is 3 attempts
- **WHEN** the failure is recorded
- **THEN** the job is queued again after a delay that is longer than the previous delay

#### Scenario: Limit reached
- **GIVEN** a job whose third attempt failed with the limit of 3
- **WHEN** the failure is recorded
- **THEN** the job moves to failed and appears in the job status of its owner

### Requirement: Claim only by GPU-equipped team PCs
(Must) The system SHALL accept job claims only from desktop clients that declare a usable GPU and that act for a person holding the editor role.

#### Scenario: Client without GPU
- **GIVEN** a desktop client that declares no usable GPU
- **WHEN** it claims a job
- **THEN** the system refuses the claim with 403

#### Scenario: Eligible client claims
- **GIVEN** a desktop client with a usable GPU acting for an editor
- **WHEN** it claims a queued job
- **THEN** the claim is accepted

### Requirement: Jobs from any origin
(Must) The system SHALL queue and process jobs for recordings whatever PC they were recorded on, including PCs without a GPU.

#### Scenario: Recording from PC without GPU
- **GIVEN** a recording uploaded by a PC without a GPU
- **WHEN** its transcription job is claimed
- **THEN** a GPU-equipped team PC can claim it

### Requirement: Use the local original when the checksum matches
(Must) The claim response SHALL state the checksum of the source file, and when the claimant holds an original file with that checksum, the claimant SHALL use that file instead of downloading the source.

#### Scenario: Local original used
- **GIVEN** a claimant holding the original recording with the checksum named in the claim
- **WHEN** it works on the job
- **THEN** it reads the local file and downloads no source

#### Scenario: Local copy differs
- **GIVEN** a claimant holding a file whose checksum differs from the claim
- **WHEN** it works on the job
- **THEN** it downloads the source and does not use the local file

### Requirement: Job responses carry no credentials
(Must) The system SHALL return to a claimant only short-lived download targets for the source, and SHALL NOT include credentials, tokens or secrets in job responses, job logs or job status.

#### Scenario: Claim response inspected
- **GIVEN** a claim response
- **WHEN** its content is searched for secrets
- **THEN** only short-lived download targets are present

### Requirement: Job status visible to editors only
(Must) The system SHALL show job status to people holding the editor role, and SHALL answer 404 to any caller who may not see the job.

#### Scenario: Editor reads status
- **GIVEN** a queued job and an editor
- **WHEN** the editor requests its status
- **THEN** the status is returned

#### Scenario: Viewer requests status
- **GIVEN** a job and a viewer
- **WHEN** the viewer requests its status
- **THEN** the system answers 404

### Requirement: Audit of claims, completions and failures
(Must) The system SHALL record an audit entry for every claim, completion and failure of a job, with the acting person and the channel.

#### Scenario: Claim audited
- **GIVEN** a successful claim by a desktop client of a person
- **WHEN** the claim is recorded
- **THEN** one audit entry records the person, the job, the time and the channel desktop
