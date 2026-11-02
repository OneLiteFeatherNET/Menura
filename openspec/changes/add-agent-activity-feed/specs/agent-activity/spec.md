## Purpose

Agent activity lets a streamer's AI agent sessions appear on stream as presence (active, waiting, done) without revealing what the agent does. The Claude Code hook reports to the OBS plugin on the same PC over local IPC; the plugin applies the privacy filter and forwards the filtered events to the hub. This capability defines which lifecycle events are reported, what is removed before anything leaves the PC, how the streamer stops reporting, and how the hub turns events into per-person presence.

## ADDED Requirements

### Requirement: Lifecycle event reporting
(Must) The Claude Code hook SHALL report to the OBS plugin on the same PC the session start and stop, the thinking or working state, the tool use category, the waiting-for-input state, subagent spawned and finished, and done.

#### Scenario: Session start and working state are reported
- **GIVEN** an allowlisted project session on a streamer PC with the hook installed
- **WHEN** the session starts and the agent begins working
- **THEN** the OBS plugin receives a session start event and a working event for that session

#### Scenario: Subagent lifecycle is reported
- **GIVEN** an active allowlisted session
- **WHEN** the agent spawns a subagent and the subagent later finishes
- **THEN** the OBS plugin receives a subagent spawned event and a subagent finished event for that session

#### Scenario: Waiting and done are reported
- **GIVEN** an active allowlisted session
- **WHEN** the agent waits for user input and later completes its work
- **THEN** the OBS plugin receives a waiting-for-input event followed by a done event

### Requirement: Local IPC only
(Must) The hook SHALL reach the OBS plugin only over an OS-permissioned local socket or named pipe, and nothing on the PC SHALL listen on a network port for agent events.

#### Scenario: Hook delivers over local IPC
- **GIVEN** the OBS plugin is running on the PC
- **WHEN** the hook reports an event
- **THEN** the event is delivered over the local socket or named pipe of the plugin

#### Scenario: Other local user is refused
- **GIVEN** a process of another operating-system user on the same PC
- **WHEN** it tries to connect to the plugin's local socket or pipe
- **THEN** the operating system refuses the connection

### Requirement: Tool use is reported as a category
(Must) The hook SHALL report a tool use only as a category from a fixed list and SHALL NOT report the tool name or its arguments.

#### Scenario: Shell command reported as category
- **GIVEN** an allowlisted session where the agent runs a shell command
- **WHEN** the hook reports the tool use
- **THEN** the reported event carries the category of the tool and no tool name or arguments

### Requirement: Event schema carries no private fields
(Must) The event schema SHALL contain only lifecycle type, session identifier, agent kind, tool category, subagent count and timestamp, and SHALL NOT contain fields for prompt text, file contents or file paths.

#### Scenario: Schema check
- **GIVEN** the event schema
- **WHEN** an event is validated against it
- **THEN** an event that contains a prompt, file content or path field is rejected

### Requirement: Only allowlisted project roots are reported
(Must) The privacy filter of the OBS plugin SHALL drop every event of a session whose project root is not on the streamer's allowlist before the event is forwarded to the hub.

#### Scenario: Session outside the allowlist
- **GIVEN** a streamer allowlist that contains one project root
- **WHEN** an agent session runs in a directory outside that root
- **THEN** no event of that session is forwarded to the hub

#### Scenario: Session inside the allowlist
- **GIVEN** a streamer allowlist that contains one project root
- **WHEN** an agent session runs inside that root
- **THEN** its lifecycle events are forwarded to the hub

### Requirement: No private content leaves the PC
(Must) The privacy filter SHALL remove prompt text, file contents and any path outside the allowlist from every event before the event is forwarded.

#### Scenario: Prompt text is not sent
- **GIVEN** an allowlisted session in which the person enters a prompt
- **WHEN** the hook reports events for that session
- **THEN** no forwarded event contains any text of the prompt

#### Scenario: Paths outside the allowlist are not sent
- **GIVEN** an allowlisted session that touches a file outside the allowlisted roots
- **WHEN** the hook reports events for that session
- **THEN** no forwarded event contains that file path

