# Tasks

## 1. GPU detection and model setup

- [ ] 1.1 Write failing tests that a PC without a usable GPU refuses to claim jobs and shows why, using a fake GPU probe, and verify they fail
- [ ] 1.2 Implement GPU detection on first start and the refusal, and verify the tests of 1.1 pass
- [ ] 1.3 Write failing tests that a missing model is offered for a one-click download, that nothing starts before the click, and that a job with a missing model is not started, and verify they fail
- [ ] 1.4 Implement the consented model placement and the missing-model message, and verify the tests of 1.3 pass
- [ ] 1.5 Write failing tests that device and model choices are saved locally and used after restart, and verify they fail
- [ ] 1.6 Implement the device and model settings, and verify the tests of 1.5 pass

## 2. Claiming and lease-bound work

- [ ] 2.1 Write failing tests that the desktop app transcribes only claimed jobs and sends heartbeats during long work, with an injected clock and a fake queue, and verify they fail
- [ ] 2.2 Implement the job client with claim, heartbeat and lease tracking, and verify the tests of 2.1 pass
- [ ] 2.3 Write failing tests that a refused heartbeat stops the work and discards partial output, and verify they fail
- [ ] 2.4 Implement the stop-on-lost-lease behaviour, and verify the tests of 2.3 pass
- [ ] 2.5 Write failing tests that a matching local original is used without download and a differing one leads to a download, using a fake transport, and verify they fail
- [ ] 2.6 Implement the checksum comparison and the local-original decision, and verify the tests of 2.5 pass

## 3. Transcription

- [ ] 3.1 Write failing tests that only tracks the recording's metadata marks as spoken are transcribed, excluded tracks never are, and each spoken track uses the language its metadata gives, using fixture audio and a fake recognizer, and verify they fail
- [ ] 3.2 Implement reading track roles and languages from the recording metadata, and verify the tests of 3.1 pass
- [ ] 3.3 Write failing tests that segments carry word-level start and end times and that the glossary fetched with the job reaches the recognizer while an empty glossary changes nothing, and verify they fail
- [ ] 3.4 Implement the local transcription pipeline with word timings and the job's glossary input, and verify the tests of 3.3 pass
- [ ] 3.5 Write failing tests that a claimed job completes offline from its local original and that the index is written before upload starts, and verify they fail
- [ ] 3.6 Implement offline completion and the index-first write order, and verify the tests of 3.5 pass
- [ ] 3.7 Document in the desktop README the GPU requirement, device choice and model placement, and verify each documented step against the first-start check

## 4. Transcript upload, fenced reporting and sync

- [ ] 4.1 Write failing tests that a completed transcript and index are uploaded and attached to the job's session, with markers and their offsets included, using a fake hub, and verify they fail
- [ ] 4.2 Implement transcript and index upload with attachment, and verify the tests of 4.1 pass
- [ ] 4.3 Write failing tests that a report under the current lease moves the job to done and that a report refused as stale makes the desktop app discard its transcript, and verify they fail
- [ ] 4.4 Implement lease-generation reporting and discard on refusal, and verify the tests of 4.3 pass
- [ ] 4.5 Write failing tests that three transcripts completed offline are all reported and attached after reconnect, and that a re-sync after lost confirmation attaches once, and verify they fail
- [ ] 4.6 Implement the local outbox with idempotent attach by transcript identifier, and verify the tests of 4.5 pass

## 5. Job queue view and restart

- [ ] 5.1 Write failing tests that the queue lists each job with its state, transcription progress and upload progress, and verify they fail
- [ ] 5.2 Implement the queue state and queue view, and verify the tests of 5.1 pass
- [ ] 5.3 Write failing tests that after restart a job with a valid lease continues from local state and a job whose lease expired is released with its partial output discarded, with an injected clock, and verify they fail
- [ ] 5.4 Implement persisted queue state and the restart rule, and verify the tests of 5.3 pass

## 6. Integration

- [ ] 6.1 Run the integration tests tagged `integration` with a test hub, the job queue and a short fixture recording, and verify a job is claimed, transcribed from a fetched source, and its transcript attached
- [ ] 6.2 Open the code pull request titled `feat(desktop): add local transcription`
