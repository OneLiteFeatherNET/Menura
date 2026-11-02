# Proposal

Conventional Commits: `feat(activity)` (code PR). Phase: 6 (Overlay). Depends on: `add-overlay-system` (scene feed, module system), `add-access-control` (device-bound tokens, identity, privacy rules), `add-obs-plugin` (local relay, privacy filter host, plugin token).

Strand: overlay.

## Why

Streamers who work with Claude Code on stream want viewers to see that an agent is working, waiting for input or finished, without revealing what the agent is doing. Today nothing reports agent state to the stream, and any naive reporting would leak prompts, file contents or private paths. The feed must therefore be useful on air and privacy-safe by construction.

## What Changes

- A Claude Code hook, installed with the OBS plugin on streamer PCs, reports agent lifecycle events to the OBS plugin on the same PC over local IPC (an OS-permissioned socket or named pipe). Nothing listens on a network port. Events: session start and stop, thinking or working, tool use by category, waiting for input, subagent spawned and finished, and done.
- The OBS plugin applies the privacy filter before anything is forwarded: only sessions in allowlisted project roots, no prompt text, no file contents, no paths outside the allowlist, tool names reduced to categories. A kill switch stops all forwarding at once. The plugin forwards with its own device-bound token; the hub refuses events from anywhere else.
- The hub aggregates events into per-person agent presence (active sessions, state, subagent count), with timeouts for stale sessions, and publishes it on the topic `agent.activity` of the scene feed.
- A "Claude presence" overlay module shows a small Claude icon per active session and subagent, animated by state (working, waiting, done), themed from the brand kit.
- Should: other agents (for example Codex or Copilot) use the same event schema with their own icon.
- Could: an optional per-session label, the project name mapped from the allowlist, shown on hover or on the card.

## Capabilities

### New Capabilities
- `agent-activity`: reporting of agent lifecycle events from a PC to the hub, the privacy filter and kill switch, and the per-person presence aggregation with stale-session timeouts.
- `claude-presence-module`: the overlay module that shows agent presence per session and subagent with state animation and brand theming.

### Modified Capabilities
- None.

## Impact

- OBS plugin (`add-obs-plugin`): hosts the local IPC endpoint, the privacy filter, the allowlist and the kill switch; the hook is installed with it.
- Hub: event ingestion endpoint accepting only the OBS plugin's device-bound token; aggregation with an injected clock; publishes `agent.activity` on the scene feed.
- Overlay: one new built-in module that subscribes to `agent.activity` and uses the brand kit theme.
- No change to the desktop app: it does not take part in agent activity.
- The lab prototype's Claude Code hook and privacy filter are the reference for the filter rules only; no code is copied.