### Requirement: Kill switch stops reporting
(Must) The streamer SHALL be able to disable all activity reporting with one action in the OBS plugin, and once disabled the plugin SHALL forward no further agent events until the streamer enables reporting again.

#### Scenario: Kill switch during a session
- **GIVEN** an allowlisted session that is reporting events
- **WHEN** the streamer activates the kill switch
- **THEN** no further event of any session is forwarded to the hub
- **AND** the hub marks the sessions of that PC as stopped once their timeout has passed

#### Scenario: Reporting resumes after the switch is released
- **GIVEN** the kill switch is active
- **WHEN** the streamer releases the kill switch
- **THEN** events of allowlisted sessions are forwarded again

### Requirement: Events are forwarded with the plugin's device token
(Must) The hub SHALL accept agent events only from the OBS plugin with a valid device-bound token of the person, and SHALL refuse other events, including events sent by a hook directly, with a Problem Details error.

#### Scenario: Event from the plugin with a valid token
- **GIVEN** an OBS plugin with a valid device-bound token
- **WHEN** the plugin forwards a filtered event
- **THEN** the hub accepts the event

#### Scenario: Event from a revoked device
- **GIVEN** a device whose token has been revoked in the web app
- **WHEN** the plugin forwards an event
- **THEN** the hub refuses the event with 401 as a Problem Details error

#### Scenario: Event sent by a hook directly
- **GIVEN** a hook that tries to send an event to the hub without the plugin
- **WHEN** the request reaches the hub
- **THEN** the hub refuses the request with 401 as a Problem Details error

### Requirement: Per-person presence aggregation
(Must) The hub SHALL aggregate the events of a person into presence that lists the active sessions, the state of each session and the number of active subagents per session.

#### Scenario: Two sessions and a subagent
- **GIVEN** two active sessions of one person, one of them with one active subagent
- **WHEN** presence is read
- **THEN** presence lists both sessions with their states
- **AND** the presence shows one active subagent for the first session

#### Scenario: Presence is per person
- **GIVEN** sessions of two different persons
- **WHEN** presence is read for one person
- **THEN** only that person's sessions are listed

### Requirement: Stale sessions time out
(Must) The hub SHALL mark a session as stale and remove it from active presence when no event for that session arrives within the configured timeout, using an injected clock.

#### Scenario: Session goes silent
- **GIVEN** an active session whose last event was received at time T
- **WHEN** the configured timeout has passed without a new event and the clock reads T plus the timeout
- **THEN** the session is no longer listed as active

#### Scenario: Session stays active within the timeout
- **GIVEN** an active session whose last event was received at time T
- **WHEN** the clock reads less than the timeout after T
- **THEN** the session stays listed as active

### Requirement: Presence is published on the scene feed
(Must) The hub SHALL publish the aggregated presence on the topic `agent.activity` of every scene feed that uses it, and SHALL send the current presence when a feed connects.

#### Scenario: Presence change reaches the overlay
- **GIVEN** a scene feed that subscribes to `agent.activity`
- **WHEN** a session changes from working to waiting
- **THEN** the feed delivers the updated presence to the subscribed module

#### Scenario: Current presence on connect
- **GIVEN** a running session
- **WHEN** a new overlay connects to a scene feed
- **THEN** the first presence it receives lists that session

### Requirement: Other agents use the same event schema
(Should) The event schema SHALL identify the agent kind per event, so that other agents such as Codex or Copilot report with the same lifecycle events and their own agent kind.

#### Scenario: Second agent kind reported
- **GIVEN** a session of another agent kind on an allowlisted root
- **WHEN** its hook reports a working event
- **THEN** the hub records the event with that agent kind
- **AND** the presence shows the session with the icon of that agent kind

### Requirement: Optional session label
(Could) Where the streamer maps a project root to a name, the hub SHALL be able to include that name as a label of the session in the presence.

#### Scenario: Mapped project label
- **GIVEN** an allowlisted root that the streamer has mapped to the name "Project A"
- **WHEN** a session in that root reports events
- **THEN** the presence shows the session label "Project A"
