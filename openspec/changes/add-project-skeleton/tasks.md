# Tasks

## 1. Repository layout

- [ ] 1.1 Write a failing layout check, a Gradle task `verifyLayout` in the root build, that asserts the modules `server`, `jobs` and `core` are included and that the directories `web`, `desktop`, `obs-plugin`, `overlay`, `contracts/openapi`, `deploy/helm/menura` and the file `docker-compose.yml` exist; verify it fails before the skeleton exists
- [ ] 1.2 Implement the root Gradle settings, the empty `core`, `server` and `jobs` modules, the Java 25 toolchain convention and the placeholder directories with a README each naming the change that fills it; verify the check of 1.1 passes

## 2. Server and health

- [ ] 2.1 Write failing tests (Micronaut test with a fresh context per test) that `GET /health` answers 200 without credentials with `status` and the database readiness only, with no version, host or configuration field, and that readiness reports down when the database is unreachable (fake datasource); verify they fail
- [ ] 2.2 Implement the Micronaut 5 server with the health endpoint, and pin the Micronaut, Gradle plugin and library versions in the version catalog; verify the tests of 2.1 pass
- [ ] 2.3 Write failing tests that every setting has a local default and that an environment variable overrides it, using an injected environment map; verify they fail
- [ ] 2.4 Implement environment-variable configuration with local-dev defaults that point at the compose services; verify the tests of 2.3 pass
- [ ] 2.5 Write failing tests that the server serves `contracts/openapi/menura.yaml` unchanged on the anonymous documentation path and that the health route in code matches the contract; verify they fail
- [ ] 2.6 Implement the documentation route that serves the contract file, and verify the tests of 2.5 pass

## 3. Shared primitives in core

- [ ] 3.1 Write failing unit tests that a Problem Details body carries `type`, `title`, `status`, `detail` and `instance` and is sent as `application/problem+json`; verify they fail
- [ ] 3.2 Implement the Problem Details model and the exception mapping in `core` and `server`, and verify the tests of 3.1 pass
- [ ] 3.3 Write failing unit tests that the identifier generator yields RFC 9562 version 4 UUIDs from an injected secure random source with no time component, that cursor paging defaults to 50 and clamps to a maximum of 200, and that an invalid cursor is refused as a Problem Details error; verify they fail
- [ ] 3.4 Implement the identifier generator and the paging primitives in `core`, and verify the tests of 3.3 pass

## 4. Jobs entry point

- [ ] 4.1 Write failing tests that `jobs run ping` exits 0, that `jobs run` with an unknown name exits non-zero and names the job, and that running without a name prints usage and exits non-zero; verify they fail
- [ ] 4.2 Implement the `jobs` dispatcher with the `ping` job, and verify the tests of 4.1 pass

## 5. Database migrations

- [ ] 5.1 Write a failing integration test, tagged `integration`, with Testcontainers PostgreSQL 17, that `jobs run migrate` applies the baseline to an empty database and that a second run changes nothing; verify it fails
- [ ] 5.2 Implement Flyway in `core` with the baseline migration `V1__baseline.sql` (creates the `menura` schema only) and the `migrate` job, and verify the test of 5.1 passes
- [ ] 5.3 Write a failing unit test that the server does not run Flyway at startup unless the local profile enables it, and verify it fails; then implement the switch and verify the test passes

## 6. Web application

- [ ] 6.1 Write failing Vitest tests that the app mounts its shell and that the Material Design 3 theme tokens are generated from the single seed colour file; verify they fail
- [ ] 6.2 Implement the Vue 3 and TypeScript app with Vite, the Material Web components and the theme from the seed colour file, and verify the tests of 6.1 pass

## 7. Development environment

- [ ] 7.1 Write a failing check that `docker compose config --quiet` passes and that no service uses a `latest` tag and that the PostgreSQL image is version 17; verify it fails before the compose file exists
- [ ] 7.2 Implement `docker-compose.yml` with PostgreSQL 17 and Garage as the S3-compatible test container, with pinned tags, health checks and a bucket created at start; verify the check of 7.1 passes
- [ ] 7.3 Under the heavy-job lock, run `docker compose up -d --wait`, verify `GET /health` answers 200 against the local server, then run `docker compose down`

## 8. Contract

- [ ] 8.1 Write a failing contract check, part of `./gradlew check`, that lints `contracts/openapi/menura.yaml` and asserts the shared components (Problem Details, paging envelope, identifier) and the `GET /health` operation exist; verify it fails before the file exists
- [ ] 8.2 Implement `contracts/openapi/menura.yaml` with `GET /health` and the shared components, and the conventions document in `contracts/openapi/CONVENTIONS.md` covering Problem Details, version 4 UUIDs, cursor paging and RFC 3339 UTC timestamps; verify the check of 8.1 passes

## 9. Helm chart

- [ ] 9.1 Write a failing check, `helm lint` plus a rendered-manifest assertion, that the chart renders exactly three HTTPRoutes with the hosts menura.onelitefeather.net, api-menura.onelitefeather.net and mcp-menura.onelitefeather.net and no other host; verify it fails before the chart exists
- [ ] 9.2 Implement the chart `deploy/helm/menura` with Deployments and Services for the server, the web app and the jobs, the `migrate` Helm hook, the three HTTPRoutes attached to the shared gateway listener from values, and verify the check of 9.1 passes

## 10. Developer guide and delivery

- [ ] 10.1 Write the README development guide and verify each command in it runs on a fresh clone (build, tests, compose start, web dev server)
- [ ] 10.2 Under the heavy-job lock, run `./gradlew check` and the web checks, and verify they pass
- [ ] 10.3 Open the code pull request titled `build(repo): add project skeleton`
