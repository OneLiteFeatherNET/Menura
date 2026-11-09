# Spec Delta

## Purpose

Keeps one central, versioned brand identity for OneLiteFeather: colours, typography, marks, and voice and tone texts. Every consumer reads the same kit, can pin a version, and is told when a new version is published. The first version is seeded from the stream overlay theme, so the shorts and the stream share one look.

## ADDED Requirements

### Requirement: Versioned central brand kit

(Must) The system SHALL keep exactly one central brand kit whose every saved change creates a new immutable version, and SHALL keep all earlier versions readable.

#### Scenario: Change creates a new version
- **GIVEN** brand kit version 1 is published
- **WHEN** an editor changes the primary colour and publishes
- **THEN** version 2 exists with the new colour and version 1 is still readable unchanged

#### Scenario: Earlier version remains readable
- **GIVEN** versions 1 to 3 exist
- **WHEN** a consumer requests version 1
- **THEN** the content of version 1 is returned

### Requirement: Named colour tokens with light and dark roles

(Must) The system SHALL define the colour palette as named tokens, each with a value for the light role and a value for the dark role.

#### Scenario: Token resolved per role
- **GIVEN** a token "surface" with a light value and a dark value
- **WHEN** a consumer asks for the light role and the dark role
- **THEN** each request returns the value for its role

#### Scenario: Token missing a role
- **GIVEN** a token with a value for the light role only
- **WHEN** the editor publishes the version
- **THEN** the system rejects the publication and names the token and the missing role

### Requirement: Tonal palette derived from seed colours

(Must) The system SHALL derive tonal palettes from seed colours, so that the same seed colours always produce the same tones.

#### Scenario: Deterministic derivation
- **GIVEN** a seed colour is stored in the kit
- **WHEN** the tonal palette is derived twice from that seed
- **THEN** both derivations produce identical tones

#### Scenario: Seed change updates derived tones
- **GIVEN** a derived tonal palette and a new seed colour
- **WHEN** the editor publishes a version with the new seed
- **THEN** the derived tones in the new version come from the new seed

### Requirement: Contrast of text roles

(Must) The system SHALL check every text role against the background role it is shown on and SHALL refuse to publish a version in which a text role does not reach WCAG AA contrast.

#### Scenario: Text role passes
- **GIVEN** a text role whose contrast against its background meets WCAG AA
- **WHEN** the version is published
- **THEN** the publication succeeds

#### Scenario: Text role fails
- **GIVEN** a text role whose contrast against its background is below WCAG AA
- **WHEN** the editor publishes the version
- **THEN** the publication is refused and the report names the role pair and its contrast ratio

### Requirement: Typography roles

(Must) The system SHALL store font files and map each typography role (for example headline, body and caption) to one font file and size set.

#### Scenario: Role resolved to font
- **GIVEN** the role "headline" is mapped to a font file
- **WHEN** a consumer asks for the typography of the kit
- **THEN** the role is returned with its font file reference

#### Scenario: Role without font
- **GIVEN** a role without a font file
- **WHEN** the editor publishes the version
- **THEN** the publication is refused and the role is named

### Requirement: Logos and marks with variants and safe area

(Must) The system SHALL store logos and marks in named variants and SHALL declare for each variant a safe area in which no other element may be placed.

#### Scenario: Variant with safe area
- **GIVEN** a mark with a light variant and a dark variant, each with a safe area
- **WHEN** a consumer asks for the mark
- **THEN** both variants and their safe areas are returned

### Requirement: Voice, tone and per-language texts

(Must) The system SHALL store voice and tone rules as text and SHALL store hashtags and end-card texts for each supported language.

#### Scenario: Language entry present
- **GIVEN** end-card text and hashtags are stored for German and English
- **WHEN** a consumer asks for the English end-card text
- **THEN** the English text and hashtags are returned

#### Scenario: Language entry missing
- **GIVEN** no end-card text is stored for French
- **WHEN** a consumer asks for the French end-card text
- **THEN** the system reports that no French text exists and does not substitute another language

### Requirement: Read access for all consumers

