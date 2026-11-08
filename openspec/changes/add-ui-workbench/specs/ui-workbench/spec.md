# Spec Delta

## Purpose

Gives the web app a workbench in which every shared component is built, seen and tested before a page uses it, and a set of end-to-end tests that drive the real app against the real server. The defects that the prototype showed (invisible select options, a menu covering the controls below it, a header action leaving the viewport) must fail a test before they reach a page.

## ADDED Requirements

### Requirement: Storybook workbench

(Must) The system SHALL provide a Storybook 10 workbench in the `web` package, built with `@storybook/vue3-vite`, that shares the application's Vite configuration, theme tokens, light and dark mode and German locale, and that is started by `pnpm storybook` and built by `pnpm build-storybook`.

#### Scenario: Story renders with the app theme
- **GIVEN** a component story rendered in the workbench
- **WHEN** the theme toggle switches between light and dark mode
- **THEN** the story uses the same theme tokens as the application in that mode, with no hard-coded colour

#### Scenario: Build command produces a static workbench
- **GIVEN** a clean checkout of the `web` package with its dependencies installed
- **WHEN** `pnpm build-storybook` runs
- **THEN** it exits with status 0 and writes a static workbench to `storybook-static/`

#### Scenario: Workbench started for development
- **GIVEN** a developer in the `web` package
- **WHEN** `pnpm storybook` runs
- **THEN** the workbench is served locally and lists every story of `web/src/components/ui/`

### Requirement: Storybook build as internal artifact

(Should) The system SHOULD publish the static Storybook build as a CI artifact available to team members, and SHALL NOT include the build or the Storybook configuration in the production web image.

#### Scenario: Artifact produced on pull request
- **GIVEN** a pull request that changes a component story
- **WHEN** the build workflow completes
- **THEN** the static workbench is attached to the run as an artifact

#### Scenario: Production image is free of the workbench
- **GIVEN** the production web image built from `web/Dockerfile`
- **WHEN** its file system is listed
- **THEN** it contains no `storybook-static/` directory and no `.storybook/` configuration

### Requirement: Shared component layer

(Must) The system SHALL provide the shared components under `web/src/components/ui/` (at least AppSelect, AppTextField, FilterBar, TagChips, StatusBadge, MetaList, PageHeader, CardGrid, DataTable, ConfirmInline, AppDialog, EmptyState and LoadState), each taking its data as props, reporting user actions as emitted events and making no API call.

#### Scenario: Component has no API access
- **GIVEN** the source of a component under `web/src/components/ui/`
- **WHEN** its imports are checked
- **THEN** it imports no module from the API layer and performs no network request

#### Scenario: Component stories cover its states
- **GIVEN** a shared component
- **WHEN** its stories are listed
- **THEN** the stories include the default state and, where the component shows them, the empty, loading, error, long-text and disabled states

#### Scenario: Role-dependent component shows both roles
- **GIVEN** a shared component whose actions depend on the role
- **WHEN** its stories are listed
- **THEN** the stories include a viewer variant and an editor variant

#### Scenario: New features use the components
- **GIVEN** a feature view added by a later code change
- **WHEN** the view is reviewed
- **THEN** it composes the shared components instead of defining its own select, filter bar, badge or page header

### Requirement: Interaction tests in a real browser

(Must) The system SHALL run the `play` functions of the stories and their interaction assertions in Chromium through the Storybook Vitest addon in Vitest browser mode with the Playwright provider, locally and in CI on every pull request, and a failing assertion SHALL fail the run.

#### Scenario: Select shows its option labels
- **GIVEN** the AppSelect story with three options
- **WHEN** its `play` function opens the select
- **THEN** each option label is visible with non-empty text in the rendered menu

#### Scenario: Select menu does not cover the following controls
- **GIVEN** the AppSelect story with a text field placed directly below it
- **WHEN** its `play` function opens the select menu
- **THEN** the menu's bounding box does not overlap the bounding box of the text field, and the text field remains interactable

#### Scenario: Browser tests run on pull requests
- **GIVEN** a pull request that changes a component
- **WHEN** the build workflow runs
- **THEN** the Storybook interaction tests run in Chromium and a failing test blocks the merge

#### Scenario: Browser tests run locally
- **GIVEN** a developer with the browser installed by the Playwright provider
- **WHEN** the Storybook test command runs
- **THEN** the same tests run in Chromium with the same outcome as in CI

### Requirement: Automated accessibility checks

(Must) The system SHALL check every story with axe through the accessibility addon, and SHALL fail the story's test on any violation of impact `serious` or `critical`; violations of lower impact SHALL be reported without failing the run.

#### Scenario: Serious violation fails the story
- **GIVEN** a story whose rendered control has no accessible name
- **WHEN** its accessibility check runs
- **THEN** the check reports a serious violation and the story's test fails

#### Scenario: Moderate violation is reported only
- **GIVEN** a story with a moderate axe violation and no serious or critical violation
- **WHEN** its accessibility check runs
- **THEN** the violation is reported and the story's test passes

### Requirement: Page header actions stay within the viewport

