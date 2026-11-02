# brand-mcp-resources

## Purpose

Brand kit, layout templates and editing rules exposed through the hub MCP.

## ADDED Requirements

### Requirement: Brand resources
(Must) The hub SHALL expose the current brand kit, its layout templates and the editing rules as MCP resources, and SHALL allow a client to read a pinned brand kit version.

#### Scenario: Read the brand kit
- **GIVEN** a client with the viewer role or higher
- **WHEN** the client reads the brand kit resource
- **THEN** the hub returns the current brand kit version

#### Scenario: Read a pinned version
- **GIVEN** a brand kit version the client has pinned
- **WHEN** the client reads the brand kit resource for that version
- **THEN** the hub returns that version, not the newest one
