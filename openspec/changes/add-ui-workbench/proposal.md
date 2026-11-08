# Proposal

Conventional Commits: `build(web)` (code PR). Phase: 1 (Pool, Calendar & Analytics). Depends on: `add-project-skeleton`, `add-ci-pipeline`, `add-access-control` (all phase 0).

## Why

The prototype of the web app works, but its defects are the kind that unit tests do not see. A select in the outlined style rendered its options empty, and its open menu covered the controls below it. A button in the page header ran past the right edge of the window. The unit tests passed, because they check component logic in a simulated DOM and never look at what an editor sees on screen.

The views were also built as large files without a shared set of parts, so the same select, filter or badge exists in several forms. Every new feature would copy one of them. The maintainer wants the interface split into small, reusable components, a place where each component can be seen and tested in isolation (Storybook), and a small set of end-to-end tests that drive the real app against the real server. This change sets up that workbench and the shared components, before the features of phase 1 are built on top of them.

## What Changes

- Establish Storybook 10 in the `web/` package, sharing the Vite configuration, theme tokens, light and dark mode and the German locale of the app. The static Storybook build is a CI artifact and is never part of the production web image.
- Add a shared component layer under `web/src/components/ui/`, for example a select, a text field, a filter bar, tag chips, a status badge, a meta list, a page header, a card grid, a data table, an inline confirmation, a dialog, an empty state and a load state. Each component takes props, emits events and makes no API calls. Each has stories for its states.
- Test the components' interaction in a real browser (Chromium) through the Storybook Vitest addon, and check accessibility with axe, failing on serious and critical violations. The first scenarios cover the defects named above: a select shows its option labels and its menu does not cover the controls that follow, and a page header action stays inside the viewport at 1280 px and 390 px.
- Add end-to-end tests with Playwright in `web/e2e/`, against the built web app and a real server started in a dedicated Micronaut environment `e2e`, with a PostgreSQL that is either in memory or started by Testcontainers. A test-only sign-in exists only in that environment and issues the normal session cookie for a chosen test role. The server refuses to start when `e2e` is combined with `prod`.
- Cover first the sign-in per role, browsing, filtering and searching the pool, planning a clip in the calendar, and switching tabs in analytics.
- Run Storybook tests and the Playwright tests in a job of the existing build workflow, on every pull request. Failures block the merge. Traces of failed end-to-end tests are kept as CI artifacts.
- Features built in later code changes use the shared components. The prototype views are not migrated by this change; they are throw-away.
- No breaking changes: the production application behaves as before, and the only new production-facing code is the shared component layer, which no production route uses yet.
- Not in this change: visual snapshot tests (decided against in `design.md`), hosted visual testing, any new feature screen, and any change to the authentication flow for real users.

## Capabilities

### New Capabilities
- `ui-workbench`: the Storybook workbench, the shared component layer and its stories, browser-based interaction and accessibility tests of the stories, the end-to-end test suite with its test-only sign-in, and the CI job that runs them.

### Modified Capabilities
- None.

## Impact

- Web: `web/package.json` scripts `storybook` and `build-storybook`, the Storybook and Vitest browser configuration, the components under `web/src/components/ui/`, and the Playwright suite under `web/e2e/`. The production build and the production web image do not include Storybook.
- Server: a test-only sign-in endpoint in the `e2e` environment only (bean `@Requires(env = "e2e")`) and a startup check that refuses `e2e` together with `prod`. The endpoint issues a normal session cookie, so the browser still holds only the `__Host-menura-session` cookie (principle 6).
- CI: one job in `.github/workflows/build.yml` for Storybook tests and Playwright tests (Chromium only), with cached browsers and uploaded traces.
- Security headers: unchanged. The Storybook build is not served by nginx; its tests run against the local dev server.
- Access: the e2e sign-in grants any role on request, so it must never be reachable outside environment `e2e` (see `design.md`, risks).
