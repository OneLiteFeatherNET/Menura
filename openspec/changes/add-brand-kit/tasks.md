# Tasks

## 1. Kit model and versioning

- [ ] 1.1 Write failing unit tests that a saved change creates a new immutable version and earlier versions stay readable; verify they fail.
- [ ] 1.2 Implement whole-kit versions with immutable storage; verify the 1.1 tests pass.
- [ ] 1.3 Write failing unit tests for named colour tokens with light and dark values and for refusing a publication where a token lacks a role; verify they fail.
- [ ] 1.4 Implement colour tokens with role validation; verify the 1.3 tests pass.
- [ ] 1.5 Write failing unit tests that the same seed colour always yields identical tones and that a new seed updates the derived tones; verify they fail.
- [ ] 1.6 Implement tonal palette derivation at publication time and store the tones in the version; verify the 1.5 tests pass.
- [ ] 1.7 Add the database migration for kit versions; verify it applies to an empty PostgreSQL in an integration test.
- [ ] 1.8 Document the kit structure and version fields in the OpenAPI description; verify the OpenAPI document validates.

## 2. Contrast, typography and marks

- [ ] 2.1 Write failing unit tests for the WCAG AA check: a passing text role publishes and a failing one is refused with the role pair and ratio in the report; verify they fail.
- [ ] 2.2 Implement the contrast check in the publication path; verify the 2.1 tests pass.
- [ ] 2.3 Write failing unit tests that each typography role maps to one font file and that a role without a font refuses publication; verify they fail.
- [ ] 2.4 Implement typography roles and font file storage; verify the 2.3 tests pass.
- [ ] 2.5 Write failing unit tests for marks with named variants and a safe area per variant; verify they fail.
- [ ] 2.6 Implement marks storage; verify the 2.5 tests pass.
- [ ] 2.7 Write failing unit tests for voice text and per-language hashtags and end-card texts, including the rule that a missing language is reported and not substituted; verify they fail.
- [ ] 2.8 Implement the per-language texts; verify the 2.7 tests pass.
- [ ] 2.9 Document typography, marks and text fields in the OpenAPI description; verify the OpenAPI document validates.

## 3. Reading, pinning and notification

- [ ] 3.1 Write failing unit tests for the REST and MCP reads of the current kit and of a named version, and for a request without a valid token being refused with Problem Details; verify they fail.
- [ ] 3.2 Implement the reads; verify the 3.1 tests pass.
- [ ] 3.3 Write failing unit tests that a pinned consumer keeps its version after a new publication and an unpinned consumer receives the latest; verify they fail.
- [ ] 3.4 Implement pins stored per consumer; verify the 3.3 tests pass.
- [ ] 3.5 Write failing unit tests that a publication emits one notification naming the new version to every subscribed client, using a fake event sink and an injected clock; verify they fail.
- [ ] 3.6 Implement the SSE notification; verify the 3.5 tests pass.
- [ ] 3.7 Document the pin parameter and the notification event in the OpenAPI description and the notification description; verify both documents validate.

## 4. Layout templates

- [ ] 4.1 Write failing unit tests that a template referencing a token is accepted, and that a raw colour value or an unknown token is rejected with the element named; verify they fail.
- [ ] 4.2 Implement the template model and token validation; verify the 4.1 tests pass.
- [ ] 4.3 Write failing unit tests that each required format exists, that parameters must be declared, and that undeclared or missing parameters are refused; verify they fail.
- [ ] 4.4 Implement the template formats and parameter validation; verify the 4.3 tests pass.
- [ ] 4.5 Write failing unit tests that a template change creates a kit version and that a pinned consumer receives the templates of its pinned version; verify they fail.
- [ ] 4.6 Implement template versioning with the kit; verify the 4.5 tests pass.
- [ ] 4.7 Document the template format and its parameters for the desktop and overlay teams; verify the document lists every format and parameter.

## 5. Web editing and exports (Should, Could)

- [ ] 5.1 Write failing unit tests that the palette preview uses unpublished values while the published version stays unchanged; verify they fail.
- [ ] 5.2 Implement the palette editor preview and the template preview with sample parameters in the web app; verify the 5.1 tests pass and a component test renders a template with the new colour.
- [ ] 5.3 Write failing unit tests that the JSON and CSS custom property exports list every colour token with its light and dark values; verify they fail.
- [ ] 5.4 Implement the design token export; verify the 5.3 tests pass.
- [ ] 5.5 Write failing unit tests that a sub-brand returns inherited values and its own overrides; verify they fail.
- [ ] 5.6 Implement sub-brand inheritance; verify the 5.5 tests pass.

## 6. Per-person profiles

- [ ] 6.1 Write failing unit tests that items are fixed unless marked overridable, and that an override of a fixed item is refused; verify they fail.
- [ ] 6.2 Implement the overridable markers and the override validation; verify the 6.1 tests pass.
- [ ] 6.3 Write failing unit tests for inheritance: a profile resolves overridden values to its own and all others to the kit, a removed token is reported, and removing an override reverts it; verify they fail.
- [ ] 6.4 Implement profile storage and resolution against the pinned or latest kit version; verify the 6.3 tests pass.
- [ ] 6.5 Write failing unit tests that a central change updates every non-overriding profile and sends one notification per subscribed client, while an overridden token stays unchanged, using a fake event sink and an injected clock; verify they fail.
- [ ] 6.6 Implement the change propagation through the existing notification; verify the 6.5 tests pass.
- [ ] 6.7 Write failing unit tests that a text-role override below WCAG AA is refused, that personal assets are accepted only where marked overridable, and that a non-owner non-editor receives 404; verify they fail.
- [ ] 6.8 Implement the contrast check for overrides, personal assets and the access rules; verify the 6.7 tests pass.
- [ ] 6.9 Document the profile resources, override markers and 404 behaviour in the OpenAPI description; verify the OpenAPI document validates.

## 7. Brand MCP resources

- [ ] 7.1 Write failing unit tests that the brand kit resource returns the current version to a caller with the viewer role or higher, and that a request without a valid token is refused with Problem Details; verify they fail first
- [ ] 7.2 Write failing unit tests that the resource for a pinned version returns that version and not the newest one; verify they fail first
- [ ] 7.3 Implement the brand kit resource with the current and pinned reads on the hub MCP server; verify the 7.1 and 7.2 tests pass
- [ ] 7.4 Write failing unit tests that the layout templates and the editing rules are exposed as MCP resources of the same version as the kit; verify they fail first
- [ ] 7.5 Implement the layout template and editing rules resources; verify the 7.4 tests pass
- [ ] 7.6 Document the brand resources in the hub MCP documentation and verify each documented resource URI exists in the resource registry test

## 8. Analytics: layout template dimension

- [ ] 8.1 Write failing tests that analytics aggregates can be grouped and filtered by the layout template an asset was rendered with, read from rollups; verify they fail
- [ ] 8.2 Provide the layout template of each published asset to the analytics rollup job; verify the tests from the previous task pass

## 9. Integration check

- [ ] 9.1 Run the integration suite tagged `integration` on a fresh database and verify publication, contrast refusal, pinning, notification, profile resolution and the brand MCP resources work end to end.
- [ ] 9.2 Open the code pull request titled `feat(brand): add brand kit and layout templates`.

## Workflow follow-up

- Tick the completed boxes and archive the change in a follow-up pull request after the code pull request is merged.
