# Tasks

## 1. Conventions and release configuration

- [ ] 1.1 Write a failing test script that runs commitlint against sample messages, accepting `feat(mcp): add hub mcp server` and refusing `Add hub` and `feat: add hub.`; verify it fails before the configuration exists
- [ ] 1.2 Implement `commitlint.config.mjs` with the Conventional Commits types of the convention and the scope rules, and verify the script of 1.1 passes
- [ ] 1.3 Write a failing check that `release-please-config.json` and `.release-please-manifest.json` parse, that the changelog sections are set, and that each extra file names an existing `x-release-please-version` marker; verify it fails
- [ ] 1.4 Implement the release-please configuration in simple mode, the manifest at the version in the root build file, and the markers in `build.gradle.kts`, `web/package.json` and `deploy/helm/menura/Chart.yaml`; verify the check of 1.3 passes

## 2. Pull request checks

- [ ] 2.1 Write a failing check, with actionlint and a script, that `build.yml` defines the Gradle, web, integration and chart jobs and that every reusable workflow it uses is pinned to a full SemVer tag; verify it fails before the workflow exists
- [ ] 2.2 Implement `build.yml` with the reusable Gradle build workflow (Temurin 25.0.3, tests on) for `server`, `jobs` and `core`, and a web job running lint, type check, unit tests and build; verify the check of 2.1 passes and a pull request runs both jobs green
- [ ] 2.3 Add the integration job that runs the tests tagged `integration` with Testcontainers after the unit tests; verify on a throwaway pull request that a failing integration test blocks the merge, then close that pull request
- [ ] 2.4 Implement `chart.yml` with `helm lint` and a render of the default values checked by kubeconform; verify it passes on the code pull request and fails on a deliberately broken template on a throwaway branch
- [ ] 2.5 Implement `pr-lint.yml` with the reusable pr-lint workflow; verify that a pull request titled `Add thing` fails and one titled `ci(github): add thing` passes
- [ ] 2.6 (Should) Implement `markdown-lint.yml` with the reusable markdown-lint workflow and a lychee exclusion list for generated files; verify it passes on the proposal's documentation

## 3. Release, images and SBOM

- [ ] 3.1 Write a failing check that no workflow starts on a tag push and that every publishing job is gated on `release_created` of the release-please job; verify it fails before `release-please.yml` exists
- [ ] 3.2 Implement `release-please.yml` with the reusable release-please workflow, the image jobs for `server`, `jobs` and `web` through the reusable docker-publish workflow with keyless cosign signing, all gated on `release_created`; confirm the Harbor project and robot account first, then verify the check of 3.1 passes and that a `feat:` commit on `main` opens a release pull request bumping the three marked files
- [ ] 3.3 Add the SBOM job that produces the CycloneDX inventory of the tag, attaches it to the release and sends it to Dependency-Track; confirm the Dependency-Track project first, then verify the release carries the SBOM asset and Dependency-Track shows the project
- [ ] 3.4 Verify after the first release that the three images exist in Harbor with signatures and that the release notes show the changelog sections

## 4. Security

- [ ] 4.1 Decide the repository visibility (see design.md), write a failing check that `security.yml` calls the reusable security scan with the vulnerability and secret scanners in report-only mode and runs weekly; verify it fails before the workflow exists
- [ ] 4.2 Implement `security.yml` with the trigger and upload settings that match the visibility decision, verify the check of 4.1 passes, and verify the scan runs green on `main`
- [ ] 4.3 Configure the repository settings: squash merges only, branches deleted after merge, and branch protection on `main` requiring the build and pr-lint checks; verify the settings with the GitHub API

## 5. Renovate

- [ ] 5.1 Confirm the Renovate maintainer team (see design.md), write a failing check that `renovate.json` extends the central preset with that team; verify it fails before the file exists, implement `renovate.json`, and verify the check passes with the Renovate configuration validator
- [ ] 5.2 Verify that Renovate opens its onboarding or dependency dashboard, and that a bump of a reusable workflow pin opens a pull request that runs the build workflow

## 6. Delivery

- [ ] 6.1 Verify on the code pull request itself that the build, pr-lint, chart and security checks run and pass, and record the run links in the pull request
- [ ] 6.2 Open the code pull request titled `ci(github): add ci pipeline`
