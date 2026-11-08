# Tasks

## 1. Workbench setup

- [ ] 1.1 Write a failing test that `pnpm build-storybook` exits 0 and writes `storybook-static/` from a minimal story; verify it fails for the expected reason (no Storybook configured).
- [ ] 1.2 Add Storybook 10 with `@storybook/vue3-vite` to `web/`, reuse the app's Vite config, theme tokens and German locale, add the `storybook` and `build-storybook` scripts, and add the light and dark mode toolbar; verify the 1.1 check passes.
- [ ] 1.3 Write a failing CI check that fails when `storybook-static/` or `.storybook/` appears in the production web image; verify it fails on an image that contains them.
- [ ] 1.4 Exclude Storybook from the production image in `web/Dockerfile` and `.dockerignore`; verify the 1.3 check passes.

## 2. Browser test and accessibility setup

- [ ] 2.1 Write a failing browser test in the Storybook Vitest addon that a story's `play` function runs in Chromium and that a failing assertion fails the run; verify it fails.
- [ ] 2.2 Add `@storybook/addon-vitest`, the Vitest browser mode and the Playwright provider with Chromium; verify the 2.1 test passes.
- [ ] 2.3 Write a failing test that an axe violation of impact serious fails a story and that a moderate violation is only reported; verify it fails.
- [ ] 2.4 Enable the accessibility addon with the impact rule of 2.3; verify the 2.3 tests pass.

## 3. Shared components, test first

- [ ] 3.1 Write failing browser tests for AppSelect: the option labels are visible with non-empty text, and the open menu does not overlap the text field placed below it; verify they fail.
- [ ] 3.2 Implement AppSelect with props, emitted events and no API import; verify the 3.1 tests pass.
- [ ] 3.3 Write failing browser tests for PageHeader at 1280 px and 390 px: the action's right edge lies within the viewport and the document has no horizontal scroll; verify they fail.
- [ ] 3.4 Implement PageHeader with a wrapping action area; verify the 3.3 tests pass.
- [ ] 3.5 Write failing story tests for each remaining component in the list of the spec (AppTextField, FilterBar, TagChips, StatusBadge, MetaList, CardGrid, DataTable, ConfirmInline, AppDialog, EmptyState, LoadState) covering the states the spec names, including a viewer and an editor variant where relevant; verify they fail.
- [ ] 3.6 Implement the remaining components with props, emitted events and no API import; verify the 3.5 tests pass.
- [ ] 3.7 Write a failing test that no file under `web/src/components/ui/` imports from the API layer; verify it fails when an import is added and passes on the implementation.

## 4. Test-only sign-in and startup guard

- [ ] 4.1 Write failing server tests that the test-only sign-in issues a `__Host-menura-session` cookie for a chosen role in environment `e2e`, that the same path answers 404 in environment `prod`, and that the logs contain no cookie value; use an injected clock and no network; verify they fail.
- [ ] 4.2 Implement the `@Requires(env = "e2e")` bean and its endpoint; verify the 4.1 tests pass.
- [ ] 4.3 Write a failing test that a startup with both `e2e` and `prod` active exits non-zero and names both environments; verify it fails.
- [ ] 4.4 Implement the startup check; verify the 4.3 test passes.

## 5. End-to-end harness

- [ ] 5.1 Write a failing Playwright test for sign-in per role (viewer, editor, publisher, operator, admin) that checks the navigation of each role; verify it fails.
- [ ] 5.2 Add Playwright with Chromium only in `web/e2e/`, the configuration with retries 0 locally and 1 in CI, trace on first retry and on failure, and a web server that starts the built app and the e2e server; verify the 5.1 test fails for the expected reason (no sign-in yet) and not for a configuration error.
- [ ] 5.3 Implement a fixture that starts the server in environment `e2e` with an in-memory or Testcontainers PostgreSQL 17, a fixed clock and fresh data per test; verify the 5.1 test passes.
- [ ] 5.4 Write failing Playwright tests for pool browse, filter and search, for planning a clip in the calendar on a fixed day, and for analytics tab navigation; verify they fail.
- [ ] 5.5 Implement the page steps those tests need through the shared components; verify the 5.4 tests pass.
- [ ] 5.6 Write a check that fails when a test source contains `waitForTimeout` or a fixed sleep; verify it fails on a sample and passes on the suite.

## 6. CI job

- [ ] 6.1 Write a failing check of the build workflow that a job runs the Storybook tests and the Playwright tests on pull requests and uploads the Playwright traces and the static workbench as artifacts; verify it fails on the current workflow.
- [ ] 6.2 Add the job to `.github/workflows/build.yml` with the Playwright browser cache keyed by version; verify the 6.1 check passes.
- [ ] 6.3 Run the complete workbench and end-to-end suites once in CI on the pull request under `flock ~/.cache/claude-heavy.lock` for local runs, and verify that a deliberately failing story blocks the merge, then revert the deliberate failure.
- [ ] 6.4 Open the code pull request titled `build(web): add storybook, shared ui components and end-to-end tests`.

## Workflow follow-up

- Tick the completed boxes and archive the change in a follow-up pull request after the code pull request is merged, as `docs(openspec): archive add-ui-workbench`.
