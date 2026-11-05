## Purpose

The OBS plugin is the streamer's trusted client inside OBS. It signs its user in, keeps the person's overlay scenes and browser sources in OBS in sync with the hub, manages the scene tokens itself, relays Claude Code activity from the PC through the privacy filter, and keeps working from its last synced state when the hub is unreachable.

## ADDED Requirements

### Requirement: Own sign-in with device binding
(Must) The OBS plugin SHALL sign its user in itself through the authorization code flow with PKCE over a loopback redirect, SHALL keep its tokens in the operating-system keychain, and SHALL bind its tokens to the device with DPoP.

#### Scenario: First sign-in
- **GIVEN** a streamer PC with the plugin installed and no stored tokens
- **WHEN** the streamer starts the sign-in from the plugin
- **THEN** the browser opens the authorization page with a PKCE challenge and a loopback redirect
- **AND** after the redirect the plugin stores its tokens in the OS keychain bound to a DPoP key of the device

#### Scenario: Tokens are not stored in plain files
- **GIVEN** a signed-in plugin
- **WHEN** the plugin's configuration files are inspected
- **THEN** they contain no token value

#### Scenario: Sign-in after revocation
- **GIVEN** a device whose plugin token was revoked in the web app
- **WHEN** the plugin next contacts the hub
- **THEN** the hub refuses the token with 401 as a Problem Details error
- **AND** the plugin asks the streamer to sign in again

### Requirement: Scene and browser-source sync in OBS
(Must) The OBS plugin SHALL create and update, in OBS, the scenes and browser sources of the streamer's personal variant for the person's one profile, so that each scene the hub defines exists in OBS with its layout.

#### Scenario: First sync creates scenes
- **GIVEN** a signed-in plugin and a personal variant with two scenes that OBS does not yet have
- **WHEN** the sync runs
- **THEN** OBS contains the two scenes with their browser sources and layout

#### Scenario: Central change updates the browser source
- **GIVEN** a browser source in OBS for a variant
- **WHEN** the central template of that variant moves a module
- **THEN** the plugin updates the layout of the scene in OBS

#### Scenario: Scene removed centrally
- **GIVEN** a scene that the hub no longer defines for the variant
- **WHEN** the sync runs
- **THEN** the plugin removes that scene from OBS only if the streamer has not added sources to it

### Requirement: Scene tokens managed by the plugin
(Must) The OBS plugin SHALL rotate the token of its variant's browser sources itself and SHALL NOT display, copy or export any token value to the streamer or to any file the streamer can open.

#### Scenario: Token rotation updates the browser source
- **GIVEN** a browser source with a variant token
- **WHEN** the plugin rotates the token
- **THEN** the browser source uses the new token without a manual step
- **AND** the old token no longer grants access

#### Scenario: Token is not shown
- **GIVEN** the plugin's dock and its properties dialogs
- **WHEN** the streamer inspects any of them
- **THEN** no token value is shown

### Requirement: Personal profile applied
(Must) The OBS plugin SHALL sync the scenes to the personal variant resolved from the signed-in person's profile, and SHALL re-sync when the person changes their profile or the central template or brand kit changes.

#### Scenario: Profile change re-syncs
- **GIVEN** a signed-in plugin whose person changes the accent colour in their profile
- **WHEN** the change is saved on the hub
- **THEN** the plugin re-syncs the scenes to the variant resolved from the changed profile

#### Scenario: Central change of a token not overridden reaches the plugin
- **GIVEN** a signed-in plugin whose person does not override the headline colour
- **WHEN** the central brand kit publishes a new headline colour
- **THEN** the plugin re-syncs and OBS shows the new headline colour without a restart

### Requirement: Live updates over the plugin's own feed
(Must) The OBS plugin SHALL hold its own Server-Sent-Events stream to the hub and SHALL apply template, theme, profile and token changes to OBS while OBS is running, and SHALL resynchronise from the hub's current state after a reconnect.

