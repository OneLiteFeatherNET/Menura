# Proposal

Conventional Commits: `feat(desktop)` (code PR). Phase: 5 (Local & Brand). Depends on: `add-content-library`, `add-brand-kit`, `add-media-job-queue` (cut-proposal jobs), `add-access-control`
(device-bound tokens), `add-client-and-mcp-auth` (device sign-in and MCP authorization), `add-hub-mcp-server` (the hub tools and the editing playbook), `add-recording-ingest` (sources), `add-local-transcription` (speech model, GPU PC setup).

Strand: content.

## Why

Cutting a short from yesterday's stream should be a conversation, not a manual job. Claude has to reach the source
material, cut it, render it and upload the result. Video must never be rendered on the cluster, and no two PCs may
cut differently. The desktop app on a GPU PC therefore exposes a local MCP server that Claude starts over stdio, with
the rules, brand and output profiles coming from the hub, and a person confirming each cut before the final render runs.

## What Changes

- The desktop app offers its editing tools to Claude through a local MCP server over stdio. The server opens no
  network port. The editing tools refuse to run on a PC without a usable GPU and say why.
- Through it Claude lists and searches the library (proxied to the hub with the person's device-bound token), fetches
  only the needed source ranges into a local cache, creates and updates timelines, renders locally, and previews frames.
- Renders use output profiles (short 9:16 1080x1920, long 16:9, and others) with loudness targets (-14 LUFS, -1.5 dBTP
  for shorts), layout templates and the brand kit pinned by the timeline, with the person's profile applied where the
  brand kit allows it.
- A render is validated for duration, loudness and resolution before upload.
- Every result (clips, timelines, finished assets) is uploaded to the library with lineage.
- Editing rules are served by the hub and exposed to Claude as MCP resources.
- Dry runs produce low-resolution proxies before the final render.
- A toolchain check against the hub's manifest reports what is missing and how to fix it. Any download or install needs
  one click of consent.
- Semi-automatic workflow: Claude proposes a cut, a person confirms or adjusts, then render and upload run unattended.
  Cut-proposal jobs from the hub queue are claimed by the desktop app.
- **Non-Goals:** no web-based timeline editor and no server-side rendering.

## Capabilities

### New Capabilities

- `local-editing`: the stdio MCP surface, GPU requirement, hub calls, library access, source caching, timelines, output
  profiles and brand, render validation, upload with lineage, editing rules, toolchain check with consent, cut proposal
  and confirmation.
- `editing-playbook`: the editing playbook served through the hub MCP (rules, per-OS toolchain manifest, workflow prompts) so that a team member can ask Claude to cut content.

### Modified Capabilities

None (no existing capabilities).

## Impact

- Desktop app: stdio MCP server, render pipeline with ffmpeg, local cache, toolchain check, cut-proposal job claims.
- Server: timeline, clip and asset upload endpoints with lineage (storage in `add-content-library`); toolchain manifest
  and editing rules served to the desktop app (`add-hub-mcp-server`, `add-brand-kit`).
- Network: only needed byte ranges are downloaded; the cluster renders nothing.
