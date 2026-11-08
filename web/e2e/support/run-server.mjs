// Starts the Menura server in environment e2e against a PostgreSQL 17 container of Testcontainers, and stops both
// when Playwright stops this process. The server runs the local profile too, so Flyway creates the schema.
import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { PostgreSqlContainer } from '@testcontainers/postgresql';

const root = resolve(import.meta.dirname, '../../..');
const launcher = resolve(root, 'server/build/install/server/bin/server');

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

async function stop(code) {
  server.kill('SIGTERM');
  await container.stop();
  process.exit(code);
}

process.on('SIGINT', () => stop(130));
process.on('SIGTERM', () => stop(143));
server.on('exit', (code) => stop(code ?? 1));
