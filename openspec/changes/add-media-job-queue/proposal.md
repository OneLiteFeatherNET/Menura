# Proposal

Conventional Commits: `feat(jobs)` (code PR). Phase: 4 (Recording & Jobs). Depends on: `add-recording-ingest`, `add-access-control`, `add-hub-mcp-server`.

Strand: content.

## Why

Transcription and cutting need a GPU, and the GPU is on a few team PCs, not on the cluster and not on every
streaming PC. Recordings are made on any streaming PC, many without a GPU. The hub therefore has to queue the
media work it creates and let any GPU-equipped team PC claim it, with a guarantee that one job runs on one PC at a
time and that a PC that disappears does not lose the job.

## What Changes

- After a recording reaches uploaded, the hub queues a transcription job for it. People and MCP clients can request
  cut-proposal jobs.
- GPU-equipped desktop clients claim jobs with a lease that expires. A holder renews the lease with heartbeats.
  At most one holder works on a job at a time.
- An expired lease returns the job to the queue; failed attempts are retried with increasing delays up to a limit,
  then the job is marked failed and visible.
- Results are accepted only from the current lease holder.
- A claim tells the claimant which checksum the source must have; a claimant that holds that original file uses it
  instead of downloading it.
- Every claim, completion and failure is audit-logged.
- The queue lives in PostgreSQL; no other queue system is introduced.

## Capabilities

### New Capabilities

- `media-jobs`: job types, states, leases with heartbeat and expiry, single holder, retry, result fencing, claim
  rules, local-original preference, job visibility and audit.
- `job-mcp-tools`: media job status reads and cut requests through the hub MCP; no tool claims, leases, completes or fails a job.

### Modified Capabilities

None (no existing capabilities).

## Impact

- Server: job table and claim, heartbeat, complete and fail endpoints in PostgreSQL, lease expiry evaluated with an
  injected clock, job creation on upload and on cut requests, job status for the web app and MCP.
- Desktop app: claims jobs and renews leases (`add-local-transcription`, `add-local-editing-mcp`).
- No GPU on the cluster and no media processing in the queue; the queue carries references and results only.
