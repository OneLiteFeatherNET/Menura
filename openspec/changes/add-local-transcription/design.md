# Design

## Context

Recordings are uploaded by the OBS plugin (`add-recording-ingest`). The hub creates a transcription job for each
uploaded recording and queues it (`add-media-job-queue`). GPU PCs of the team claim those jobs. The prototype
`content-pipeline` transcribes with a local whisper model from user-placed files, with track roles and a language
setting per track (lab config, `[audio]` and `[transcription]`). See proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- Every uploaded recording is transcribed by some GPU PC, whichever PC recorded it.
- A PC that loses its network or lease never produces a duplicate or a stale attachment.
- Nothing downloads or installs without a click.

**Non-Goals:**
- Cluster re-transcription fallback. A job waits for a GPU PC; the cluster never transcribes.
- Speaker identification beyond track identity; the index records the track.
- Hub-hosted models or automatic model download.

## Decisions

- **Desktop app claims jobs; recording PCs are not involved.** A recording made on a PC without a GPU is
  transcribed by any GPU team PC through the queue, so transcription no longer depends on the recording PC.
  Rejected: local transcription on the recording PC (needs a GPU there, and the desktop app is not installed on
  it); transcription on the cluster (principle 2).
- **Local original is used by checksum match only.** The claim names the checksum; the PC compares its own file. This
  saves the download when the PC recorded the stream itself, and never trusts a file by name.
  Rejected: matching by file name or path (wrong files, unsafe).
- **Consent-based model placement.** The first-start check shows the model state and offers a one-click download.
  Rejected: silent download on first run (violates principle 12).
- **Track roles, track languages and glossary come from the hub.** The recording's metadata (announced by the OBS plugin,
  see `add-recording-ingest`) carries each track's role and language; the central glossary is served next to the
  editing rules and fetched with the claimed job. A GPU PC transcribes recordings made on other PCs, so it cannot
  know their audio tracks, and a PC-local glossary would make the same recording come out differently on two PCs.
  Rejected: keep track roles and glossary on the PC as in the prototype (a claimant does not know the track setup of
  the recording PC, so it would transcribe the wrong tracks or none, and the setting would drift per PC).
- **Results are fenced by lease generation.** A PC that lost its lease cannot attach stale output (see
  `add-media-job-queue`).
  Rejected: last-write-wins attach (a slow PC could overwrite a newer transcript).
- **Attach is idempotent by transcript identifier.** A retry after a lost confirmation cannot duplicate the attach.
- **Queue state is persisted locally on every change.** A restart reads the same store; a job continues only if its lease
  is still valid.

## Risks / Trade-offs

- [Risk] Transcription slows the GPU PC during a stream → Mitigation: the queue view shows the running job and its
  progress, so the streamer sees the load; device choice is per PC.
- [Risk] Long offline period lets leases expire → Mitigation: the result is refused on sync and the job is re-run by
  another PC; the local transcript is discarded and the queue shows it.
- [Risk] Model file corruption → Mitigation: the model is checked against a known checksum in the setup check.
