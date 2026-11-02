# Spec Delta

## Purpose

Defines the layout templates that turn brand and content into finished output: short and long videos, thumbnails, social post images, end cards and the overlay theme. Templates stay consistent with the brand kit because they can only refer to its tokens and must declare what a person fills in.

## ADDED Requirements

### Requirement: Templates for each output format

(Must) The system SHALL provide layout templates for the formats short 9:16, long 16:9 intro, long 16:9 outro, thumbnail, social post image, end card and overlay theme.

#### Scenario: Short template available
- **GIVEN** the brand kit has a short 9:16 template
- **WHEN** a consumer requests the templates for the short format
- **THEN** the 9:16 template is returned with its format identifier

#### Scenario: Overlay theme available
- **GIVEN** the brand kit has an overlay theme template
- **WHEN** the overlay requests its theme
- **THEN** the overlay theme template is returned

### Requirement: Templates reference brand tokens only

(Must) The system SHALL accept a template only if every colour in it is a reference to a named brand colour token, and SHALL reject any template that contains a raw colour value.

#### Scenario: Token reference accepted
- **GIVEN** a template whose background refers to the token "surface"
- **WHEN** the template is saved
- **THEN** the template is accepted

#### Scenario: Raw colour rejected
- **GIVEN** a template whose text colour is written as a hexadecimal value
- **WHEN** the template is saved
- **THEN** the system rejects the template and names the element holding the raw colour

#### Scenario: Unknown token rejected
- **GIVEN** a template that refers to the token "accent-2" which the kit does not define
- **WHEN** the template is saved
- **THEN** the system rejects the template and names the unknown token

### Requirement: Declared parameters

(Must) The system SHALL require every template to declare its parameters, each with a name and a type, and SHALL support the parameters title, subtitle and image slot.

#### Scenario: Parameters filled at render time
- **GIVEN** a template that declares the parameters title and image slot
- **WHEN** a consumer supplies a title and an image for the slot
- **THEN** the rendered description uses both values

#### Scenario: Undeclared parameter rejected
- **GIVEN** a template that declares only the parameter title
- **WHEN** a consumer supplies a value for the parameter subtitle
- **THEN** the system rejects the request and names the undeclared parameter

#### Scenario: Missing required parameter
- **GIVEN** a template whose title parameter is required
- **WHEN** a consumer supplies no title
- **THEN** the system rejects the request and names the missing parameter

### Requirement: Templates versioned with the kit

(Must) The system SHALL version layout templates together with the brand kit, so that a template change creates a new kit version and a pinned consumer receives the templates of its pinned version.

#### Scenario: Template change creates kit version
- **GIVEN** brand kit version 2 with a thumbnail template
- **WHEN** an editor changes the thumbnail template and publishes
- **THEN** a new kit version exists containing the changed template and version 2 still contains the original

#### Scenario: Pinned consumer keeps its templates
- **GIVEN** the desktop app is pinned to version 2
- **WHEN** version 3 changes the end card template
- **THEN** the desktop app still receives the version 2 end card template

### Requirement: Template preview

(Should) The system SHALL render any template with sample parameters in the web editor so that the result can be checked before publication.

#### Scenario: Sample render
- **GIVEN** an unpublished end card template with sample parameters
- **WHEN** the editor requests a preview
- **THEN** a preview of the end card is shown using the sample parameters
