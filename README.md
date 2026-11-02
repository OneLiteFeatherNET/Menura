# Menura

Menura is the content and stream hub of OneLiteFeather. It turns stream recordings into
searchable content, lets team members cut shorts and long videos with Claude on their own
GPU machines, distributes approved content natively to YouTube, Twitch, TikTok and Instagram,
and keeps branding and the modular stream overlay central for everyone.

The project is in its planning phase. Its principles live in
[`openspec/config.yaml`](openspec/config.yaml); every feature starts as an
[OpenSpec](https://github.com/Fission-AI/OpenSpec) proposal under `openspec/changes/`
before any code is written.

## Components

| Component | Runs on | Purpose |
|---|---|---|
| server | OLF Kubernetes cluster | REST API, web backend, sign-in, hub MCP, feeds, media job queue |
| jobs | OLF Kubernetes cluster | Distribution to platforms, analytics collection and rollups |
| web | any browser | Library, brand kit, overlays, approvals, content pipeline |
| obs-plugin | every streaming PC (Windows, macOS, Linux) | Sign-in, recording upload, scene and brand sync |
| desktop | PCs with a GPU | Transcription and local editing with Claude through a local MCP server |
| overlay | OBS browser sources | Modular stream overlay fed by the hub |

## Development

### Prerequisites

- JDK 25. Every Gradle module compiles with the Java 25 toolchain.
- Node.js 26 and pnpm 10.33.4. `corepack enable` provides the pinned pnpm.
- Docker with Compose v2, for the local database and object store and for the integration tests.
- Helm 3, [yq](https://github.com/mikefarah/yq) v4 and jq, for the deploy checks.

### Local database and object store

```sh
docker compose up -d --wait
```

This starts PostgreSQL 17 on `localhost:5432` (database, user and password `menura`) and a
Garage S3 node on `localhost:3900` with the development bucket `menura-dev`. The passwords and
keys in `docker-compose.yml` are public development values. Stop the stack with
`docker compose down`; add `-v` to delete the data volumes as well.

### Run the server

```sh
MICRONAUT_ENVIRONMENTS=local ./gradlew :server:run
```

The `local` environment applies the Flyway migrations at startup. Production leaves that to the
`migrate` job. The server listens on port 8080. In a second terminal:

```sh
curl -fsS localhost:8080/health
```

The response is `{"status":"UP","database":"UP"}` while the database is reachable.

The jobs entry point is `jobs run <name>`. To apply the migrations as production does:

```sh
./gradlew :jobs:run --args="run migrate"
```

### Build and test

```sh
./gradlew check
```

This runs the unit tests, the integration tests, the contract check and the repository layout
check (`verifyLayout`) for every module. The integration tests start PostgreSQL 17 containers
with Testcontainers, so Docker must be running. Use `--max-workers=2` on a shared machine.

Without Docker, skip the integration tests:

```sh
./gradlew test -PexcludeTags=integration
```

Run only the integration tests:

```sh
./gradlew test --tests '*IntegrationTest'
```

### Web app

```sh
cd web
pnpm install --frozen-lockfile
pnpm dev
```

The Vite dev server listens on `http://localhost:5173`. The theme tokens are generated from
`web/src/theme/seed.json` with `pnpm generate:theme`. The web checks are:

```sh
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

### Deploy checks

```sh
deploy/check-compose.sh
deploy/check-helm.sh
```

`check-compose.sh` parses `docker-compose.yml` and asserts pinned image tags and PostgreSQL 17.
`check-helm.sh` lints the Helm chart in `deploy/helm/menura`, renders it, and asserts the three
public hosts, the database settings, the migrate hook and the probes. Neither script needs a
running cluster or stack.

### OpenSpec

Changes live in `openspec/changes/<change-name>/`: a proposal, the design, the specs and the task
list. Finished changes move to `openspec/changes/archive/`. Requirements that are in force live
in `openspec/specs/`. The architecture and product principles that every change must respect are
in [`openspec/config.yaml`](openspec/config.yaml).

## License

[GNU Affero General Public License v3.0](LICENSE)
