# Tasks

## 1. Per-person profiles

- [ ] 1.1 Write failing unit tests that items are fixed unless marked overridable, and that an override of a fixed item is refused; verify they fail.
- [ ] 1.2 Implement the overridable markers and the override validation; verify the 1.1 tests pass.
- [ ] 1.3 Write failing unit tests for inheritance: a profile resolves overridden values to its own and all others to the kit, a removed token is reported, and removing an override reverts it; verify they fail.
- [ ] 1.4 Implement profile storage and resolution against the pinned or latest kit version; verify the 1.3 tests pass.
- [ ] 1.5 Write failing unit tests that a central change updates every non-overriding profile and sends one notification per subscribed client, while an overridden token stays unchanged, using a fake event sink and an injected clock; verify they fail.
- [ ] 1.6 Implement the change propagation through the existing notification; verify the 1.5 tests pass.
- [ ] 1.7 Write failing unit tests that a text-role override below WCAG AA is refused, that personal assets are accepted only where marked overridable, and that a non-owner non-editor receives 404; verify they fail.
- [ ] 1.8 Implement the contrast check for overrides, personal assets and the access rules; verify the 1.7 tests pass.
- [ ] 1.9 Document the profile resources, override markers and 404 behaviour in the OpenAPI description; verify the OpenAPI document validates.

## 2. Brand MCP resources

- [ ] 2.1 Write failing unit tests that the brand kit resource returns the current version to a caller with the viewer role or higher, and that a request without a valid token is refused with Problem Details; verify they fail first.
- [ ] 2.2 Write failing unit tests that the resource for a pinned version returns that version and not the newest one; verify they fail first.
- [ ] 2.3 Implement the brand kit resource with the current and pinned reads on the hub MCP server; verify the 2.1 and 2.2 tests pass.
- [ ] 2.4 Write failing unit tests that the layout templates and the editing rules are exposed as MCP resources of the same version as the kit; verify they fail first.
- [ ] 2.5 Implement the layout template and editing rules resources; verify the 2.4 tests pass.
- [ ] 2.6 Document the brand resources in the hub MCP documentation and verify each documented resource URI exists in the resource registry test.

## 3. Web palette editor

- [ ] 3.1 Write failing unit tests that the palette preview uses unpublished values while the published version stays unchanged; verify they fail.
- [ ] 3.2 Implement the palette editor preview in the web app; verify the 3.1 tests pass and a component test renders a template with the new colour.

## 4. Sub-brands

- [ ] 4.1 Write failing unit tests that a sub-brand returns inherited values and its own overrides; verify they fail.
- [ ] 4.2 Implement sub-brand inheritance; verify the 4.1 tests pass.

## 5. Integration check

- [ ] 5.1 Run the integration suite tagged `integration` on a fresh database and verify profile resolution, the contrast refusal for overrides, the brand MCP resources, the palette preview and sub-brand inheritance work end to end.
- [ ] 5.2 Open the code pull request titled `feat(brand): add brand profiles and editing`.

## Workflow follow-up

- Tick the completed boxes and archive the change in a follow-up pull request after the code pull request is merged.
