# Design

## Context

Transcription and cutting run only on GPU PCs of the team (principle 2). The hub owns the sessions and the
original media in object storage (principle 1). Desktop clients hold their own tokens (`add-access-control`).
Jobs carry references and results, never media. See proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- Every uploaded recording gets its transcript, even when no GPU PC is online at upload time.
- A job runs on one PC at a time, and a vanished PC never blocks a job for longer than its lease.
- No second queueing system to operate.

**Non-Goals:**
- Running any media work on the cluster or in the queue itself.
- Priorities, schedules or fairness between team members (first claim wins; can be added later).
- Cancellation of running jobs from the web app.

## Decisions

- **The queue is a PostgreSQL table, claimed with row locking and skip-locked selection.** The database is already
  the one store of metadata (principle 1), so the queue adds no system to run or back up.
  Rejected: a message broker (a further stateful system); the object store (no atomic claim).
- **Leases with expiry, evaluated against an injected clock.** A claim is valid until its expiry; heartbeats move the
  expiry forward. Tests drive time explicitly.
  Rejected: holding a database lock for the whole job (a sleeping PC would hold it indefinitely).
- **Fencing by lease generation.** Each claim gets a generation number; a result is accepted only with the current
  generation. This prevents a slow PC from overwriting a result produced by the PC that took over.
  Rejected: accepting any result for a done job (the late result can be stale).
- **Claimant decides on the local original by checksum.** The server only names the checksum; the claimant verifies its
  own file. The server never learns where files are on a PC.
  Rejected: server-side tracking of local files per PC (privacy, and it goes stale).
- **Retry limit and backoff are configuration.** Default: 3 attempts with delays that double, so a broken recording
  does not loop forever. (Assumption, see report.)
  Rejected: unlimited retries (a broken job would hold a GPU PC forever).

## Risks / Trade-offs

- [Risk] Clock skew between PCs and the hub shortens or extends leases → Mitigation: lease expiry is evaluated by the
  hub's clock only; the PC sends heartbeats and does not judge expiry itself.
- [Risk] No GPU PC online for days → Mitigation: jobs stay queued and are visible; the queue status shows the age of
  the oldest job, so the backlog is seen.
- [Risk] A bad recording loops across PCs → Mitigation: the attempt limit and the visible failed state.
