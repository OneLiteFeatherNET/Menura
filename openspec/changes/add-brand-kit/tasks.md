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

- [ ] 3.1 Write failing unit tests for the REST reads of the current kit and of a named version, and for a request without a valid token being refused with Problem Details; verify they fail.
- [ ] 3.2 Implement the REST reads; verify the 3.1 tests pass.
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
- [ ] 4.5 Write failing unit tests that the short 9:16 template is 1080 by 1920 pixels and declares the slots badge, title, captions, session-tiles and cta; verify they fail.
- [ ] 4.6 Write failing unit tests that the badge is visible for the whole duration, the title only from 0 to 3 seconds, the cta only during the last 2 seconds, the cta requires the text parameter join_domain, the same installation-wide join domain is used for every channel, and no end card exists; verify they fail.
- [ ] 4.7 Implement the short 9:16 template with its slots, timing and parameters; verify the 4.5 and 4.6 tests pass.
- [ ] 4.8 Write failing unit tests that a template whose badge, title, captions or logo slot lies in the top 14 % or bottom 35 % of the frame is refused and the slot is named, and that a template inside the safe zone is accepted; verify they fail.
- [ ] 4.9 Implement the safe-zone validation on save; verify the 4.8 tests pass.
- [ ] 4.10 Write failing unit tests that a template change creates a kit version and that a pinned consumer receives the templates of its pinned version; verify they fail.
- [ ] 4.11 Implement template versioning with the kit; verify the 4.10 tests pass.
- [ ] 4.12 Document the template format, the short 9:16 slots and timing, the safe zone and every parameter for the renderer and overlay teams; verify the document lists every format and parameter.

## 5. Render bundle, CSS export and initial kit

- [ ] 5.1 Write failing unit tests that the JSON render bundle of a named version contains every colour token with its resolved values, every typography role with its font file reference, every mark with its safe area per variant, the per-language texts and the layout templates; verify they fail.
- [ ] 5.2 Implement the JSON render bundle export; verify the 5.1 tests pass.
- [ ] 5.3 Write failing unit tests that a request for a pinned version returns exactly that version's bundle and not the newest one, and that the CSS custom property export lists every colour token with its light and dark values; verify they fail.
- [ ] 5.4 Implement the pinned bundle request and the CSS custom property export; verify the 5.3 tests pass.
- [ ] 5.5 Write failing unit tests that the first published version takes its colour roles from the overlay theme and its mark from the feather logo, and that the render bundle and CSS export contain no colour value outside the kit; verify they fail.
- [ ] 5.6 Implement the seeding of the initial version from the overlay theme; verify the 5.5 tests pass.
- [ ] 5.7 Write failing unit tests for the template preview with sample parameters in the web app, and that the preview uses the template's own parameter declarations; verify they fail.
- [ ] 5.8 Implement the template preview in the web app; verify the 5.7 tests pass and a component test renders the short 9:16 template with sample parameters.

## 6. Integration check

- [ ] 6.1 Run the integration suite tagged `integration` on a fresh database and verify publication, contrast refusal, the safe-zone refusal, pinning, notification, the render bundle export and the initial kit seeding work end to end.
- [ ] 6.2 Open the code pull request titled `feat(brand): add brand kit and layout templates`.

## Workflow follow-up

- Tick the completed boxes and archive the change in a follow-up pull request after the code pull request is merged.