#### Scenario: Theme change while streaming
- **GIVEN** a running stream with a variant on screen
- **WHEN** a central brand kit change is published
- **THEN** the plugin applies the new theme to the variant without restarting OBS or the stream

#### Scenario: Resynchronisation after reconnect
- **GIVEN** a plugin whose feed dropped while a template changed
- **WHEN** the plugin reconnects
- **THEN** the plugin reads the current state and applies the change it missed

### Requirement: Hook relay with privacy filter
(Must) The OBS plugin SHALL receive Claude Code hook events over a local IPC endpoint that only the streamer's user can open, SHALL apply the privacy filter of `agent-activity` before forwarding any event, and SHALL forward only the filtered events to the hub with its own device-bound token.

#### Scenario: Allowlisted event is forwarded
- **GIVEN** an allowlisted session and a valid plugin token
- **WHEN** the hook sends a working event over the local IPC endpoint
- **THEN** the plugin forwards the filtered event to the hub

#### Scenario: Event outside the allowlist is dropped
- **GIVEN** a session in a directory outside the allowlist
- **WHEN** the hook sends an event for that session
- **THEN** the plugin drops the event and forwards nothing

#### Scenario: Missing allowlist
- **GIVEN** an allowlist that is missing or unreadable
- **WHEN** any event arrives
- **THEN** the plugin forwards nothing

### Requirement: Kill switch for activity reporting
(Must) The OBS plugin SHALL provide a kill switch in its dock that stops all forwarding of agent events at once and SHALL show that the switch is active.

#### Scenario: Kill switch active
- **GIVEN** the kill switch is activated
- **WHEN** a hook event arrives
- **THEN** the plugin drops the event and forwards nothing
- **AND** the dock shows that reporting is off

### Requirement: No network listener on the PC
(Must) The OBS plugin SHALL NOT open any listening network port; its local IPC endpoint SHALL be reachable only through the operating system's permissions for the streamer's user.

#### Scenario: Port scan of the PC
- **GIVEN** a running plugin
- **WHEN** the PC's listening sockets are inspected
- **THEN** the plugin has no listening network socket

### Requirement: Offline operation with last synced state
(Must) The OBS plugin SHALL keep the last synced scenes, theme and profile locally and SHALL keep OBS usable, including streaming and recording, when the hub is unreachable.

#### Scenario: Hub unreachable at stream start
- **GIVEN** a plugin with a previous sync and no network connection
- **WHEN** the streamer starts OBS and the stream
- **THEN** the scene collection with the last synced scenes is available in OBS
- **AND** the plugin shows that it works from the last synced state

#### Scenario: Sync resumes when online
- **GIVEN** a plugin that worked offline
- **WHEN** the network returns
- **THEN** the plugin syncs the scenes and tokens again

### Requirement: Signed installers and signed updates
(Must) The plugin SHALL be delivered as one signed installer per operating system (Windows, macOS and Linux) with signed auto-update, and the first start SHALL sign the streamer in and show the state of the sync and the hook.

#### Scenario: Installation on Linux
- **GIVEN** the signed installer for Linux
- **WHEN** the installer runs
- **THEN** the signature is verified before installation and the plugin is available in OBS

#### Scenario: Update with an invalid signature
- **GIVEN** an update package whose signature does not verify
- **WHEN** the plugin checks for updates
- **THEN** the plugin refuses the update and keeps the installed version

#### Scenario: First start wizard
- **GIVEN** a freshly installed plugin
- **WHEN** the streamer starts it
- **THEN** the wizard asks the streamer to sign in and then shows the state of the scene sync and the hook

### Requirement: Secrets never shown or logged
(Must) The OBS plugin SHALL keep tokens and scene tokens out of its logs, crash reports and any screen.

#### Scenario: Error with a token in the request
- **GIVEN** a failed sync request that carries a scene token
- **WHEN** the failure is logged
- **THEN** the log contains no token value
