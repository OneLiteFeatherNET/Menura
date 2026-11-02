## Purpose

Live control covers how a running overlay receives its data, recovers after a dropped connection, reflects operator decisions and brand changes within seconds, and keeps private data off the air. It defines the behaviour of the scene feed and the operator panel, not their internals.

## ADDED Requirements

### Requirement: Scene feed delivers current state
(Must) The hub SHALL give each overlay browser source its own Server-Sent-Events feed for its scene, and the feed SHALL send the current value of every topic the scene uses as soon as the connection opens.

#### Scenario: Overlay connects to a running scene
- **GIVEN** a scene whose modules show a follower goal at 40 of 100
- **WHEN** a browser source opens the feed of that scene
- **THEN** the first events on the feed carry the current goal value of 40 of 100

#### Scenario: Feeds are independent per client
- **GIVEN** two browser sources that show the same scene
- **WHEN** one of them disconnects
- **THEN** the other continues to receive feed events without interruption

### Requirement: Named data topics
(Must) The scene feed SHALL carry the named topics agent activity (`agent.activity`), stream metadata, follower and chat events, library and distribution data such as the next scheduled post, and custom values.

#### Scenario: Next scheduled post is shown
- **GIVEN** a promo module that subscribes to the next-scheduled-post topic
- **WHEN** the distribution calendar schedules a new next post
- **THEN** the feed delivers the new next post to the promo module

#### Scenario: Custom value is updated by an operator
- **GIVEN** a custom topic value set by an operator
- **WHEN** the operator changes the value
- **THEN** every overlay showing a module subscribed to that topic displays the new value

### Requirement: Resynchronisation after reconnect
(Must) When an overlay reconnects after a dropped feed, the overlay SHALL read the current state of its scene and replace any state it holds that is out of date.

#### Scenario: Missed events during a network drop
- **GIVEN** an overlay whose feed dropped while the follower goal changed from 40 to 55
- **WHEN** the overlay reconnects
- **THEN** the overlay shows the follower goal at 55
- **AND** it does not show the stale value of 40 at any point after reconnecting

### Requirement: Operator panel controls live scenes
(Must) The web operator panel SHALL let a person with the operator or admin role switch modules on and off, show promo cards and hide private areas on a live scene that the person owns.

#### Scenario: Operator switches a module off
- **GIVEN** a live scene with a visible chat module
- **WHEN** an operator switches the chat module off in the operator panel
- **THEN** the chat module disappears from the live scene

#### Scenario: Operator shows a promo card
- **GIVEN** a scene with a promo card module that is hidden
- **WHEN** an operator shows the promo card
- **THEN** the promo card appears on the live scene

#### Scenario: Operator acts only on own variant
- **GIVEN** a live personal variant owned by person A and an operator who is person B
- **WHEN** the operator tries to switch a module on that variant
- **THEN** the hub answers 404 as a Problem Details error and the variant stays unchanged

#### Scenario: Viewer cannot operate a scene
- **GIVEN** a person with the viewer role
- **WHEN** the person tries to switch a module on a live scene
- **THEN** the hub refuses the action with 403 as a Problem Details error

### Requirement: Live changes reach OBS within one second
(Must) When an operator changes a live scene, the change SHALL be visible in the OBS browser source within 1 second.

#### Scenario: Module switched off on air
- **GIVEN** an OBS browser source connected to a live scene and the operator panel
- **WHEN** an operator switches a module off
- **THEN** the module is no longer shown in the browser source within 1 second of the operator action

### Requirement: Privacy curtain
(Should) The operator panel SHALL offer a privacy curtain action that blanks the configured regions of a scene immediately, and the regions SHALL stay blank until the operator removes the curtain.

#### Scenario: Curtain blanks regions
- **GIVEN** a live scene with a configured region showing the chat
- **WHEN** an operator triggers the privacy curtain
- **THEN** the region is blank in the browser source within 1 second
- **AND** the region stays blank until the operator removes the curtain

#### Scenario: Curtain is removed
- **GIVEN** a privacy curtain that is active
- **WHEN** an operator removes the curtain
- **THEN** the configured regions show their modules again

### Requirement: Overlay theme from the brand kit and profile
(Must) The overlay SHALL render every variant with the overlay theme template of the brand kit, with only the overrides allowed by the owner's brand profile, and a central brand kit change SHALL update every live variant that does not override that exact token, without a manual reload.

#### Scenario: Central accent colour change reaches live variant
- **GIVEN** a live variant that uses the overlay theme without overriding the accent colour
- **WHEN** a new brand kit version with a different accent colour is published
- **THEN** the live variant shows the new accent colour without a manual reload

#### Scenario: Personal override survives a central change
- **GIVEN** a live variant whose owner overrode the accent colour in the brand profile
- **WHEN** a new brand kit version changes the accent colour
- **THEN** the live variant keeps the owner's accent colour

### Requirement: No unapproved private data on screen
(Must) The overlay SHALL NOT render any data that is not approved for display on air, including data of private areas that an operator has hidden.

#### Scenario: Hidden area receives data
- **GIVEN** a private area that an operator has hidden
- **WHEN** new data for that area arrives on the feed
- **THEN** the overlay does not render the data

### Requirement: Operator actions are audit-logged
(Must) The hub SHALL audit-log every operator action on a live scene with the person, the action, the scene and the time, marked as performed via the web app.

#### Scenario: Operator action is logged
- **GIVEN** an operator who shows a promo card
- **WHEN** the action completes
- **THEN** the audit log records the operator, the "show" action, the scene, the time and the web app as the channel

### Requirement: Feed heartbeat below the proxy idle timeout
(Must) While a scene feed is open, the hub SHALL send a heartbeat event at least every 30 seconds, so that the CDN proxy in front of the hub never closes the stream for being idle.

#### Scenario: Quiet scene stays connected
- **GIVEN** an open scene feed and no data change for 5 minutes
- **WHEN** the feed is observed through the proxy
- **THEN** a heartbeat arrives at least every 30 seconds and the connection stays open
