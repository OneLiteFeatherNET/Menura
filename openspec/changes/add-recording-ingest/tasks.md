# Tasks

## 1. Recording sessions on the server

- [ ] 1.1 Write failing tests for creating a session in state announced with metadata (including track roles and languages) and markers, and for refusing callers without the editor role, with a fake token verifier, and verify they fail
- [ ] 1.2 Implement the recording session resource with metadata and marker storage in PostgreSQL and the access check, and verify the tests of 1.1 pass
- [ ] 1.3 Write failing tests for the session state machine: only announced → uploading → uploaded → indexed, failed reachable from announced or uploading, and no other transitions, and verify they fail
- [ ] 1.4 Implement the state machine, and verify the tests of 1.3 pass
- [ ] 1.5 Write failing tests that a session returns owner and contributors, that an editor can add a contributor, and that a session owned by a person who lost the editor role refuses changes with 403, and verify they fail
- [ ] 1.6 Implement owner and contributor fields, the contributor endpoint and the role-based change check, and verify the tests of 1.5 pass

## 2. Presigned resumable upload

- [ ] 2.1 Write failing tests that the server issues one presigned multipart upload per file and reports confirmed parts, using a fake object storage, and verify they fail
- [ ] 2.2 Implement presigned multipart URL issuance and the confirmed-parts report against S3-compatible storage, and verify the tests of 2.1 pass
- [ ] 2.3 Write failing tests that an upload resumed after interruption continues from the last confirmed part and that no ingest endpoint accepts media bytes, and verify they fail
- [ ] 2.4 Implement the confirmed-parts resume path and the absence of any media-bytes endpoint, and verify the tests of 2.3 pass
- [ ] 2.5 Write failing tests that a matching checksum moves the session to uploaded, a mismatch moves it to failed, and stored bytes equal uploaded bytes, and verify they fail
- [ ] 2.6 Implement checksum verification on completion without any transcoding step, and verify the tests of 2.5 pass
- [ ] 2.7 Write failing tests that reaching uploaded queues exactly one transcription job for the session, using a fake job queue, and verify they fail
- [ ] 2.8 Implement the job creation on the uploaded transition, and verify the tests of 2.7 pass

## 3. Duplicates and optional live chunks

- [ ] 3.1 Write failing tests that an announced session with a checksum already known for the same person is flagged with a reference to the existing session, and verify they fail
- [ ] 3.2 Implement duplicate detection by checksum, and verify the tests of 3.1 pass
- [ ] 3.3 Write failing tests that live chunk upload, only when enabled, uses the same session and upload mechanism, and verify they fail
- [ ] 3.4 Implement the optional live chunk path behind its switch, and verify the tests of 3.3 pass

## 4. OBS plugin announcement and upload

- [ ] 4.1 Write failing tests for recording-stop and stream-stop detection that announce the files with metadata including track roles and languages from the hub settings, using a fake OBS event source, and verify they fail
- [ ] 4.2 Implement stop detection and the announcement payload in the OBS plugin, and verify the tests of 4.1 pass
- [ ] 4.3 Write failing tests that the plugin computes checksums before announcing and uploads without a desktop app running, using a fake hub, and verify they fail
- [ ] 4.4 Implement checksum computation and the upload client in the OBS plugin, and verify the tests of 4.3 pass
- [ ] 4.5 Write failing tests that an upload resumes from the first unconfirmed part after a restart and retries with increasing delays after network loss, with an injected clock and no sleeps, and verify they fail
- [ ] 4.6 Implement persisted resume state and retry, and verify the tests of 4.5 pass
- [ ] 4.7 Write failing tests for the highlight hotkey adding a marker with its offset and for the dock showing pending, uploading, uploaded and failed states, and verify they fail
- [ ] 4.8 Implement the marker hotkey and the dock state display, and verify the tests of 4.7 pass
- [ ] 4.9 Document the announcement payload and upload states in the obs-plugin README and verify the documented fields match the test fixture

## 5. Integration

- [ ] 5.1 Run the integration tests tagged `integration` against PostgreSQL and an S3-compatible test container (the API that Ceph RGW offers), and verify a recording goes from announced to uploaded with a resumed upload and a checksum check, and that a transcription job is queued
- [ ] 5.2 Write failing tests that upload URLs for a 12 GB recording are issued for parts of at most 95 MB and that the parts complete to one object, and verify they fail
- [ ] 5.3 Cap the multipart part size at 95 MB, and verify the tests pass
- [ ] 5.4 Open the code pull request titled `feat(ingest): add recording ingest`
