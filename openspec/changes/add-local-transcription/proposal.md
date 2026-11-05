# Proposal

Conventional Commits: `feat(desktop)` (code PR). Phase: 5 (Local & Brand). Depends on: `add-media-job-queue` (jobs), `add-recording-ingest` (uploaded recordings), `add-access-control` (sign-in), `add-client-and-mcp-auth` (device-bound tokens).

Strand: content.

## Why

Transcripts make a recording searchable and let Claude find cut candidates. Transcription needs a GPU, so it runs
only on team PCs that have one. Recordings are made on any streaming PC, so a GPU PC claims transcription jobs
from the hub's queue instead of waiting for a recording on its own disk. Work must not be lost when a PC sleeps,
disconnects or restarts, and a GPU PC without a network must still be able to finish and sync later.

## What Changes

- The desktop app is installed only on PCs with a usable GPU. On first start it detects the GPU; without one it
  refuses media work and says why.
- The desktop app claims transcription jobs from the hub queue, holds each with a lease and heartbeats, and uses the
  local original when its checksum matches the job, otherwise it downloads the source.
- Transcription per spoken audio track as the recording's metadata from the hub marks it, with word-level timestamps,
  the hub's glossary and each track's language from that metadata; excluded tracks (live mix and music by default)
  are never transcribed.
- A speech model is placed or downloaded only after a one-click consent; nothing is downloaded silently.
- The transcript and a local index (segments, track, markers) are uploaded and attached to the recording session;
  results are reported under the claim's lease and are refused if the lease is no longer current.
- Transcription works offline for a claimed job; results sync later without duplicates.
- The desktop app shows its job queue (claimed and waiting jobs, transcription and upload progress) and keeps it across restarts.
- Not part of this change: cluster re-transcription as fallback (Non-Goal, see design.md).

## Capabilities

### New Capabilities

- `local-transcription`: GPU detection and refusal, device and model choice, consented model placement, claiming and
  working on jobs, local originals, transcription output.
- `desktop-sync`: transcript and index upload and attachment, fenced result reporting, offline sync, the job queue
  view and restart survival.

### Modified Capabilities

None (no existing capabilities).

## Impact

- Desktop app (Tauri 2, Rust core): GPU detection, speech model runner on the GPU, job client with leases, local index
  store, sync outbox, queue UI shared with web components.
- Server: result endpoint under lease generation and transcript attach endpoint (job queue in `add-media-job-queue`,
  search in `add-content-library`).
- Disk: local model files and transcript caches on the GPU PC.
