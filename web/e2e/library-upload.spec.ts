import { randomUUID } from 'node:crypto';
import { mkdtempSync, truncateSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { API, APP, expect, signInAs, test } from './fixtures';

// The upload of a finished video from the pool page. The e2e environment has no object storage, so the upload
// session calls and the presigned part PUT are answered by page.route, the way the server and S3 would answer: the
// storage checks the part checksum header and answers with an ETag and the echoed checksum. Everything else (sign-in,
// the pool listing) runs against the real server.
//
// The file is one part. Routing a 95 MB request body through Playwright's interception overflows its string buffer,
// so multi-part behaviour (part splitting, per-part checksums, resume) is covered by the unit tests instead.

const STORAGE = 'http://storage.menura.test';
const FILE_SIZE = 1024 * 1024;
const SESSION = randomUUID();
const ASSET = randomUUID();
const EXISTING_ASSET = randomUUID();
const CHECKSUM_PATTERN = /^[A-Za-z0-9+/]{43}=-1$/;

// The API answers credentialed requests of the web app only; the storage answers the presigned PUT, which carries no
// credentials, so its origin is open and nothing else is.
function apiCors() {
  return {
    'Access-Control-Allow-Origin': APP.replace(/\/$/, ''),
    'Access-Control-Allow-Credentials': 'true',
    'Access-Control-Allow-Headers': 'content-type, accept',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  };
}

const storageCors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'x-amz-checksum-sha256',
  'Access-Control-Allow-Methods': 'PUT, OPTIONS',
  'Access-Control-Expose-Headers': 'ETag',
};

function sessionView(overrides: Record<string, unknown>) {
  return {
    id: SESSION,
    state: 'uploading',
    kind: 'video',
    format: 'mp4',
    durationSeconds: 120,
    width: 1280,
    height: 720,
    sizeBytes: FILE_SIZE,
    checksum: '',
    partSizeBytes: FILE_SIZE,
    partCount: 1,
    streamTitle: null,
    streamDate: null,
    duplicateOf: null,
    assetId: null,
    failureReason: null,
    confirmedParts: [],
    nextPart: 1,
    parts: [
      {
        partNumber: 1,
        sizeBytes: FILE_SIZE,
        url: `${STORAGE}/menura/${SESSION}/part/1`,
        expiresAt: '2030-01-01T00:00:00Z',
      },
    ],
    createdAt: '2026-11-08T12:00:00Z',
    updatedAt: '2026-11-08T12:00:00Z',
    ...overrides,
  };
}

test('an editor uploads a video from the pool, confirms the duplicate and sees it completed', async ({ page }) => {
  const workDir = mkdtempSync(join(tmpdir(), 'menura-upload-'));
  const file = join(workDir, 'render.mp4');
  writeFileSync(file, '');
  truncateSync(file, FILE_SIZE);

  const storedParts: number[] = [];
  let checksum = '';
  let flaggedStart = true;

  // The API calls of the upload, answered in the order the flow makes them.
  await page.route(new RegExp(`^${API}/asset-upload-sessions`), async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: apiCors() });
      return;
    }
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, headers: { ...apiCors(), 'content-type': 'application/json' }, body: JSON.stringify(body) });

    if (request.method() === 'POST' && path === '/asset-upload-sessions') {
      const body = request.postDataJSON() as { checksum: string; sizeBytes: number };
      checksum = body.checksum;
      expect(body.checksum, 'the declared checksum is the composite of one part').toMatch(CHECKSUM_PATTERN);
      expect(body.sizeBytes).toBe(FILE_SIZE);
      if (flaggedStart) {
        await json(sessionView({ state: 'flagged', checksum, parts: [], nextPart: null, duplicateOf: EXISTING_ASSET }), 201);
        return;
      }
      await json(sessionView({ checksum }), 201);
      return;
    }
    if (request.method() === 'POST' && path === `/asset-upload-sessions/${SESSION}/duplicate-confirmation`) {
      flaggedStart = false;
      await json(sessionView({ checksum }));
      return;
    }
    if (request.method() === 'POST' && path === `/asset-upload-sessions/${SESSION}/complete`) {
      await json(sessionView({ checksum, state: 'available', assetId: ASSET, confirmedParts: [1], nextPart: null, parts: [] }));
      return;
    }
    await json({ type: 'about:blank', title: 'Not Found', status: 404, detail: 'x', instance: path }, 404);
  });

  // The presigned part PUT. The part body is not read (see the note above); the checksum header must be a base64
  // SHA-256, and the storage echoes it back as S3 does.
  await page.route(`${STORAGE}/**`, async (route) => {
    const request = route.request();
    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: storageCors });
      return;
    }
    const partNumber = Number(new URL(request.url()).pathname.split('/').pop());
    const declared = request.headers()['x-amz-checksum-sha256'] ?? '';
    if (!/^[A-Za-z0-9+/]{43}=$/.test(declared)) {
      await route.fulfill({ status: 400, headers: storageCors, body: '<Error><Code>InvalidDigest</Code></Error>' });
      return;
    }
    storedParts.push(partNumber);
    await route.fulfill({
      status: 200,
      headers: { ...storageCors, ETag: `"etag-${partNumber}"`, 'x-amz-checksum-sha256': declared },
      body: '',
    });
  });

  await signInAs(page, 'editor');
  await expect(page.getByTestId('session-role')).toBeVisible();
  await page.getByRole('navigation', { name: 'Hauptnavigation' }).getByRole('link', { name: 'Pool', exact: true }).click();
  await expect(page.getByRole('table', { name: 'Content-Pool' })).toBeVisible();

  await page.getByRole('button', { name: 'Hochladen' }).first().click();
  const dialog = page.getByRole('dialog', { name: 'Hochladen' });
  await expect(dialog).toBeVisible();

  // The browser cannot read the zero-filled file's media headers, so the editor declares the facts by hand.
  await dialog.getByLabel('Datei').setInputFiles(file);
  await expect(dialog.getByText('Gewählt: render.mp4')).toBeVisible();
  await dialog.getByLabel('Länge in Sekunden').fill('120');
  await dialog.getByLabel('Breite in Pixeln').fill('1280');
  await dialog.getByLabel('Höhe in Pixeln').fill('720');
  await dialog.getByRole('button', { name: 'Hochladen' }).click();

  // The checksum matches an earlier upload of this editor, so the upload waits for the confirmation.
  await expect(dialog.getByRole('alert')).toContainText('Diese Datei wurde schon hochgeladen');
  const refreshed = page.waitForRequest((request) => request.url().startsWith(`${API}/library/items`) && request.method() === 'GET');
  await dialog.getByRole('button', { name: 'Trotzdem hochladen' }).click();

  await expect(dialog.getByText('Der Upload ist abgeschlossen. Die Datei steht jetzt im Pool.')).toBeVisible();
  expect(storedParts, 'the part reached the storage with its checksum').toEqual([1]);

  await dialog.getByRole('button', { name: 'Fertig' }).click();
  await expect(dialog).toBeHidden();
  await refreshed;
  await expect(page.getByRole('table', { name: 'Content-Pool' })).toBeVisible();
});
