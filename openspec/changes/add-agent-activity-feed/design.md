# Design

## Context

Agent sessions run on streamers' PCs. The reference for filter rules is the lab prototype's Claude Code hook and privacy filter (reference only, not copied). Requirements are in `specs/agent-activity` and `specs/claude-presence-module`; motivation is in proposal.md. Principle 5 applies: agent events carry no prompt text, file contents or paths outside an allowlist. Principle 11 applies: nothing on a PC listens on a network port, so hook events travel over OS-permissioned local IPC to the OBS plugin, which is installed on every streaming PC. The plugin applies the privacy filter and forwards with its own device-bound token (`add-access-control`, `add-obs-plugin`). Principle 3 applies: the overlay gets presence through the scene feed of `add-overlay-system`, not through the hook or OBS.

## Goals / Non-Goals

**Goals:**
- A PC-side filter that decides what may leave the machine, with fail-closed behaviour.
- A single event schema shared by Claude and other agent kinds.
- Presence that is correct after stale timeouts and after the streamer's kill switch.

**Non-Goals:**
- No transmission of prompts, transcripts, tool arguments or file contents, in any mode.
- No per-tool detail beyond the category on stream.
- No storage of raw agent events as library content (presence only).
- The desktop app takes no part in agent activity, so it need not be installed on PCs without a GPU for this feature to work.
- No control of agents from the overlay or from the hub (presence is read-only).

## Decisions

**Filtering happens once, in the OBS plugin, before forwarding.** The hook sends only the fixed event schema over local IPC; the plugin applies allowlist, field removal and kill switch and forwards the result; the hub never receives unfiltered data. Alternatives: filtering inside the hook (rejected: the hook is a short-lived process of the coding tool, and a single filter in the always-installed plugin is easier to audit and update); sending everything and filtering at the hub (rejected: private data would leave the PC, violating principle 5); a localhost HTTP endpoint for the hook (rejected: principle 11 forbids listening ports).

**Fail closed.** If the allowlist is missing or unreadable, or the plugin cannot classify a session's project root, the plugin forwards nothing for that session. Alternative: fail open with a default allowlist (rejected: unknown sessions could leak).

**The hook never blocks the agent.** The hook has a short time budget per IPC write and drops the event on error, so Claude Code keeps working when the plugin is not running or the hub is unreachable. Alternative: blocking delivery (rejected: a stream feature must not slow a coding session).

**Presence held in the hub's memory with timeouts.** The hub keeps per-person session state and derives presence from it, using an injected clock for stale detection. After a hub restart presence rebuilds from the next events of each session. Alternative: persisting every event in PostgreSQL (rejected: no requirement for history, and it stores more than needed for on-air presence).

**Stale timeout default of five minutes, configurable.** Assumption, see report; the value only affects how long an idle session stays on screen.

**Hook delivered with the OBS plugin installer.** Assumption, see report: the plugin is the only PC component that every streaming PC has, so the hook is installed with it.

**Event schema with an agent kind field.** One schema for Claude, Codex and Copilot; the kind selects the icon in the presence module. Alternative: a separate endpoint per agent (rejected: duplicate logic for the same lifecycle).

**Kill switch in the OBS plugin, checked before every forward.** Activating it stops forwarding at once and needs no hub round trip. Alternative: a hub-side mute (rejected: events would still leave the PC).

## Risks / Trade-offs

- [Risk] The allowlist is misconfigured and a private project appears on stream. → Mitigation: the filter is fail-closed; the plugin shows the allowlisted roots and the active sessions before going live; the kill switch is one action.
- [Risk] A category or label reveals more than intended. → Mitigation: categories come from a fixed list; labels are only the streamer's own mapping names.
- [Risk] A local process impersonates the hook and injects events. → Mitigation: the IPC endpoint is OS-permissioned to the owning user; the plugin forwards only events that match the schema, and the hub accepts only the plugin's token.
- [Risk] The hook slows or breaks Claude Code. → Mitigation: time-bounded, non-blocking delivery that drops events on error.
- [Risk] A revoked or copied device token sends fake presence. → Mitigation: the plugin's device-bound token is revocable per device and sender-constrained (DPoP); events from a revoked token are refused.
- [Trade-off] A hub restart loses the in-flight state; presence is briefly incomplete until sessions report again.

## Open Questions

None that change specs, approach or tasks. The stale timeout default is recorded as an assumption in the report and can be changed by configuration.
