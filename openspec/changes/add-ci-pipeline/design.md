# Design

## Context

The organisation standardises build, release, security and dependency handling through reusable workflows in
`OneLiteFeatherNET/workflows`, pinned to full version tags, plus a central Renovate preset. Quelea (Gradle, Micronaut, Java
25) and Sturnus (Python, several images) show the working layout in this organisation. Principle 7 and the project context
state that commits and pull request titles follow Conventional Commits and that release-please drives the release. The
repository is trunk-based: short-lived branches, squash merges, one release line from `main`. See proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- A pull request cannot merge without a green build of every component that has code, a valid title and a valid contract.
- A release is produced only from `main` by release-please, with its images, signatures and SBOM from the same run.
- Security and dependency updates use the organisation's configuration, not a local variant.

**Non-Goals:**
- Deployment. Flux applies the chart from the cluster repository; this pipeline only renders and lints it.
- Building the desktop app, the OBS plugin and the overlay. They are placeholders until their changes add code.
- Coverage reporting and performance tests.

## Decisions

- **Reusable workflows pinned to full version tags.** Each `uses:` names a full SemVer tag (`@v2.9.1` at the time of
  writing; the tag current at apply time is used). Renovate proposes the bumps. Rejected: `@main` (changes under us) and
  bare major aliases (silently move). Rejected: copying the workflow bodies into the repository (drift from the
  organisation's fixes).
- **One workflow file per concern, as in Quelea and Sturnus.** `build.yml` for pull requests, `pr-lint.yml`,
  `release-please.yml` (release and the publishing jobs it gates), `security.yml`, `chart.yml`, `markdown-lint.yml`.
  Rejected: one monolithic workflow (a failing security scan would then hold back a merge, and the permissions of each
  concern could not be scoped separately).
- **Release-please in simple mode with extra files.** The version lives in the root `build.gradle.kts`, `web/package.json`
  and `deploy/helm/menura/Chart.yaml`, each behind a `x-release-please-version` marker. The manifest starts at the
  version of the first release. Rejected: release-please per component (the components ship together, as principle 7
  states).
- **Publishing inside the release run.** Images, SBOM and release assets depend on the `release_created` output of the same
  run. Tags created with `GITHUB_TOKEN` do not start `on: push: tags` workflows, so no separate tag workflow exists.
  Rejected: a workflow triggered by the release tag (it never starts).
- **Images through the organisation's docker-publish workflow.** `onelitefeather/menura-server`, `onelitefeather/menura-jobs`
  and `onelitefeather/menura-web` go to Harbor with keyless cosign signatures from the run's OIDC token. `server` and `jobs`
  build their optimised Docker context with Gradle, as Quelea does; `web` builds from its own Dockerfile.
  Rejected: a Docker build inside the Gradle task (the reusable workflow already handles registry login, signing and retries).
- **Tests on every pull request, including integration tests.** The Testcontainers suite runs after the unit suite in the
  same job; a failing integration test blocks the merge. Rejected: integration tests on the weekly schedule only (a
  regression would reach `main` unseen).
- **Security scan report-only at first.** Trivy runs the vulnerability and secret scanners and uploads SARIF only when code
  scanning is available for the repository. Failing on findings is switched on after the first clean baseline.
  Rejected: failing from day one (the baseline would block every pull request before anyone has triaged it).
- **SBOM at release, not per pull request.** The CycloneDX inventory describes the release; it is attached to the release
  and sent to Dependency-Track by the organisation's SBOM workflow.
- **Renovate through the central preset.** `renovate.json` extends `github>OneLiteFeatherNET/renovate:default(OneLiteFeatherNET/menura-maintainers)`
  and adds no custom rules unless a package needs one. Rejected: a local configuration with its own rules (the preset is
  where the organisation keeps them).
- **Conventional Commits enforced twice.** The pull request title is the squash commit, so it is linted; every commit on the
  branch is linted as well, because release-please reads commits, not titles. The rule set is `commitlint.config.mjs`; the
  types are those of the global convention, and the changelog shows `feat`, `fix`, `perf`, `revert` and `docs`.

## Risks / Trade-offs

- [Risk] The repository is private, so code scanning upload and some reusable workflow triggers are unavailable. →
  Mitigation: the workflows detect the visibility and fall back to `workflow_dispatch` plus logs, as the organisation does for
  private repositories (see Open Questions).
- [Risk] The release-please bootstrap version is wrong and the first release jumps. → Mitigation: the manifest is written
  from the version in the root build file at the time this change merges.
- [Risk] Renovate's team does not exist for this repository. → Mitigation: the team is created as part of this change
  (`menura-maintainers`) or the preset's existing team is reused; see Open Questions.
- [Risk] A reusable workflow tag is moved or broken upstream. → Mitigation: full tags only; Renovate shows each bump as a
  pull request that runs this pipeline.
- [Trade-off] Integration tests on every pull request make the pipeline slower. Accepted: the suite is small in this phase,
  and the alternative lets regressions reach `main`.

## Open Questions

- Repository visibility: public repositories get code scanning uploads and the pull-request triggers; private ones do not.
  Decide before task 4.1.
- Renovate maintainer team: `menura-maintainers` (following the organisation's naming) must exist, or an existing team must
  be named in the preset parameters. Decide before task 5.1.
- Dependency-Track project for Menura: the SBOM job needs a project name and the organisation secret. Decide before task 3.3.
- Harbor project and robot account for `onelitefeather/menura-*`: confirm the project exists before task 3.2.
