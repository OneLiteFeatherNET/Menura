# Spec Delta

## Purpose

Keeps one central, versioned brand identity for OneLiteFeather: colours, typography, marks, and voice and tone texts. Every consumer reads the same kit, can pin a version, and is told when a new version is published.

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

(Must) The system SHALL make the brand kit readable over REST and MCP and SHALL let the overlay, the desktop app and the OBS plugin read the kit and its templates.

#### Scenario: Desktop reads for rendering
- **GIVEN** a signed-in desktop app
- **WHEN** it requests the current brand kit and templates for rendering
- **THEN** the kit and templates are returned

#### Scenario: OBS plugin reads for scene sync
- **GIVEN** a signed-in OBS plugin
- **WHEN** it requests the current brand kit and overlay theme template
- **THEN** the kit and theme are returned

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

### Requirement: Web palette editor with live preview

(Should) The system SHALL offer a web editor for the colour palette that shows the effect of unpublished palette changes on each layout template before publication.

#### Scenario: Preview before publish
- **GIVEN** an editor changes the primary colour without publishing
- **WHEN** the editor opens a layout template preview
- **THEN** the preview shows the template in the new colour and the published version is unchanged

### Requirement: Design token export

(Should) The system SHALL export the current brand kit as design tokens in JSON and as CSS custom properties.

#### Scenario: Export as CSS variables
- **GIVEN** a published brand kit with colour tokens
- **WHEN** a consumer requests the CSS export
- **THEN** each colour token is returned as a named CSS custom property with its light and dark values

### Requirement: Sub-brands inherit the base kit

(Could) The system SHALL allow a sub-brand to be defined that inherits every value of the OLF base kit and overrides only the values it sets.

#### Scenario: Inherited value
- **GIVEN** a sub-brand that overrides the primary colour only
- **WHEN** a consumer requests its typography
- **THEN** the base kit typography is returned

#### Scenario: Overridden value
- **GIVEN** a sub-brand that overrides the primary colour
- **WHEN** a consumer requests its primary colour
- **THEN** the sub-brand colour is returned
