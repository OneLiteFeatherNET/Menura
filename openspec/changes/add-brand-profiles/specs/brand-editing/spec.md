# Spec Delta

## Purpose

Lets the OLF team edit the palette of the brand kit in the web app and preview the change before it is published, and lets sub-brands inherit the base kit. Both build on the brand kit of `add-brand-kit`; neither changes the kit's publication rules.

## ADDED Requirements

### Requirement: Web palette editor with live preview

(Should) The system SHALL offer a web editor for the colour palette that shows the effect of unpublished palette changes on each layout template before publication.

#### Scenario: Preview before publish
- **GIVEN** an editor changes the primary colour without publishing
- **WHEN** the editor opens a layout template preview
- **THEN** the preview shows the template in the new colour and the published version is unchanged

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
