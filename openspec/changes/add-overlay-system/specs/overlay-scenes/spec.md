## Purpose

Scenes are the hub-managed layouts of overlay modules that OBS shows as browser sources. Central scene templates define the layout for the team; each person gets a personal variant that inherits the template and applies that person's profile. This capability defines how templates and variants are composed, addressed and protected, and who may change them.

## ADDED Requirements

### Requirement: Central scene templates
(Must) The hub SHALL store each scene template as a layout of module instances, where every instance has a position, a size and its own module configuration.

#### Scenario: Template with two module instances
- **GIVEN** an editor has created a template named "Main"
- **WHEN** the editor places a chat module and a follower goal module on it with positions, sizes and configuration
- **THEN** the hub stores both instances with their positions, sizes and configuration as part of the template
- **AND** reading the template returns the same layout

#### Scenario: Module instance outside the canvas is rejected
- **GIVEN** a template with a defined canvas size
- **WHEN** a module instance is saved with a position or size that lies outside the canvas
- **THEN** the hub rejects the change with a Problem Details error
- **AND** the stored template stays unchanged

### Requirement: Scene template editing is limited to editors and admins
(Must) The hub SHALL allow only persons with the editor or admin role to create, change or delete scene templates and their layouts.

#### Scenario: Viewer tries to change a template
- **GIVEN** a person with the viewer role
- **WHEN** the person tries to change the layout of a template
- **THEN** the hub refuses the change with 403 as a Problem Details error
- **AND** the template stays unchanged

#### Scenario: Editor changes a template
- **GIVEN** a person with the editor role
- **WHEN** the person changes the layout of a template
- **THEN** the hub stores the change
- **AND** the change is audit-logged with the person, the template and the time

### Requirement: Personal variant per person and template
(Must) The hub SHALL give each person a personal variant of every scene template, which inherits the template's layout and applies only the overrides that the person's brand profile allows.

#### Scenario: Variant created for a new person
- **GIVEN** a central template "Main" and a person with a brand profile
- **WHEN** the person first opens the scene list
- **THEN** the hub provides a personal variant of "Main" for that person
- **AND** the variant shows the template's layout with the person's profile overrides applied

#### Scenario: Variant cannot change the layout
- **GIVEN** a personal variant of template "Main"
- **WHEN** the owner tries to move a module instance of the variant
- **THEN** the hub refuses the change with a Problem Details error
- **AND** the layout stays inherited from the template

#### Scenario: Template change reaches variants
- **GIVEN** a personal variant that has not overridden the position of a module
- **WHEN** an editor moves that module in the central template
- **THEN** the variant shows the module at the new position

### Requirement: Variants are private to their owner
(Must) The hub SHALL show a personal variant only to its owner, and SHALL answer a request for another person's variant with 404 as a Problem Details error, even to editors and admins.

#### Scenario: Other person requests a variant
- **GIVEN** a personal variant owned by person A
- **WHEN** person B requests that variant
- **THEN** the hub answers 404 as a Problem Details error

#### Scenario: Editor lists scene templates
- **GIVEN** an editor who lists scene templates
- **WHEN** the list is returned
- **THEN** it contains the central templates and no other person's variant

### Requirement: Browser-source URL per variant
(Must) The hub SHALL give each personal variant a browser-source URL for OBS that embeds a variant-scoped, read-only token.

#### Scenario: Browser source loads a variant
- **GIVEN** a personal variant with a browser-source URL
- **WHEN** OBS loads that URL as a browser source
- **THEN** the overlay shows the layout of that variant with its current module data

#### Scenario: Token grants only its variant
- **GIVEN** two variants of person A, "Main" and "Vertical", with their own browser-source URLs
- **WHEN** the token of "Main" is used to read the feed of "Vertical"
- **THEN** the hub refuses the request with 401 or 403 as a Problem Details error

### Requirement: Variant tokens are rotated by the owner's OBS plugin
(Must) The hub SHALL let the OBS plugin of a variant's owner rotate that variant's token, and the previous token SHALL stop granting access once the rotation is done; an admin SHALL be able to revoke a variant token.

#### Scenario: Rotation breaks the old URL
- **GIVEN** a variant whose browser-source URL carries token A
- **WHEN** the owner's OBS plugin rotates the token to B
- **THEN** a feed request with token A is refused with 401 as a Problem Details error
- **AND** a feed request with token B is accepted

#### Scenario: Admin revokes a token
- **GIVEN** a variant token that an admin has revoked
- **WHEN** the browser source uses that token
- **THEN** the hub refuses the feed with 401 as a Problem Details error

#### Scenario: Rotation is audit-logged without the token
- **GIVEN** an owner's OBS plugin that rotates a variant token
- **WHEN** the rotation completes
- **THEN** the audit log records the person, the variant and the time of the rotation
- **AND** the audit log does not contain the token value

### Requirement: Scene tokens are never logged or referred
(Must) The hub SHALL redact scene tokens from logs, traces and error reports, and SHALL send a Referrer-Policy of no-referrer with every overlay page.

#### Scenario: Token in a failed request
- **GIVEN** a feed request with a wrong token that fails
- **WHEN** the failure is logged and reported
- **THEN** the log and the report contain no token value

#### Scenario: Overlay page has no referrer policy header
- **GIVEN** an overlay page served by the hub
- **WHEN** the page is requested
- **THEN** the response carries Referrer-Policy: no-referrer

### Requirement: Opaque identifiers
(Must) The hub SHALL use non-sequential identifiers for templates, variants and module instances.

#### Scenario: Identifiers are not guessable
- **GIVEN** two templates created one after another
- **WHEN** their identifiers are compared
- **THEN** the identifiers do not follow a sequence

### Requirement: Scene configuration is validated on save
(Must) When a template or variant is saved, the hub SHALL validate every module instance's configuration against the config schema of that module's manifest and reject invalid configuration.

#### Scenario: Invalid module configuration
- **GIVEN** a chat module whose config schema requires a numeric message limit
- **WHEN** an editor saves a template with a non-numeric message limit for that module
- **THEN** the hub rejects the save with a Problem Details error that names the invalid field
- **AND** the stored template stays unchanged
