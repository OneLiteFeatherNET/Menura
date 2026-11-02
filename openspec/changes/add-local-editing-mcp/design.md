# Design

## Context

The desktop app runs only on GPU PCs (principle 2 and D2). Claude runs on the same PC and starts local tools itself.
The hub owns the library, brand kit, output profiles, editing rules and toolchain manifest, and queues cut-proposal
jobs (`add-media-job-queue`). The lab prototype cuts shorts locally with output profiles, loudness targets and editing
rules (`content-pipeline` README). See proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- Claude can cut and render a short from a stream by conversation, with the person confirming the cut.
- Every PC renders the same way: rules, profiles, templates and brand come from the hub.
- Nothing is rendered or uploaded without a validated result and, for cuts, a person's confirmation.
- No network port is opened by the editing server (principle 11).

**Non-Goals:**
- No web-based timeline editor; timelines are created and changed through the local MCP server.
- No server-side rendering; the cluster never runs ffmpeg or renders a frame.
- No automatic installation; every download or install needs consent.

## Decisions

- **stdio transport for the local MCP server.** Claude starts it as a child process; the operating system's process
  boundary is the access control, and no port exists to be reached from the network.
  Rejected: a loopback HTTP server (any local process could call it; a port is a listener to be secured).
- **Hub calls use the person's device-bound token from the keychain.** The stdio server holds no token of its own.
  Rejected: a separate local token for the editing server (a second secret to protect and revoke).
- **Library access is a proxy with no persistent copy.** The hub's role checks stay authoritative.
  Rejected: syncing the library to the PC (duplicates access rules).
- **Source fetching by segment or byte range into a content-addressed cache.** Cache keys are source and range.
  Rejected: downloading whole recordings.
- **Timelines are data, pinned to brand and template versions.** A timeline reused from the library renders the same way
  everywhere; a person's profile only changes overridable tokens.
  Rejected: storing the render recipe as a shell command (not portable, not reviewable).
- **Validation is a separate step between render and upload.** Duration, loudness and resolution are measured on the
  output file; a failed render never reaches the library.
  Rejected: validating the timeline only.
- **Semi-automatic flow as a state machine: proposed → confirmed → rendering → validated → uploaded.** Only the
  confirmation needs a person; an adjustment returns the cut to proposed.
  Rejected: fully unattended cutting (nobody sees the result before the library); a prompt before every step (slow).
- **Toolchain check reports; consent gates every download.** The check names what is missing and offers one-click
  consented installs per item (D10).
  Rejected: bundling ffmpeg with the app (licence and update burden) and silent installers (principle 12).
- **Cut proposals are jobs.** A proposal requested by a person or an MCP client is claimed by a GPU PC like any other
  media work, so it runs on a PC that has the local originals or fetches them.
  Rejected: proposing cuts inside the hub (no GPU there).

## Risks / Trade-offs

- [Risk] Long renders block Claude's session → Mitigation: renders run as jobs with progress queries.
- [Risk] Different ffmpeg builds produce different output → Mitigation: the manifest pins the version and features; validation
  measures the output, not the encoder.
- [Risk] Cache grows on the PC → Mitigation: a size limit set by the person, with least-recently-used eviction (assumption, see report).
- [Risk] Preview differs from the final render → Mitigation: the preview uses the same timeline and profile at lower
  resolution; the final render still passes validation.
