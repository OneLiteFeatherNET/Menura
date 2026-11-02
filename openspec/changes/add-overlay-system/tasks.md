# Tasks

## 1. Central templates

- [ ] 1.1 Write failing tests for storing a template as module instances with position, size and configuration, and for rejecting an instance outside the canvas with a Problem Details error; verify they fail for the expected reason before implementing.
- [ ] 1.2 Implement template storage with non-sequential identifiers and the template read and write endpoints; verify the tests from 1.1 pass.
- [ ] 1.3 Write failing tests that only editor and admin roles can change a template (viewer gets 403) and that each change is audit-logged; verify they fail.
- [ ] 1.4 Implement role checks and the audit entry for template changes; verify the tests from 1.3 pass.

## 2. Personal variants and browser-source URLs

- [ ] 2.1 Write failing tests that each person gets a variant of every template that inherits the layout, that a layout change on the variant is refused with a Problem Details error, and that a central template move reaches a variant that did not override that position; verify they fail.
- [ ] 2.2 Implement variant creation, inheritance resolution at read time and the layout refusal; verify the tests from 2.1 pass.
- [ ] 2.3 Write failing tests that another person's variant answers 404 (also for editors and admins), that the template list contains no variant of another person, and that an operator cannot act on another person's variant; verify they fail.
- [ ] 2.4 Implement the 404 rule for variants and the template list filter; verify the tests from 2.3 pass.
- [ ] 2.5 Write failing tests that each variant gets its own browser-source URL, that a token of one variant is refused on another variant's feed with 401 or 403, that rotation by the owner's plugin makes the old token fail with 401, and that an admin revocation refuses the token; verify they fail.
- [ ] 2.6 Implement variant-scoped read-only tokens, rotation by the owner's OBS plugin, admin revocation and the rotation audit entry without the token value; verify the tests from 2.5 pass.
- [ ] 2.7 Write failing tests that scene tokens are absent from logs and error reports and that the overlay page response carries Referrer-Policy no-referrer; verify they fail.
- [ ] 2.8 Implement token redaction and the referrer header; verify the tests from 2.7 pass.
- [ ] 2.9 Document the variant browser-source setup for editors in the web app help text and verify the help text is shown on the template page with a mount test.

## 3. Module system

- [ ] 3.1 Write failing tests for manifest validation: a manifest without a version is refused with a Problem Details error naming the field; a template or variant with an invalid module configuration is rejected and left unchanged; verify they fail.
- [ ] 3.2 Implement the module manifest model, registration and configuration validation against the config schema; verify the tests from 3.1 pass.
- [ ] 3.3 Write failing tests that a module receives only data of its declared topics and that an undeclared action is refused with the module state unchanged; verify they fail.
- [ ] 3.4 Implement topic filtering per module and action dispatch limited to declared actions; verify the tests from 3.3 pass.
- [ ] 3.5 Write failing mount tests that each built-in module (chat rail, task board, follower goal, promo card, now-playing, limits and the other catalogue modules listed in design.md) renders from its manifest with a sample configuration; verify they fail.
- [ ] 3.6 Implement the built-in modules with their manifests; verify the mount tests from 3.5 pass.
- [ ] 3.7 Write failing tests for third-party modules: a registered module with a valid manifest is placed and rendered, and a removed module is no longer offered while the scene still renders without error; verify they fail.
- [ ] 3.8 Implement the module registry loading and placement; verify the tests from 3.7 pass.
- [ ] 3.9 Write the module manifest contract as docs/overlay-modules.md with an example manifest and verify the example passes the manifest validation test from 3.1.
- [ ] 3.10 (Could) Write a failing test that a third-party module in an isolated frame cannot read the scene token, then implement the isolation and verify the test passes; skip this task and note it in the pull request if the first delivery excludes it.

## 4. Live scene feed

- [ ] 4.1 Write failing tests with an injected clock and a fake event source that a new connection receives the current value of every topic first, that each browser source gets its own feed, and that private data is not sent; verify they fail.
- [ ] 4.2 Implement the per-variant feed with snapshot on connect, topic publishing and server-side filtering of private data; verify the tests from 4.1 pass.
- [ ] 4.3 Write failing overlay unit tests with a fake event source that after a reconnect the overlay replaces stale state with the snapshot and never shows the old value again; verify they fail.
- [ ] 4.4 Implement the overlay reconnect and resync from the snapshot; verify the tests from 4.3 pass.
- [ ] 4.5 Write an integration test (tagged `integration`) that a change made through the hub reaches a connected feed within 1 second; verify it passes in the integration suite.

## 5. Operator panel and live control

- [ ] 5.1 Write failing tests that an operator can switch a module off, show a promo card and hide a private area on own variant, that a viewer gets 403, and that each operator action is audit-logged with channel web; verify they fail.
- [ ] 5.2 Implement the operator commands on the hub and the operator panel in the web app; verify the tests from 5.1 pass.
- [ ] 5.3 Write failing tests that the privacy curtain blanks configured regions, that the regions stay blank until the curtain is removed, and that the action is audit-logged; verify they fail.
- [ ] 5.4 Implement the privacy curtain action and its panel control; verify the tests from 5.3 pass.
- [ ] 5.5 Write failing tests that a central brand kit change updates the accent colour of a live variant without reload, that a variant with an overridden accent keeps it, and that templates reference theme tokens rather than raw colours; verify they fail.
- [ ] 5.6 Implement overlay theme resolution from the brand kit overlay theme template and the owner's profile overrides, with the live update on a new version; verify the tests from 5.5 pass.

## 6. Overlay MCP tools

- [ ] 6.1 Write failing unit tests that an operator token reads the scene state of its own variant, and that the state contains no other person's variant; verify they fail first
- [ ] 6.2 Implement the scene state tool on the hub MCP server; verify the 6.1 tests pass
- [ ] 6.3 Write failing unit tests that an operator triggers the "show" action of a promo card on the own live variant, that the promo card is shown, and that the action is audit-logged via MCP; verify they fail first
- [ ] 6.4 Implement the module action tool with the operator role check and the audit entry; verify the 6.3 tests pass
- [ ] 6.5 Write failing unit tests that a token of an editor-only person is refused for a module action on a live variant with the refusal named, and that a module action on another person's variant answers 404; verify they fail first
- [ ] 6.6 Implement the role and ownership checks for the overlay tools; verify the 6.5 tests pass
- [ ] 6.7 Document the overlay MCP tools in the hub MCP documentation and verify each documented tool name exists in the tool registry test

## 7. Integration checks

- [ ] 7.1 Load a variant URL in a headless browser source and verify the layout, a live module change, the privacy curtain and a central brand change behave as specified; record the result in the pull request.
- [ ] 7.2 Run the overlay and hub test suites with the project's heavy-job lock and verify they pass.

## 8. Analytics: link posts to the overlay state

- [ ] 8.1 Write failing tests with an injected clock that an external post is linked in content analytics to the overlay state that was current when the post was scheduled; verify they fail
- [ ] 8.2 Record the current overlay state reference when a post is scheduled and expose it to content analytics; verify the tests from the previous task pass

## 9. Delivery

- [ ] 9.1 Write failing tests with an injected clock that an idle scene feed emits a heartbeat at least every 30 seconds, and verify they fail
- [ ] 9.2 Emit scene feed heartbeats, and verify the tests pass
- [ ] 9.3 Open the code pull request titled `feat(overlay): add overlay system`.
