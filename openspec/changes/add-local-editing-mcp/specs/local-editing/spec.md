# Spec Delta

## Purpose

Defines how Claude edits video on a GPU-equipped streamer PC through the desktop app's local MCP server over stdio: no network listener, GPU requirement, hub calls with the person's device-bound token, library access, source caching, timelines, output profiles and brand, render validation, upload of every result with lineage, shared editing rules, consented toolchain setup and the cut-confirm-render workflow. Rendering always happens on the PC.

## ADDED Requirements

### Requirement: Local MCP over stdio without a network listener
(Must) The desktop app SHALL offer its editing tools through a local MCP server that uses the stdio transport, and SHALL NOT open any network listener for it.

#### Scenario: Claude starts the server
- **GIVEN** Claude Code or Claude Desktop on a GPU PC
- **WHEN** it starts the local MCP server
- **THEN** the tools are available over stdio and no port is opened

#### Scenario: No listener on the network
- **GIVEN** a running desktop app
- **WHEN** the open network ports of the PC are checked
- **THEN** the editing server is not among them

### Requirement: GPU required for editing tools
(Must) WHILE the PC has no usable GPU, the editing tools SHALL refuse to run and SHALL say why.

#### Scenario: Tool call without GPU
- **GIVEN** a PC without a usable GPU
- **WHEN** Claude calls a render tool
- **THEN** the call is refused with the reason that no usable GPU was found

### Requirement: Hub calls with the person's device-bound token
(Must) The system SHALL call the hub with the person's device-bound token from the operating system keychain and SHALL return only what the hub returns for that person.

#### Scenario: Search with person's token
- **GIVEN** a person with the viewer role using the local MCP server
- **WHEN** Claude searches the library for a stream title
- **THEN** the results are those the hub returns for that person

### Requirement: Library access through the hub
(Must) When Claude lists or searches the library, the system SHALL forward the request to the hub and SHALL NOT keep a copy of library listings beyond the session.

#### Scenario: Unreadable item not shown
- **GIVEN** a library item the person may not read
- **WHEN** Claude searches
- **THEN** the item is not part of the results

### Requirement: Fetch only needed source ranges
(Must) When a timeline needs a source range, the system SHALL download only the byte ranges or segments covering that range into a local cache and SHALL NOT download the whole recording for that range.

#### Scenario: Range fetched
- **GIVEN** a timeline that uses 30 seconds of a 4-hour recording
- **WHEN** the source is fetched
- **THEN** only the segments covering those 30 seconds are downloaded

#### Scenario: Cached range reused
- **GIVEN** a range already in the local cache
- **WHEN** a timeline needs it again
- **THEN** no download takes place

### Requirement: Create and update timelines
(Must) The system SHALL let Claude create and update timelines, each an ordered list of source ranges with overlays, captions and a layout template reference.

#### Scenario: Timeline created
- **GIVEN** three source ranges, one caption and a layout template reference
- **WHEN** Claude creates a timeline with them
- **THEN** the timeline is stored with the ranges in the given order

#### Scenario: Timeline updated
- **GIVEN** an existing timeline
- **WHEN** Claude changes the order of two ranges
- **THEN** the timeline keeps all ranges in the new order

### Requirement: Local rendering
(Must) The system SHALL render timelines on the streamer's PC and SHALL NOT send any render job to the hub.

#### Scenario: Render stays local
- **GIVEN** a timeline and a connection to the hub
- **WHEN** Claude renders it
- **THEN** the render runs on the PC and no media is sent to the hub before upload

### Requirement: Output profiles
(Must) The system SHALL render each asset according to an output profile fetched from the hub, including a short 9:16 profile at 1080x1920 and a long 16:9 profile, and loudness targets of -14 LUFS and -1.5 dBTP for short profiles.

#### Scenario: Short render
- **GIVEN** the short 9:16 output profile
- **WHEN** a timeline is rendered with it
- **THEN** the output is 1080x1920 with loudness -14 LUFS and true peak -1.5 dBTP

#### Scenario: Unknown profile
- **GIVEN** a profile name that the hub does not define
- **WHEN** a render is requested with it
- **THEN** the system refuses the render and names the unknown profile

### Requirement: Brand kit and templates pinned, person profile within the brand
(Must) The system SHALL render with the layout templates and brand kit version pinned by the timeline, and SHALL apply the person's profile only to the tokens and assets the brand kit marks as overridable.

#### Scenario: Pinned version used
- **GIVEN** a timeline that pins brand kit version 3
- **WHEN** version 4 is published and the timeline is rendered
- **THEN** the render uses version 3

#### Scenario: Personal accent colour applied
- **GIVEN** a person whose profile overrides the accent colour, which the brand kit marks as overridable
- **WHEN** a timeline is rendered
- **THEN** the render uses the personal accent colour

