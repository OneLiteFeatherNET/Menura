# editing-playbook

## Purpose

The editing playbook the hub MCP serves so any team member can let Claude edit locally.

## ADDED Requirements

### Requirement: Editing playbook per operating system
(Must) The hub SHALL serve an editing playbook through MCP that contains the editing rules, the toolchain manifest with the required local tools and models, their versions and setup instructions for Windows, macOS and Linux, and workflow prompts for finding candidates, proposing a cut, rendering locally, uploading and drafting a post.

#### Scenario: Playbook request for shorts
- **GIVEN** a person who tells Claude to cut shorts from yesterday's stream
- **WHEN** the client reads the playbook
- **THEN** the client receives the editing rules, the toolchain manifest and the workflow prompt that runs the steps in order

#### Scenario: Toolchain manifest per operating system
- **GIVEN** a client on Windows, a client on macOS and a client on Linux
- **WHEN** each reads the toolchain manifest
- **THEN** each receives the setup instructions for its own operating system with the same required versions

#### Scenario: Setup requires consent
- **GIVEN** a toolchain entry that needs a download
- **WHEN** the client reads the setup instructions
- **THEN** the instructions state that the download happens only after the person's one-click consent in the desktop app and never silently
