# Design

## Context

Menura is one repository released as a whole (principle 7 and the project context). Its components differ in runtime:
a JVM server and jobs, a browser app, a Tauri desktop app, a native OBS plugin and a browser overlay. Principle 1 fixes
PostgreSQL 17 as the only database and S3 as the media store. Principle 8 fixes environment-variable configuration with
local defaults, and principle 9 fixes the test rules. The contract is the input every parallel change needs, so it must
exist before the endpoints do. See proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- A repository in which `./gradlew check` and the web checks run green on a fresh clone, with a health endpoint.
- A single contract location and conventions that every later endpoint follows.
- A schema home and migration path that the server and the jobs share.
- A chart that renders the three public hosts and nothing else that is not decided yet.

**Non-Goals:**
- Any product endpoint, entity or screen beyond health.
- Authentication (`add-access-control`), CI (`add-ci-pipeline`) and the production cluster wiring.
- Building the desktop, OBS plugin or overlay. Their directories are placeholders.

## Decisions

- **One Gradle build with `server`, `jobs` and `core`.** `server` and `jobs` depend on `core`; `jobs` never depends on
  `server`. Both ship as separate images from the same build, so a CronJob starts without the HTTP stack.
  Rejected: a separate repository per component (release-please would have to coordinate them). Rejected: jobs as a
  package inside `server` (every job would start the web container stack).
- **Flyway migrations in `core`.** Migrations are versioned SQL under `core/src/main/resources/db/migration`. The
  `jobs` entry `migrate` applies them; the chart runs it as a Helm hook before the server rolls out. In development the
  compose start runs the same command.
  Rejected: migrations inside `server` (the jobs would depend on the server's start order). Rejected: Liquibase (Flyway
  is the agreed tool and its SQL files are reviewable as they are).
- **`jobs` is one entry point with a named job.** `jobs run <name>` dispatches to a registered job; each Kubernetes
  CronJob sets one name. The skeleton registers `migrate` and a `ping` job that exits zero, so the wiring is testable.
  Rejected: one main class per job (one image per job and more build targets to keep in step).
- **Contract first.** `contracts/openapi/menura.yaml` is the source of truth. The server serves that file unchanged on the
  anonymous documentation path, and tests check that the health route in code matches the contract.
  Rejected: annotation-generated documents as the source (the contract would then follow the code, and parallel changes
  would wait for code before they can agree on an endpoint).
- **Identifiers are random version 4 UUIDs.** Generated from a secure random source through an injected generator.
  Rejected: UUIDv7 (it embeds the creation time, which the access-control rules forbid revealing), and database sequences
  (they reveal order and count).
- **Paging is cursor-based.** Requests take `limit` (default 50, maximum 200) and an opaque `cursor`; responses return
  `items` and `nextCursor`. Rejected: offset paging (rows shift between pages and large tables pay for skipped rows).
- **Problem Details for every error.** `application/problem+json` with `type`, `title`, `status`, `detail` and `instance`.
  Rejected: an ad-hoc error envelope (each change would invent its own shape).
- **Web: Vue 3, TypeScript, Vite and Material Web components with a Material Design 3 theme.** The theme tokens come from
  one seed colour in one file.
  Rejected: Vuetify (its layout system would become the design vocabulary of the app). Rejected: hand-written MD3 CSS
  (every token would need maintaining).
- **Dev database and object store in compose.** PostgreSQL 17 matches the production major version, with no extensions.
  The S3-compatible test container is Garage, which is Apache-2.0 licensed and speaks the S3 API including presigned URLs.
  Rejected: MinIO (its community container distribution has changed, so a pinned dev image is not reliable to depend on).
  The image tag is pinned and checked when the compose file is written (task 7.1).
- **Health is anonymous and reveals only status.** `GET /health` answers 200 with `status` and the database readiness,
  without versions, hostnames or configuration. The same route serves liveness and readiness probes. Rejected: the full
  management endpoint (it exposes configuration).
- **Chart attaches to the shared gateway.** Three HTTPRoutes, one per public host, attach to the shared Envoy Gateway
  listener named in values. Each route forwards to one Service. The routes are created here, and the CORS, cookie and TLS
  policies are added by `add-access-control`. Rejected: one route for all hosts (CORS and cookie rules differ per host).
- **Configuration through environment variables with local defaults.** Every setting has a default that points at the
  compose services. Rejected: a configuration file per environment (secrets would end up in files).

## Risks / Trade-offs

- [Risk] Micronaut 5 or Java 25 needs a version the build plugins do not yet support. → Mitigation: the versions are pinned
  in the version catalog, and the first build decides them before any module is added.
- [Risk] Garage behaves differently from Ceph RGW for multipart uploads. → Mitigation: the media change tests presigned
  multipart on Garage and on the real endpoint before relying on it.
- [Risk] Placeholders go stale while their changes wait. → Mitigation: each README names its change and phase.
- [Trade-off] Contract first means the contract changes before the code. Accepted: that is the point of parallel work.

## Open Questions

- Exact Micronaut 5 and Gradle plugin versions: pinned in the first build (task 2.2), recorded in the version catalog.
- Whether `core` should publish to the OLF Maven repository later. Not needed in this change.
