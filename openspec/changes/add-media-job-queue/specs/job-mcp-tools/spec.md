# job-mcp-tools

## Purpose

MCP tools of the hub MCP for the media job queue.

## ADDED Requirements

### Requirement: Media job tools
(Must) The hub SHALL offer MCP tools to read the status of media jobs that the caller may see and to request a cut proposal job for given recordings or clips, and SHALL NOT offer any tool that claims, leases, completes or fails a job.

#### Scenario: Job status read
- **GIVEN** a transcription job for a recording that the caller may see
- **WHEN** the client reads the job
- **THEN** the result shows the job type, its state and its time of last change, and no lease or worker secret

#### Scenario: Cut request
- **GIVEN** an editor who asks Claude to cut shorts from yesterday's stream
- **WHEN** the client requests a cut proposal job for the recordings of that stream with an output profile
- **THEN** the hub creates the job in the queued state and returns its identifier

#### Scenario: No claim tool
- **GIVEN** any authenticated client
- **WHEN** the client lists the available tools
- **THEN** no tool claims, leases, completes or fails a media job
