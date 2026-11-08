# Design

## Context

The web app (`web/`, Vue 3.5, Vite 8, TypeScript, Material Design 3 through `@material/web`, vue-router, Vitest, pnpm 10, Node 26) is served by nginx with `default-src 'self'` and a `script-src 'self'` policy. Its prototype has views of several hundred lines that mix layout, data loading and styling. Its unit tests run in jsdom, where layout, focus and overlay positioning do not exist, so the defects named in `proposal.md` could not be caught.

Constraints from the project context: principle 6 (the browser holds only the `__Host-` session cookie, never a token; sign-in is Entra ID through Menura's own authorization server), principle 9 (F.I.R.S.T.: injected clock, no sleeps, fixed seeds), principle 11 (secrets never in logs or output, 404 for hidden objects), principle 14 (no other origin or credentialed CORS), principle 16 (depends only on phase 0 changes).

## Goals / Non-Goals

**Goals:**
- A component is seen, operated and checked in isolation before a page uses it.
- The defects of the prototype fail a test before they reach a page: invisible select options, overlapping menus, overflowing header actions, and missing accessible names.
- A path through the real server, with real sessions and real roles, runs on every pull request.

**Non-Goals:**
- No migration of the prototype views (they are throw-away).
- No visual regression (pixel) snapshots in this change.
- No hosted service for stories or tests.
- No change to the production sign-in.

## Decisions

**Storybook 10 with `@storybook/vue3-vite`, sharing the app's Vite config.** Stories load the same `vite.config.ts` plugins, the same theme tokens (`web/src/theme/tokens.css`) and the same German locale as the app, so a component looks in Storybook as it looks in the app. Light and dark mode are switched by a toolbar global that sets the `data-theme` attribute already used by the tokens. Storybook is a development dependency; `build-storybook` writes `storybook-static/`, which CI uploads as an artifact and which the production Dockerfile never copies.
- Rejected: a separate Storybook Vite configuration. Two configurations drift, and a component would pass in Storybook and fail in the app for a reason no test shows.

**Interaction tests through the Storybook Vitest addon in Vitest browser mode.** Each story's `play` function drives the component (open the select, pick an option, measure the menu) and the assertions run in Chromium through the Playwright provider. The same Vitest 5 runner that runs the unit tests runs these, so there is one test runner for the web package.
- Rejected: the Storybook test-runner (`@storybook/test-runner`, Jest and Playwright underneath). It is a second runner with its own configuration and reporting. The Vitest addon reuses the runner and the coverage setup that the project already has.
- Rejected: jsdom for component behaviour. jsdom has no layout engine, so it cannot tell that a menu covers the next control or that a button leaves the viewport; the prototype defects are exactly of that kind. jsdom stays for pure logic (formatting, domain rules), which is unchanged.

**Accessibility through the a11y addon (axe).** The axe run is part of each story's test. Violations of impact `serious` or `critical` fail the test; `minor` and `moderate` are reported but do not fail, so the first version can be enforced without a backlog of fixes.

**Viewport checks in the same browser tests.** Stories that carry a page header set the viewport to 1280 px and 390 px in their tests and assert that the action element's bounding box lies within the viewport width, with no horizontal scroll on the document.

**End-to-end with Playwright, not Cypress.** Playwright runs the real browser against the built web app (served by `vite preview` in CI, with the same security headers as nginx where reachable) and the real server. It auto-waits on its assertions, so no test needs a sleep, and it records traces that can be opened after a failure. It runs Chromium only, which keeps the CI time bounded; Firefox and WebKit are a later decision.
- Rejected: Cypress. It runs in its own process model, is weaker for multiple tabs and cross-origin flows (the web app and API are on different hosts), and the project would then have two browser-test stacks, one of which is already chosen for the component tests.

**Real Entra ID login in E2E is rejected.** Signing in through Entra in CI needs a real tenant account, a stored password or a token, and a live identity provider; each test would depend on an external service and on an account whose roles change in another system. The test-only sign-in replaces only the upstream step; the session, the cookie, the role checks and the authorization code path of the server are the same code as in production.
- Rejected: stubbing the whole server in E2E. The flows that matter (roles, filters, persisted plans) depend on the server's real behaviour.

**Test-only sign-in limited to environment `e2e`.** A bean with `@Requires(env = "e2e")` exposes one endpoint that issues a normal `__Host-menura-session` cookie for a role the test chooses. Outside `e2e` the bean does not exist, so the route does not exist either. A startup check refuses to start when the active environments include both `e2e` and `prod`, so a misconfigured deployment fails at boot instead of serving the endpoint. The endpoint does not log the cookie value (principle 11).

**Fixed clock and fresh data per test.** The `e2e` environment injects a fixed `java.time.Clock` (set by an environment variable with a local default) so that calendar and analytics tests see the same "today". Each test creates its own data through the API and deletes it at the end, or starts from a freshly migrated database; no test reads data left by another test.
- Rejected: one shared seed for all tests. Tests then depend on each other's changes and on the order they run in (principle 9).

**PostgreSQL in E2E: in-memory or Testcontainers.** Locally the E2E run uses an in-memory PostgreSQL where the environment provides one, and CI uses a Testcontainers PostgreSQL 17 image matching the production major version (principle 1). Both are started per run, never shared with other jobs.

**Shared components are props-only.** Components under `web/src/components/ui/` receive their data as props, report user actions as emitted events and never import an API module. Pages (feature views) connect the two. This makes every component's stories complete without a server, and keeps the API contract in one place.

**Visual snapshot tests: postponed.** Pixel snapshots of a few key stories were considered as a Should. They are postponed to a later change, for three reasons: the font and theme rendering differ between Linux CI and the developers' machines, so every snapshot needs a pinned renderer in CI before it is reliable; a baseline has to be reviewed every time a token changes; and the layout defects that motivated this change are caught by the geometry assertions above. The decision is revisited once the component set has stabilised.
- Rejected for now: Chromatic and any other hosted visual testing service, because it sends rendered screenshots of stories to an external service (principle 1 and the team's data rules).

## Risks / Trade-offs

- [Risk] CI time grows with browser tests → Mitigation: Playwright browsers are cached by version; Storybook tests and E2E run as separate steps of one job so that a failure in one shows at once; the E2E suite is limited to the flows listed in the spec.
- [Risk] Flaky end-to-end tests → Mitigation: no sleeps, only Playwright auto-waiting and web-first assertions; retries are 0 locally and at most 1 in CI, and a test that needs the retry is reported as flaky in the run, not hidden.
- [Risk] Test-only sign-in reachable in production → Mitigation: the bean is absent unless the environment is `e2e`; the startup check refuses `e2e` with `prod`; a test asserts that the route answers 404 when the environment is `prod` and that the application does not start when both are active.
- [Risk] Test-only sign-in grants any role → Mitigation: it is only issued to a request inside the `e2e` environment, it is not in the production image's configuration, and the role is named explicitly in each test.
- [Risk] Storybook dependencies bloat the production image → Mitigation: Storybook is a development dependency and the production Dockerfile copies only the built app; a check in CI fails if `storybook-static/` or `.storybook/` appears in the image.
- [Risk] Accessibility rules block work on the prototype → Mitigation: only the new components are covered; the prototype views are not checked until they are replaced.
- [Trade-off] A second browser stack in CI (Chromium for Vitest browser mode and for Playwright E2E) → both use the same installed Chromium through the Playwright provider, so the browser is installed once per job.

## Migration Plan

No data changes. Storybook, the component layer and the test suites ship as one code change; the production application keeps its routes. Rollback reverts the change; no stored data is involved.

## Open Questions

- The server part (test-only sign-in and startup check) is in the server module but, as the brief fixes the header, ships under `build(web)`. If the maintainer prefers a server scope for it, that part becomes its own change, for example `test(server)`, before the code PR of this change; the front-end part keeps `build(web)`.
- Whether `vite preview` or the nginx image serves the built app in E2E. The default is `vite preview` with the same security headers as `web/nginx/security-headers.conf`, so the CSP is tested as deployed.
