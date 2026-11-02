# Proposal

Conventional Commits: `ci(github)` (code PR). Phase: 0 (Foundation). Depends on: `add-project-skeleton`.
Strand: platform.

## Why

Menura's repository will hold a JVM server and jobs, a Vue web app, desktop and OBS placeholders and a Helm chart. Without
a pipeline the first changes would merge unbuilt, and releases, images and dependency updates would be done by hand. The
OneLiteFeather organisation already runs the same stack in its other repositories (Quelea and Sturnus are the reference
layouts). Adopting the organisation's reusable workflows keeps Menura on the same release, security and registry rules
instead of inventing a second set.

## What Changes

- Pull requests: build and test of `server`, `jobs` and `core` through the organisation's reusable Gradle build workflow
  (Temurin 25, tests on), the web checks (lint, type check, unit tests, build), the contract and chart checks, and the
  integration tests tagged `integration` against Testcontainers.
- Pull request titles and commits are checked as Conventional Commits (reusable pr-lint, with `commitlint.config.mjs`).
- release-please on `main` creates one release for the whole repository from the Conventional Commit history, with
  changelog sections per type. Image publishing, SBOM upload and release assets run in the same workflow run, gated on
  `release_created`.
- Container images for `server`, `jobs` and `web` are pushed to the OLF registry (Harbor) with keyless cosign signatures.
- A CycloneDX SBOM is produced at release, attached to the release and sent to Dependency-Track.
- Security: a Trivy scan (vulnerabilities and secrets) that is report-only at first, and a weekly scheduled run.
- Renovate: the repository extends the central OneLiteFeatherNET preset and keeps the reusable workflow pins current.
- Helm chart lint and render checks on pull requests.
- Repository settings: squash merges only, and the build and pr-lint checks are required for `main`.

## Capabilities

### New Capabilities

- None. This change has `skip_specs: true`: it adds workflows and repository configuration, not product behaviour.

### Modified Capabilities

- None (no existing capabilities).

## Impact

- Repository: `.github/workflows/` (build, pr-lint, release-please, security, chart, markdown-lint), `renovate.json`,
  `release-please-config.json`, `.release-please-manifest.json`, `commitlint.config.mjs`.
- Organisation: secrets for the registry and Dependency-Track, and a Renovate maintainer team for this repository.
- Releases: the first release after this change is the baseline for the release-please manifest.