(Must) The system SHALL keep the actions of PageHeader inside the viewport at 1280 px and at 390 px width, with no horizontal scroll of the document, and SHALL verify this with a browser test.

#### Scenario: Action inside the viewport at desktop width
- **GIVEN** the PageHeader story with a long title and a primary action
- **WHEN** its test sets the viewport to 1280 px
- **THEN** the action's right edge lies at or before 1280 px and the document has no horizontal scroll

#### Scenario: Action inside the viewport at phone width
- **GIVEN** the PageHeader story with a long title and a primary action
- **WHEN** its test sets the viewport to 390 px
- **THEN** the action's right edge lies at or before 390 px and the document has no horizontal scroll

### Requirement: Test-only sign-in in environment e2e

(Must) The system SHALL expose a test-only sign-in endpoint only in the Micronaut environment `e2e`, through a bean annotated `@Requires(env = "e2e")`, which issues the normal session cookie `__Host-menura-session` for a test role chosen in the request, and SHALL NOT expose the endpoint in any other environment.

#### Scenario: Sign-in as a chosen role
- **GIVEN** the server running in environment `e2e`
- **WHEN** a test requests the test-only sign-in for the role editor
- **THEN** the response sets a `__Host-menura-session` cookie that the server accepts as an editor session

#### Scenario: Endpoint absent outside e2e
- **GIVEN** the server running in environment `prod`
- **WHEN** a request is sent to the test-only sign-in path
- **THEN** the server answers 404 and issues no cookie

#### Scenario: Cookie not logged
- **GIVEN** a test-only sign-in in environment `e2e`
- **WHEN** the request is logged
- **THEN** the log contains no cookie value

### Requirement: Refusal to start with e2e and prod together

(Must) If the active Micronaut environments include both `e2e` and `prod`, then the system SHALL refuse to start and SHALL name both environments in the startup error.

#### Scenario: Combined environments refused
- **GIVEN** the environments `e2e` and `prod` are both active
- **WHEN** the server starts
- **THEN** the process exits with a non-zero status before it accepts requests, and the error names `e2e` and `prod`

#### Scenario: Single environment starts
- **GIVEN** only environment `e2e` is active
- **WHEN** the server starts
- **THEN** it starts and accepts requests

### Requirement: End-to-end flows with Playwright

(Must) The system SHALL cover the flows sign-in per role, pool browse, filter and search, planning a clip in the calendar, and analytics tab navigation with Playwright tests in `web/e2e/` against the built web app and the real server in environment `e2e`, using Chromium only.

#### Scenario: Sign-in per role
- **GIVEN** the e2e environment with the test-only sign-in
- **WHEN** a test signs in as viewer, editor, publisher, operator and admin in turn
- **THEN** each session shows the navigation that its role allows and no other

#### Scenario: Pool filter and search
- **GIVEN** a test that has created three pool entries of which one matches a search term
- **WHEN** the test applies the search term and a filter
- **THEN** only the matching entry is listed

#### Scenario: Plan a clip in the calendar
- **GIVEN** a test signed in as editor and an entry in the pool
- **WHEN** the test plans the entry for a fixed future day in the calendar
- **THEN** the calendar shows the planned entry on that day

#### Scenario: Analytics tab navigation
- **GIVEN** a test signed in as viewer
- **WHEN** the test switches between the analytics tabs
- **THEN** each tab shows its heading and the URL reflects the selected tab

### Requirement: Deterministic end-to-end data

(Must) The system SHALL run each end-to-end test with a fixed clock injected through the `e2e` environment and with data that the test itself creates, so that no test depends on the data or the result of another test or on the order of execution.

#### Scenario: Fixed clock
- **GIVEN** the e2e environment with its fixed clock set to a known date
- **WHEN** a test reads "today" in the calendar
- **THEN** the calendar shows that date regardless of the wall clock time

#### Scenario: Independent tests
- **GIVEN** two end-to-end tests run in reverse order
- **WHEN** the suite runs
- **THEN** both pass with the same results

#### Scenario: No fixed waits
- **GIVEN** the end-to-end test sources
- **WHEN** they are searched for fixed waits
- **THEN** no test contains a sleep or fixed timeout in place of a Playwright assertion

### Requirement: CI job for workbench and end-to-end tests

(Must) The system SHALL run the Storybook interaction tests and the Playwright end-to-end tests in a job of the existing build workflow on every pull request, SHALL block the merge when either fails, SHALL keep the Playwright traces of a failed test as CI artifacts, and SHALL retry a failed test at most once in CI and never locally.

#### Scenario: Failure blocks merge
- **GIVEN** a pull request in which an end-to-end test fails
- **WHEN** the job completes
- **THEN** the pull request check is failed and the trace of the failed test is attached to the run

#### Scenario: Retry limited
- **GIVEN** a test that fails once and passes on retry in CI
- **WHEN** the job completes
- **THEN** the test is reported as flaky and the run is not hidden as passed

#### Scenario: No retry locally
- **GIVEN** a developer running the end-to-end suite locally
- **WHEN** a test fails
- **THEN** the test is not retried