(Must) The system SHALL make the brand kit and its templates readable over REST for every signed-in role, and SHALL make them readable over MCP only where the hub MCP server is available. The overlay, the desktop app and the OBS plugin SHALL read the kit and its templates over REST and SHALL NOT depend on an MCP read.

#### Scenario: Desktop reads for rendering
- **GIVEN** a signed-in desktop app
- **WHEN** it requests the current brand kit and templates for rendering
- **THEN** the kit and templates are returned

#### Scenario: OBS plugin reads for scene sync
- **GIVEN** a signed-in OBS plugin
- **WHEN** it requests the current brand kit and overlay theme template
- **THEN** the kit and theme are returned

#### Scenario: MCP read where the hub MCP server is available
- **GIVEN** the hub MCP server is available and a client with the viewer role or higher
- **WHEN** the client reads the current brand kit through MCP
- **THEN** the current kit version is returned

#### Scenario: Unauthenticated request
- **GIVEN** a request without a valid token
- **WHEN** it requests the brand kit
- **THEN** the system responds with an authentication error in Problem Details format

### Requirement: Version pinning

(Must) The system SHALL let a consumer pin a specific brand kit version and SHALL keep serving the pinned version until the consumer changes its pin.

#### Scenario: Pinned consumer unaffected
- **GIVEN** the overlay is pinned to version 2
- **WHEN** version 3 is published
- **THEN** the overlay still receives version 2 until its pin is changed

#### Scenario: Unpinned consumer follows latest
- **GIVEN** a consumer without a pin
- **WHEN** version 3 is published
- **THEN** the consumer receives version 3

### Requirement: Change notification

(Must) The system SHALL notify every subscribed client over its Server-Sent Events stream when a new brand kit version is published.

#### Scenario: Subscriber notified
- **GIVEN** an overlay client with an open event stream
- **WHEN** a new version is published
- **THEN** the client receives a notification naming the new version

#### Scenario: Reconnect resynchronises
- **GIVEN** an overlay client that missed a notification while disconnected
- **WHEN** it reconnects and reads the current version
- **THEN** it obtains the latest version without needing the missed notification

### Requirement: Design token export

(Must) The system SHALL export a named brand kit version as a machine-readable JSON render bundle for external renderers, containing the colour tokens with their resolved light and dark values, the typography roles with their font file references, the marks with their safe areas per variant, the per-language texts and hashtags, and the layout templates; and SHALL export the colour tokens as CSS custom properties.

#### Scenario: Export as CSS variables
- **GIVEN** a published brand kit with colour tokens
- **WHEN** a consumer requests the CSS export
- **THEN** each colour token is returned as a named CSS custom property with its light and dark values

#### Scenario: Render bundle of a named version
- **GIVEN** brand kit version 2 with colour tokens, typography roles, marks and layout templates
- **WHEN** an external renderer requests the JSON render bundle of version 2
- **THEN** the bundle contains every colour token with its resolved values, every typography role with its font file reference, every mark with its safe area, the per-language texts and the layout templates

#### Scenario: Pinned renderer receives its version
- **GIVEN** an external renderer pinned to version 2 and version 3 has been published
- **WHEN** the renderer requests the render bundle for its pin
- **THEN** the bundle is exactly version 2, not version 3

### Requirement: Initial kit from the overlay theme

(Must) The system SHALL seed the first published brand kit version from the colour roles of the stream overlay theme, SHALL NOT take any colour value from another source, such as a colour hard-coded in a renderer, and SHALL leave the marks of the first version empty; the feather mark is added by publishing a later version once the logo is a verified library asset.

#### Scenario: Seeded from the overlay theme
- **GIVEN** the stream overlay theme defines its colour roles
- **WHEN** the first brand kit version is published
- **THEN** the colour tokens carry the overlay theme's role values and the version has no mark

#### Scenario: Feather mark added in a later version
- **GIVEN** the feather logo was uploaded as a verified library asset
- **WHEN** an editor publishes a new brand kit version with the feather mark
- **THEN** that version carries the feather mark and the first version stays unchanged

#### Scenario: No colour outside the kit
- **GIVEN** the first kit version was seeded from the overlay theme
- **WHEN** the render bundle and the CSS export of that version are requested
- **THEN** every colour value in them is a value of a kit token and no colour value hard-coded in the external renderer appears
