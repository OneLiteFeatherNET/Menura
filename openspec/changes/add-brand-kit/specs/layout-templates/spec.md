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

### Requirement: Short 9:16 template

(Must) The system SHALL provide a short 9:16 template of 1080 by 1920 pixels that declares the slots badge, title, captions, session-tiles and cta; SHALL show the badge for the whole duration of a short, the title only from 0 to 3 seconds and the cta only during the last 2 seconds; SHALL declare the text parameter join_host for the cta; and SHALL NOT contain an end card.

#### Scenario: Badge for the whole duration
- **GIVEN** a 30-second short rendered with the short 9:16 template
- **WHEN** the badge slot is evaluated at 0 seconds and at 29 seconds
- **THEN** the badge is visible at both times

#### Scenario: Title limited to the first three seconds
- **GIVEN** a short rendered with the short 9:16 template
- **WHEN** the title slot is evaluated at 2.5 seconds and at 3.5 seconds
- **THEN** the title is visible at 2.5 seconds and not visible at 3.5 seconds

#### Scenario: Call to action in the last two seconds
- **GIVEN** a 30-second short rendered with the short 9:16 template and a join_host text
- **WHEN** the cta slot is evaluated at 20 seconds and at 28.5 seconds
- **THEN** the cta is not visible at 20 seconds and visible at 28.5 seconds

#### Scenario: join_host required
- **GIVEN** the cta slot declares the text parameter join_host
- **WHEN** a short is rendered without a join_host value
- **THEN** the system rejects the request and names the missing parameter join_host

#### Scenario: No end card
- **GIVEN** the short 9:16 template
- **WHEN** the template is validated
- **THEN** it declares no end card slot and no element is shown after the cta

### Requirement: Safe zone for captions, badge, title and logo

(Must) The system SHALL keep the badge, title, captions and logo slots of a template out of the top 14 % and the bottom 35 % of the frame height, and SHALL refuse to save a template that places any of these slots in those bands, naming the slot.

#### Scenario: Template inside the safe zone
- **GIVEN** a short 9:16 template whose captions slot lies between 14 % and 65 % of the frame height
- **WHEN** the template is saved
- **THEN** the template is accepted

#### Scenario: Slot in the top band refused
- **GIVEN** a template whose badge slot lies in the top 14 % of the frame
- **WHEN** the template is saved
- **THEN** the system rejects the template and names the badge slot

#### Scenario: Slot in the bottom band refused
- **GIVEN** a template whose title slot starts in the bottom 35 % of the frame
- **WHEN** the template is saved
- **THEN** the system rejects the template and names the title slot

### Requirement: Template preview

(Should) The system SHALL render any template with sample parameters in the web editor so that the result can be checked before publication.

#### Scenario: Sample render
- **GIVEN** an unpublished end card template with sample parameters
- **WHEN** the editor requests a preview
- **THEN** a preview of the end card is shown using the sample parameters
