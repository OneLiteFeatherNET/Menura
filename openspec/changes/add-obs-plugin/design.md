# Design

## Context

OBS Studio loads native plugins through libobs. Streaming PCs run Windows, macOS or Linux and may have no GPU; the plugin must work there. Requirements are in `specs/obs-plugin`; motivation is in proposal.md. The plugin is the team's only component that every streaming PC has, so it also hosts the hook relay of `add-agent-activity-feed`. Principles that shape the design: principle 3 (the plugin holds its own SSE stream), principle 6 (own sign-in with PKCE and loopback, tokens in the OS keychain, DPoP-bound), principle 11 (no network listener on a PC; OS-permissioned local IPC only; secrets never logged) and principle 12 (one signed installer per OS with signed auto-update, one-click consent for downloads, never a silent install).

## Goals / Non-Goals

**Goals:**
- A streamer installs one thing on each streaming PC, and the scenes, look and activity relay work after one sign-in.
- Central changes reach OBS live; the streamer never copies a URL or a token.
- OBS stays usable, including streaming, when the hub is unreachable.

**Non-Goals:**
- Recording upload is not described here; it is specified in `add-recording-ingest`, which the plugin implements.
- No scene editing in OBS: the plugin never edits templates, only applies them. Layouts are changed on the hub.
- No desktop-app features in the plugin: no transcription, cutting or rendering.
- No control of OBS scene switching or streaming state by the hub.

## Decisions

**Native plugin, not a browser-only control panel.** A plugin can create scenes and browser sources in OBS, which is what the central sync needs; a browser panel cannot. Alternative: streamers copy the scene collection from the hub (rejected: copies go stale and the token would be copied with them, which breaks principle 11).

**Own sign-in, not the desktop app's.** The plugin signs in with PKCE over a loopback redirect like any native client, so it works on PCs without the desktop app. Alternative: the desktop app signs in and hands tokens to the plugin (rejected: the desktop app is installed only on GPU PCs, and tokens would have to cross a process boundary).

**Tokens in the OS keychain with DPoP.** The keychain holds refresh and access tokens; the DPoP key is device-bound so a copied token is useless on another PC. Alternative: encrypted files (rejected: principle 6 requires the keychain).

**Scene tokens written into the browser source, never shown.** The plugin obtains a new variant token from the hub and writes the URL into the browser source; the token is never rendered in any dialog, and logs redact it. Alternative: showing the URL for manual copy (rejected: exposes the token and invites reuse).

**Hook relay over OS-permissioned local IPC.** The hook writes to a named pipe (Windows) or a Unix socket with owner-only permissions (macOS and Linux). Alternative: a localhost HTTP port (rejected: principle 11 forbids listening ports).

**Privacy filter defined in `agent-activity`, implemented here.** One set of rules and one set of conformance fixtures; the plugin runs them before any forward. Alternative: a second filter in the hook (rejected: two filters can disagree).

**Offline state as the last sync.** Scenes, theme and profile are stored in the plugin's data directory with the last sync time; OBS's own scene collection is written by the plugin, so OBS starts with it. Alternative: failing the start without the hub (rejected: stream reliability must not depend on the network).

**Installer and updater per OS with signed packages.** Windows and macOS use their platform signing; Linux uses a signed package repository. Alternative: a single archive without signature (rejected: principle 12).

**One profile per person.** The plugin applies the signed-in person's single profile, which `add-brand-kit` gives every
person exactly once, so there is no profile selection in the plugin. Rejected: letting the person choose among several
profiles on each PC (the hub defines one profile per person, and a selector would create a PC-local state that the hub
does not know about).

## Risks / Trade-offs

- [Risk] A libobs API change breaks the plugin on one OBS version. → Mitigation: the plugin declares supported OBS versions and the test matrix covers each platform's current release.
- [Risk] A malicious update replaces the plugin. → Mitigation: signed packages and signed updates; invalid signatures are refused.
- [Risk] Sync overwrites a streamer's manual changes in OBS. → Mitigation: the sync updates only sources it created; a manual source is left in place.
- [Risk] The hook relay accepts events from a local process other than the hook. → Mitigation: owner-only IPC permissions and schema validation; only filtered events are forwarded.
- [Trade-off] Each OS needs its own installer pipeline. Accepted by principle 12.

## Open Questions

None.
