// Starts the Menura server in environment e2e against a PostgreSQL 17 container of Testcontainers, and stops both
// when Playwright stops this process. The server runs the local profile too, so Flyway creates the schema. After the
// server answers its health check, the test-only seed (seed.sql) is applied; only then does this process answer on the
// readiness port, which the Playwright configuration waits for, so no test starts before the seed is in place.
import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { existsSync } from 'node:fs';
import { createServer } from 'node:http';
import { resolve } from 'node:path';
import { PostgreSqlContainer } from '@testcontainers/postgresql';

const root = resolve(import.meta.dirname, '../../..');
const launcher = resolve(root, 'server/build/install/server/bin/server');
const seedFile = resolve(import.meta.dirname, 'seed.sql');
const READY_PORT = 8081;
const SEED_TARGET = '/tmp/e2e-seed.sql';

if (!existsSync(launcher)) {
  console.error(`The server is not installed at ${launcher}. Run ./gradlew :server:installDist first.`);
  process.exit(1);
}

const container = await new PostgreSqlContainer('postgres:17').start();
// The schema of the migrations is menura; production sets it on the database role, the e2e URL sets it instead.
const database = `jdbc:postgresql://${container.getHost()}:${container.getMappedPort(5432)}/${container.getDatabase()}?currentSchema=menura`;

const server = spawn(launcher, {
  stdio: 'inherit',
  env: {
    ...process.env,
    MICRONAUT_ENVIRONMENTS: 'e2e,local',
    MENURA_DB_URL: database,
    MENURA_DB_USER: container.getUsername(),
    MENURA_DB_PASSWORD: container.getPassword(),
    // The server refuses to start without a token key (channel connections); a fresh key per run, never stored.
    MENURA_TOKEN_ENCRYPTION_KEY: randomBytes(32).toString('base64'),
    MENURA_WEB_ORIGIN: 'http://localhost:4173',
    MENURA_AUTH_WEB_APP_URL: 'http://localhost:4173/',
  },
});

let readiness = null;

async function stop(code) {
  readiness?.close();
  server.kill('SIGTERM');
  await container.stop();
  process.exit(code);
}

process.on('SIGINT', () => stop(130));
process.on('SIGTERM', () => stop(143));
server.on('exit', (code) => stop(code ?? 1));

async function waitUntil(description, check, attempts, delayMs) {
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    if (await check()) {
      return;
    }
    await new Promise((done) => setTimeout(done, delayMs));
  }
  throw new Error(`${description} did not happen in time`);
}

async function prepare() {
  // The server answers its health check once the database is reachable and the migrations have run.
  await waitUntil('the server health check', async () => {
    try {
      return (await fetch('http://localhost:8080/health')).ok;
    } catch {
      return false;
    }
  }, 300, 1000);

  // The seed is idempotent. A run before the schema is complete fails and is retried; the last failure is reported.
  await container.copyFilesToContainer([{ source: seedFile, target: SEED_TARGET }]);
  let seeded = null;
  try {
    await waitUntil('the seed of the e2e environment', async () => {
      seeded = await container.exec(['psql', '-v', 'ON_ERROR_STOP=1', '-1', '-U', container.getUsername(),
        '-d', container.getDatabase(), '-f', SEED_TARGET]);
      return seeded.exitCode === 0;
    }, 120, 1000);
  } catch (failure) {
    console.error(seeded?.output ?? failure);
    throw failure;
  }

  readiness = createServer((request, response) => {
    response.writeHead(request.url === '/ready' ? 200 : 404);
    response.end();
  }).listen(READY_PORT);
}

prepare().catch(async (failure) => {
  console.error(failure);
  await stop(1);
});
