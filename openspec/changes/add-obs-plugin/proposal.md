# Proposal

Conventional Commits: `feat(obs)` (code PR). Phase: 6 (Overlay). Depends on: `add-access-control` (sign-in, device-bound tokens, refresh rotation, revocation), `add-client-and-mcp-auth` (device sign-in and token keeping for the plugin), `add-recording-ingest` (recording upload from the plugin), `add-overlay-system` (variants, browser-source URLs, token rotation, live feed), `add-brand-kit` (overlay theme and `brand-profiles`).

Strand: overlay.

## Why

Every streaming PC needs the same central overlay scenes, brand and personal look inside OBS, without anyone copying URLs or tokens by hand, and it needs one trusted component that also relays Claude Code activity from the PC. The OBS plugin is that component: it signs its user in, keeps OBS in sync with the hub, and works when the hub is unreachable.

## What Changes

- The plugin is a full client on Windows, macOS and Linux, installed on every streaming PC. It signs in itself with authorization code and PKCE over a loopback redirect (RFC 8252, RFC 7636), keeps its tokens in the OS keychain and binds them to the device (DPoP, RFC 9449).
- Scene sync: the plugin creates and updates the scenes and browser sources of the person's variant in OBS from the hub. It manages the variant's token itself: it rotates it and writes it into the browser source without ever showing it.
- Personal profile: the plugin applies the signed-in person's one profile (exactly one per person, see `add-brand-kit`);
  the sync follows changes to that profile and to the central template or brand kit. There is no profile choice on the PC.
- Live updates: the plugin holds its own Server-Sent-Events stream, so a central template, theme or profile change reaches OBS without a restart.
- Hook relay: a local, OS-permissioned IPC endpoint receives Claude Code hook events. The plugin applies the privacy filter defined in `agent-activity` (allowlist, field removal, fixed categories, fail-closed) and a kill switch, then forwards the filtered events to the hub with its own token. Nothing on the PC listens on a network port.
- Offline: the plugin keeps the last synced scenes, theme and profile, so OBS keeps its scene collection and streaming works without the hub.
- Setup: a signed installer and signed auto-update per operating system, and a first-start wizard that signs in and shows the state of the sync.
- Recording upload is done by the plugin but specified in `add-recording-ingest`; this change does not duplicate it.

## Capabilities

### New Capabilities
- `obs-plugin`: sign-in of the plugin, scene and browser-source sync in OBS, token handling, personal profile application, live updates over its own feed, the hook relay with the privacy filter and kill switch, offline operation, and installation and update per operating system.

### Modified Capabilities
- None.

## Impact

- OBS Studio plugin (C++, libobs) for Windows, macOS and Linux, with its own installer and update channel.
- Hub: device registration for the plugin, its own feed subscription, and the ingestion endpoint for forwarded agent events (`add-agent-activity-feed`).
- Overlay: no change to the browser-source application; the plugin only consumes its URLs.
- Desktop app: not involved; the plugin works on PCs without a GPU.