#### Scenario: Non-overridable token kept
- **GIVEN** a person's profile that tries to override a token the brand kit does not mark as overridable
- **WHEN** a timeline is rendered
- **THEN** the render uses the brand kit value

### Requirement: Render validation before upload
(Must) When a render completes, the system SHALL validate its duration, loudness and resolution against the output profile and SHALL NOT upload a render that fails validation.

#### Scenario: Valid render eligible
- **GIVEN** a render whose duration, loudness and resolution match the profile
- **WHEN** validation completes
- **THEN** the render becomes eligible for upload

#### Scenario: Loudness out of target
- **GIVEN** a render measured at -10 LUFS against a -14 LUFS target
- **WHEN** validation completes
- **THEN** the render is not uploaded and the failed check is reported

### Requirement: Upload every result with lineage
(Must) The system SHALL upload every result of local editing to the library, namely clips, timelines and finished assets, and SHALL record for each the sources it was made from.

#### Scenario: Clip uploaded
- **GIVEN** a clip cut from a recording
- **WHEN** Claude saves the clip
- **THEN** the library stores the clip with lineage to its recording

#### Scenario: Asset uploaded with lineage
- **GIVEN** a validated render made from two clips of one stream
- **WHEN** it is uploaded
- **THEN** the library stores the asset with lineage to both clips, their recording and the timeline

### Requirement: Editing rules as MCP resources
(Must) The system SHALL fetch the editing rules from the hub and expose them to Claude as MCP resources, so that every PC applies the same rules.

#### Scenario: Rules listed
- **GIVEN** the hub serves an editing rules version
- **WHEN** Claude lists the resources of the local MCP server
- **THEN** the editing rules are listed and readable

#### Scenario: Newer rules
- **GIVEN** the hub publishes a newer rules version
- **WHEN** the desktop app next fetches the rules
- **THEN** Claude reads the newer version

### Requirement: Dry run and frame preview
(Should) When a dry run is requested, the system SHALL produce a low-resolution preview and SHALL NOT produce the final render; when a frame preview is requested, the system SHALL return one frame at the requested time.

#### Scenario: Dry run
- **GIVEN** a timeline and a dry-run request
- **WHEN** the dry run completes
- **THEN** a low-resolution preview exists and no final render file is written

#### Scenario: Frame preview
- **GIVEN** a timeline and a time offset
- **WHEN** Claude requests a frame at that offset
- **THEN** the system returns one frame of the timeline at that time

### Requirement: Toolchain check against the hub manifest
(Must) The system SHALL check the local toolchain against the hub's toolchain manifest, covering the required ffmpeg version and features, the speech model and the brand fonts, and SHALL report exactly what is missing and how to fix it.

#### Scenario: All present
- **GIVEN** a toolchain that matches the manifest
- **WHEN** the check runs
- **THEN** it reports success with no missing items

#### Scenario: Missing font
- **GIVEN** a brand font listed in the manifest that is not installed
- **WHEN** the check runs
- **THEN** the report names the font and the step that installs it

#### Scenario: Older ffmpeg
- **GIVEN** an ffmpeg version older than the manifest requires
- **WHEN** the check runs
- **THEN** the report names the required and the found version

### Requirement: Consent before any download or install
(Must) The system SHALL download or install any software or model only after the person has clicked to consent for that item.

#### Scenario: Missing tool offered
- **GIVEN** a missing ffmpeg reported by the check
- **WHEN** the person has not clicked consent
- **THEN** nothing is downloaded or installed

#### Scenario: Consented install
- **GIVEN** a missing item and the person's click on its consent button
- **WHEN** the install runs
- **THEN** only that item is downloaded and installed

### Requirement: Propose a cut before rendering
(Must) When Claude proposes a cut, the system SHALL produce a timeline and a low-resolution preview and SHALL wait for a person to confirm or adjust it before the final render starts.

#### Scenario: Proposal waits
- **GIVEN** a proposed cut with timeline and preview
- **WHEN** no person has confirmed it
- **THEN** no final render starts

#### Scenario: Person adjusts
- **GIVEN** a proposed cut
- **WHEN** the person changes one range and confirms
- **THEN** the final render uses the adjusted timeline

### Requirement: Unattended render and upload after confirmation
(Must) When a person has confirmed a cut, the system SHALL run the final render, validation and upload without further manual steps.

#### Scenario: Confirmed cut completes
- **GIVEN** a confirmed cut
- **WHEN** the final render runs
- **THEN** the render is validated and uploaded without any further prompt

### Requirement: Claim cut-proposal jobs
(Should) The system SHALL claim cut-proposal jobs from the hub queue and SHALL report the proposed timeline and preview back under the job lease.

#### Scenario: Proposal job completed
- **GIVEN** a queued cut-proposal job and a GPU PC
- **WHEN** the desktop app claims and completes it
- **THEN** the proposed timeline and preview are reported to the hub for the person to confirm
