# Spec Delta

## Purpose

Lets each person keep an individual variant of the OLF brand kit. The kit decides which tokens and assets may be overridden; a person's profile overrides only those, and everything else follows the central kit, so one central change still reaches every streamer at once.

## ADDED Requirements

### Requirement: Overridable markers in the kit

(Must) The system SHALL mark every colour token, typography role and asset in the brand kit as either fixed or overridable, and SHALL treat an unmarked item as fixed.

#### Scenario: Marked items
- **GIVEN** a kit in which the accent colour token is marked overridable and the headline font role is marked fixed
- **WHEN** an editor reads the kit's override markers
- **THEN** the accent colour is reported as overridable and the headline font role as fixed

#### Scenario: Unmarked item is fixed
- **GIVEN** a token without an override marker
- **WHEN** a profile tries to override it
- **THEN** the token is treated as fixed and the override is refused

### Requirement: One profile per person

(Must) The system SHALL give each person exactly one brand profile, created with no overrides, so that the person inherits the whole central kit until they override something.

#### Scenario: New profile inherits everything
- **GIVEN** a person who has never opened their profile
- **WHEN** the profile is first read
- **THEN** the profile exists, holds no overrides, and resolves every value to the central kit

### Requirement: Overrides limited to overridable items

(Must) The system SHALL accept a profile override only for a colour token, typography role or asset that the kit marks overridable, and SHALL refuse any other override.

#### Scenario: Overridable accent colour accepted
- **GIVEN** the accent colour is marked overridable
- **WHEN** the person sets their accent colour to a new value
- **THEN** the override is stored and the resolved accent colour is the new value

#### Scenario: Fixed token refused
- **GIVEN** the headline font role is marked fixed
- **WHEN** the person tries to override the headline font
- **THEN** the system refuses the override and names the item as fixed

### Requirement: Inheritance of all other values

(Must) The system SHALL resolve every value of a profile to the profile's override when one exists and otherwise to the value of the central kit.

#### Scenario: Mixed resolution
- **GIVEN** a profile that overrides only the accent colour
- **WHEN** the resolved profile is read
- **THEN** the accent colour comes from the profile and every other value comes from the central kit

#### Scenario: Override of a removed token
- **GIVEN** a profile override for a token that a newer kit version no longer defines
- **WHEN** the resolved profile is read for that version
- **THEN** the override is not applied and the response reports the unknown token

### Requirement: Central changes reach every person at once

(Must) The system SHALL, when a central kit version changes a value, make the new value visible to every person whose profile does not override that exact token, and SHALL notify their subscribed clients over Server-Sent Events at the time of publication.

#### Scenario: Central accent change without override
- **GIVEN** two people, one who overrode the accent colour and one who did not
- **WHEN** the central kit publishes a new headline colour
- **THEN** both people's resolved headline colour changes and both subscribed clients receive the notification

#### Scenario: Overridden token unaffected
- **GIVEN** a person who overrode the accent colour
- **WHEN** the central kit publishes a new accent colour
- **THEN** the person's resolved accent colour stays at their override

### Requirement: Contrast of profile overrides

(Must) The system SHALL check a profile override of a colour used for text against the same WCAG AA contrast rule as the central kit and SHALL refuse an override that fails it.

#### Scenario: Passing override accepted
- **GIVEN** a person sets a text-role colour that reaches WCAG AA against its background
- **WHEN** the override is saved
- **THEN** the override is stored

#### Scenario: Failing override refused
- **GIVEN** a person sets a text-role colour below WCAG AA against its background
- **WHEN** the override is saved
- **THEN** the system refuses it and reports the role pair and the contrast ratio

### Requirement: Personal assets

(Must) The system SHALL let a person supply their own logo, name tag and camera frame in place of the kit's asset of the same role, only where the kit marks that asset overridable.

#### Scenario: Personal name tag used
- **GIVEN** the name tag asset is marked overridable and the person uploads their own name tag
- **WHEN** the resolved profile is read
- **THEN** the person's name tag is returned in place of the kit's name tag

#### Scenario: Personal logo refused where fixed
- **GIVEN** the primary logo is marked fixed
- **WHEN** the person tries to replace it
- **THEN** the system refuses the replacement

### Requirement: Reverting an override

(Must) The system SHALL let a person remove an override, after which the token or asset resolves to the central kit again.

#### Scenario: Override removed
- **GIVEN** a profile that overrides the accent colour
- **WHEN** the person removes that override
- **THEN** the accent colour resolves to the central kit value again

### Requirement: Access to profiles

(Must) The system SHALL allow only the owner of a profile to change it, SHALL allow the owner and editors to read it, and SHALL answer 404 to any other caller.

#### Scenario: Owner changes own profile
- **GIVEN** a person with an overridable accent colour
- **WHEN** the person changes their accent colour
- **THEN** the change is stored

#### Scenario: Other member cannot see profile
- **GIVEN** a member who is neither the profile's owner nor an editor
- **WHEN** the member requests that profile
- **THEN** the system responds 404 Not Found in Problem Details format

#### Scenario: Editor reads a profile
- **GIVEN** an editor
- **WHEN** the editor reads another person's profile
- **THEN** the profile is returned, and the editor cannot change it

### Requirement: Resolved profile for clients

(Must) The system SHALL provide the resolved profile of the requesting person over REST and MCP, and SHALL let the OBS plugin, the overlay and the desktop app read it, resolved against the kit version each consumer has pinned or, without a pin, the latest version.

#### Scenario: OBS plugin reads its owner's profile
- **GIVEN** an OBS plugin signed in as a person
- **WHEN** it requests that person's resolved profile
- **THEN** the profile is returned resolved against the latest kit version or the version pinned by that consumer
