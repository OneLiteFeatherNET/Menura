# overlay-mcp-tools

## Purpose

Overlay scene state and module actions exposed through the hub MCP.

## ADDED Requirements

### Requirement: Overlay tools
(Must) The hub SHALL offer MCP tools to read the state of a scene and to trigger the module actions that the caller's role allows for operators, on the caller's own variant only.

#### Scenario: Operator triggers a module action
- **GIVEN** a token of a person with the operator role and an own variant with a promo card module
- **WHEN** the client triggers the "show" action of the promo card
- **THEN** the promo card is shown on the live variant
- **AND** the action is audit-logged via MCP

#### Scenario: Editor cannot trigger live actions
- **GIVEN** a token of a person with the editor role only
- **WHEN** the client triggers a module action on a live variant
- **THEN** the hub refuses the call
