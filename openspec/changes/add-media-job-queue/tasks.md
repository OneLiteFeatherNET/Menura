# Tasks

## 1. Job creation

- [ ] 1.1 Write failing tests that a session reaching uploaded queues exactly one transcription job and that a repeated transition creates no second job, using a fake clock, and verify they fail
- [ ] 1.2 Implement the job table in PostgreSQL and job creation on the uploaded transition, and verify the tests of 1.1 pass
- [ ] 1.3 Write failing tests that a cut-proposal request from an MCP client with the editor role queues one cut-proposal job, and verify they fail
- [ ] 1.4 Implement the cut-proposal request path, and verify the tests of 1.3 pass

## 2. Claims, leases and heartbeats

- [ ] 2.1 Write failing tests that only the first of two claims is granted and that the second is refused with 403 or a conflict, and that claims from a client without GPU are refused, with a fake clock, and verify they fail
- [ ] 2.2 Implement the claim with row locking, a lease expiry and the GPU and editor checks, and verify the tests of 2.1 pass
- [ ] 2.3 Write failing tests that a granted transcription claim returns the source checksum, the track roles and spoken-track languages from the recording metadata, and the current central glossary, and verify they fail
- [ ] 2.4 Return the job context with the lease, and verify the tests of 2.3 pass
- [ ] 2.5 Write failing tests that a heartbeat extends the lease and that a lease passing its expiry without heartbeat returns the job to queued, with an injected clock and no sleeps, and verify they fail
- [ ] 2.6 Implement heartbeat and lease expiry evaluation, and verify the tests of 2.5 pass
- [ ] 2.7 Write failing tests that the job state machine allows only the listed transitions, and verify they fail
- [ ] 2.8 Implement the state transitions, and verify the tests of 2.7 pass

## 3. Results, retries and fencing

- [ ] 3.1 Write failing tests that a late result from an expired lease is refused and the second holder's result is kept, and that the current holder's completion moves the job to done, and verify they fail
- [ ] 3.2 Implement generation-based fencing for completion and failure reports, and verify the tests of 3.1 pass
- [ ] 3.3 Write failing tests that a failed attempt is retried after a longer delay and that the job becomes failed and visible to its owner at the attempt limit, and verify they fail
- [ ] 3.4 Implement retry with increasing delays and the attempt limit, and verify the tests of 3.3 pass

## 4. Local originals and credentials

- [ ] 4.1 Write failing tests that a claimant holding a matching original uses it without a download, and that a differing local file leads to a download, using a fake transport, and verify they fail
- [ ] 4.2 Implement the checksum in the claim response and the claimant's local-original decision, and verify the tests of 4.1 pass
- [ ] 4.3 Write failing tests that claim responses, job logs and job status contain no credentials and only short-lived download targets, and verify they fail
- [ ] 4.4 Implement the response shaping and redaction, and verify the tests of 4.3 pass

## 5. Visibility and audit

- [ ] 5.1 Write failing tests that an editor reads job status and a viewer receives 404, and verify they fail
- [ ] 5.2 Implement job status visibility, and verify the tests of 5.1 pass
- [ ] 5.3 Write failing tests that every claim, completion and failure writes exactly one audit entry with person, job, time and channel, and verify they fail
- [ ] 5.4 Implement the audit writes for job actions, and verify the tests of 5.3 pass

## 6. Job MCP tools

- [ ] 6.1 Write failing unit tests that a job status read returns job type, state and time of last change, and no lease or worker secret, and that a caller who may not see the job receives 404; verify they fail first
- [ ] 6.2 Implement the job status tool on the hub MCP server; verify the 6.1 tests pass
- [ ] 6.3 Write failing unit tests that a cut request through MCP by an editor queues one cut-proposal job in state queued and returns its identifier, and that a viewer is refused; verify they fail first
- [ ] 6.4 Implement the cut request tool on top of the cut-proposal request path of task 1.4; verify the 6.3 tests pass
- [ ] 6.5 Write failing unit tests that the tool list offered to any authenticated client contains no tool that claims, leases, completes or fails a media job; verify they fail first
- [ ] 6.6 Implement the tool registration with that restriction; verify the 6.5 tests pass
- [ ] 6.7 Document the job tools in the hub MCP documentation and verify each documented tool name exists in the tool registry test

## 7. Integration

- [ ] 7.1 Run the integration tests tagged `integration` against PostgreSQL with two simulated clients, and verify that a job is claimed by one, that the lease expiry hands it to the other after a missed heartbeat, and that the late result is refused
- [ ] 7.2 Open the code pull request titled `feat(jobs): add media job queue`
