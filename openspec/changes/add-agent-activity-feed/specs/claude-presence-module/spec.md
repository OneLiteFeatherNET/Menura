## Purpose

The Claude presence module is the overlay module that shows, on stream, one small icon per active agent session and per active subagent, animated by the session state. This capability defines what viewers see and how the module follows the brand theme.

## ADDED Requirements

### Requirement: Icon per session and subagent
(Must) The Claude presence module SHALL show one small Claude icon for each active session and one for each active subagent in the presence it receives.

#### Scenario: Session with a subagent
- **GIVEN** a presence with one active session that has one active subagent
- **WHEN** the module renders the presence
- **THEN** two icons are shown, one for the session and one for the subagent

#### Scenario: No active session
- **GIVEN** a presence with no active sessions
- **WHEN** the module renders the presence
- **THEN** no icon is shown

### Requirement: State animation
(Must) The module SHALL animate each icon according to its session state: working, waiting or done.

#### Scenario: Working state
- **GIVEN** a session in the working state
- **WHEN** the module renders the session icon
- **THEN** the icon shows the working animation

#### Scenario: Waiting state
- **GIVEN** a session that waits for user input
- **WHEN** the module renders the session icon
- **THEN** the icon shows the waiting animation

#### Scenario: Done state
- **GIVEN** a session that reports done
- **WHEN** the module renders the session icon
- **THEN** the icon shows the done appearance until the session stops or times out

### Requirement: Brand theming
(Must) The module SHALL take its colours and sizes from the overlay theme of the brand kit and SHALL NOT use fixed colours of its own.

#### Scenario: Brand colour change
- **GIVEN** a live scene that shows the module
- **WHEN** a new brand kit version changes the accent colour of the overlay theme
- **THEN** the icons show the new accent colour

### Requirement: Subscribed topic
(Must) The module SHALL declare `agent.activity` as its only subscribed topic in its manifest.

#### Scenario: Manifest topic list
- **GIVEN** the Claude presence module's manifest
- **WHEN** the manifest is validated
- **THEN** the subscribed topics are exactly `agent.activity`

### Requirement: Agent kind icon
(Should) The module SHALL show the icon that belongs to the agent kind of each session, so that sessions of other agent kinds are distinguishable from Claude sessions.

#### Scenario: Session of another agent kind
- **GIVEN** a presence with one Claude session and one session of another agent kind
- **WHEN** the module renders the presence
- **THEN** the two sessions show different icons

### Requirement: Hover label
(Could) Where a session has a label, the module SHALL be able to show the label when the viewer hovers over the session icon.

#### Scenario: Hover shows label
- **GIVEN** a session icon whose session has the label "Project A"
- **WHEN** a viewer hovers over the icon
- **THEN** the label "Project A" is shown
