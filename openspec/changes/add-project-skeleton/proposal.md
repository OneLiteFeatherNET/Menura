# Proposal

Conventional Commits: `build(repo)` (code PR). Phase: 0 (Foundation). Depends on: none.
Strand: platform.

## Why

Menura is one repository with five components, two lab prototypes to replace and a shared database. Changes that
run in parallel, such as the contract, the server, the web app and the desktop app, can only start once the repository
has one build, one schema home, one API contract with shared conventions and a deployable shape. Without this skeleton
every change would choose its own layout, and changes to identifiers, paging or errors would collide. This change adds
that skeleton and nothing that changes product behaviour beyond a health endpoint.

## What Changes

- Gradle multi-module build with a Java 25 toolchain and a version catalog: `server` (Micronaut 5), `jobs` (the same
  codebase as Kubernetes CronJob entry points) and `core` (shared primitives, the Problem Details model and the Flyway
  migrations).
- `web`: Vue 3 and TypeScript single-page app (Vite) with a Material Design 3 theme and an empty shell.
- Placeholders, each a directory with a README naming the change that fills it: `desktop` (Tauri 2), `obs-plugin`
  (CMake, C++) and `overlay` (Vue 3).
- Flyway baseline migration in `core`; the schema is applied by a `migrate` run of `jobs`, never by the server in
  production.
- OpenAPI-first contract `contracts/openapi/menura.yaml` containing only `GET /health` and the shared components
  (Problem Details, paging, identifier), and a conventions document: Problem Details (RFC 9457), version 4 UUID
  identifiers (RFC 9562), cursor paging, UTC timestamps in RFC 3339.
- Health endpoint `GET /health` on the server, anonymous, with the readiness check of the database.
- Helm chart `deploy/helm/menura` for the server, the web app and the jobs, with three HTTPRoutes on the shared Envoy
  Gateway: menura.onelitefeather.net (web), api-menura.onelitefeather.net (server) and mcp-menura.onelitefeather.net
  (server, MCP endpoint).
- docker-compose development environment with PostgreSQL 17 and an S3-compatible test container.
- README development guide: prerequisites, first start, tests, and where each component's contract lives.

## Capabilities

### New Capabilities

- None. This change has `skip_specs: true`: it creates the skeleton and the contract conventions; behaviour is specified
  by the changes that add endpoints.

### Modified Capabilities

- None (no existing capabilities).

## Impact

- Repository: new build files, module directories, contract, chart, compose file and README. The only runtime behaviour
  is the health endpoint.
- Other changes: they start from this layout and extend the contract and the migrations in `core`.
- Deployment: the chart is rendered and linted here but not installed; the Flux wiring lives in the cluster repository.
