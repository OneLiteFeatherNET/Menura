import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

// Shared components take their data as props and report user actions as events, so they never reach the API layer.
const uiDirectory = resolve(process.cwd(), 'src/components/ui');

function componentFiles(directory: string): string[] {
  return readdirSync(directory).flatMap((entry) => {
    const path = join(directory, entry);
    return statSync(path).isDirectory() ? componentFiles(path) : path.endsWith('.vue') || path.endsWith('.ts') ? [path] : [];
  });
}

/** An import or a dynamic import of anything under the API layer, however it is spelled. */
const API_IMPORT = /(?:from\s+|import\s*\(\s*|import\s+)['"][^'"]*(?:\/api\/|\/api['"]|api\/)[^'"]*['"]/;
const NETWORK_CALL = /\b(?:fetch|XMLHttpRequest|navigator\.sendBeacon)\s*\(/;

describe('shared ui components', () => {
  const files = componentFiles(uiDirectory);

  it('are present in src/components/ui', () => {
    expect(files.length, 'the component layer has files').toBeGreaterThan(0);
  });

  it('import nothing from the API layer', () => {
    const offenders = files.filter((file) => API_IMPORT.test(readFileSync(file, 'utf8')));
    expect(offenders, 'components must receive data as props').toEqual([]);
  });

  it('perform no network request themselves', () => {
    const offenders = files.filter((file) => NETWORK_CALL.test(readFileSync(file, 'utf8')));
    expect(offenders, 'components must report actions as events').toEqual([]);
  });
});
