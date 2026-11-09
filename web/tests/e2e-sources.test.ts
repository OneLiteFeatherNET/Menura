import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

// End-to-end tests wait on Playwright's web-first assertions only. A fixed wait is flakiness written down as code.
const e2eDirectory = resolve(process.cwd(), 'e2e');
const FIXED_WAIT = /waitForTimeout\s*\(|\bsetTimeout\s*\(|\bsleep\s*\(|\bThread\.sleep\s*\(/;

// The support directory holds the harness that starts the server and the seed, not tests. Its readiness loop polls a
// process that is starting, which is not a wait for a page, so it is outside this rule.
const HARNESS_DIRECTORY = join(e2eDirectory, 'support');

function sourceFiles(directory: string): string[] {
  return readdirSync(directory).flatMap((entry) => {
    const path = join(directory, entry);
    if (path === HARNESS_DIRECTORY) return [];
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return /\.(?:ts|js|mjs)$/.test(entry) ? [path] : [];
  });
}

describe('end-to-end test sources', () => {
  const files = sourceFiles(e2eDirectory);

  it('exist in web/e2e', () => {
    expect(files.length, 'the e2e suite has source files').toBeGreaterThan(0);
  });

  it('contain no fixed waits in place of Playwright assertions', () => {
    const offenders = files.filter((file) => FIXED_WAIT.test(readFileSync(file, 'utf8')));
    expect(offenders, 'use web-first assertions instead of fixed waits').toEqual([]);
  });
});
