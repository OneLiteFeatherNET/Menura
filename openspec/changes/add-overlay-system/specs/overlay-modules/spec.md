## Purpose

Overlay modules are the self-contained visual pieces that a scene places on its canvas. This capability defines what a module declares about itself, which built-in modules exist, how third-party modules are registered, and how a module receives data and actions.

## ADDED Requirements

### Requirement: Module manifest
(Must) The hub SHALL accept a module only when its manifest declares an id, a version, a config schema, the data topics it subscribes to and the actions it accepts.

#### Scenario: Complete manifest is accepted
- **GIVEN** a module manifest with an id, a version, a config schema, two subscribed topics and one accepted action
- **WHEN** the manifest is registered
- **THEN** the module is available for placement in scenes

#### Scenario: Incomplete manifest is refused
- **GIVEN** a module manifest without a version
- **WHEN** the manifest is registered
- **THEN** the hub refuses the registration with a Problem Details error that names the missing field
- **AND** the module is not available for placement in scenes

### Requirement: Built-in module set
(Must) The system SHALL provide the built-in modules chat rail, task board, follower goal, promo card, now-playing and limits, and every other built-in module of the prototype catalogue listed in design.md, each as a module with a manifest.

#### Scenario: Built-in modules are listed
- **GIVEN** a fresh hub installation
- **WHEN** an editor opens the list of available modules
- **THEN** the list contains the chat rail, task board, follower goal, promo card, now-playing and limits modules and the other built-in modules
- **AND** each entry shows its id and version

#### Scenario: Built-in module is placed in a scene
- **GIVEN** the built-in follower goal module
- **WHEN** an editor places it in a scene with a goal configuration
- **THEN** the overlay renders the follower goal with that configuration

### Requirement: Third-party modules from the module registry
(Must) The hub SHALL load third-party modules from its module registry and make each registered module available for placement in scenes; only an admin SHALL be able to add a module to the registry.

#### Scenario: Registered third-party module is placed
- **GIVEN** a third-party module that is present in the hub's module registry with a valid manifest
- **WHEN** an editor places the module in a scene
- **THEN** the overlay renders the module in that scene

#### Scenario: Registration requires an admin
- **GIVEN** a person with the editor role and no admin role
- **WHEN** the person tries to add a third-party module to the registry
- **THEN** the hub rejects the request with a 403 Problem Details response
- **AND** the module is not offered for placement

#### Scenario: Module removed from the registry
- **GIVEN** a scene that places a third-party module
- **WHEN** the module is removed from the registry
- **THEN** the hub no longer offers the module for placement
- **AND** the overlay shows the scene without that module and without an error page

### Requirement: Topic subscriptions limit module data
(Must) The overlay SHALL deliver to a module only the data of the topics that the module declares in its manifest.

#### Scenario: Module receives only its topics
- **GIVEN** a task board module that subscribes to the task topic and not to the follower topic
- **WHEN** a follower event arrives in the scene
- **THEN** the task board module receives no follower data

### Requirement: Module actions are declared
(Must) The overlay SHALL accept an action for a module only when the module's manifest declares that action.

#### Scenario: Declared action is executed
- **GIVEN** a promo card module whose manifest accepts a "show" action
- **WHEN** an operator triggers the "show" action for that module
- **THEN** the promo card module shows its content

#### Scenario: Undeclared action is refused
- **GIVEN** a promo card module whose manifest does not accept a "reset" action
- **WHEN** an operator triggers a "reset" action for that module
- **THEN** the hub refuses the action with a Problem Details error
- **AND** the module state stays unchanged

### Requirement: Module sandboxing
(Could) Where a third-party module is loaded, the overlay SHALL be able to run that module in an isolated frame so that it cannot read the scene token or the data of other modules.

#### Scenario: Isolated third-party module
- **GIVEN** a third-party module that runs in an isolated frame
- **WHEN** the module attempts to read the scene token
- **THEN** the token is not accessible to the module
