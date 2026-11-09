import type { Page } from '@playwright/test';
import { API, APP, expect, signInAs, test } from './fixtures';

// The content calendar against the real server. A fresh e2e environment has no connected channel (channels come from a
// platform's consent) and no finished asset (assets come from the upload), so the server answers the calendar with its
// empty window and refuses every planning step that needs them. The fixed e2e clock makes today 9 November 2026.

const rail = (page: Page) => page.getByRole('navigation', { name: 'Hauptnavigation' });

test('an editor opens the calendar from the rail and sees the month of the e2e clock', async ({ page }) => {
  await signInAs(page, 'editor');
  await expect(page.getByTestId('session-role')).toBeVisible();
  await rail(page).getByRole('link', { name: 'Kalender', exact: true }).click();

  await expect(page).toHaveURL(`${APP}kalender`);
  await expect(page.getByRole('heading', { level: 1, name: 'Kalender' })).toBeVisible();
  await expect(page.getByText('November 2026', { exact: true })).toBeVisible();
  await expect(page.getByRole('group', { name: 'Montag, 9. November 2026' })).toBeVisible();
});

test('an editor switches to the list view and filters by status, and the server receives the filter', async ({ page }) => {
  await signInAs(page, 'editor');
  await rail(page).getByRole('link', { name: 'Kalender', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Kalender' })).toBeVisible();

  await page.getByRole('button', { name: 'Liste' }).click();
  await expect(page.getByText('Im gewählten Zeitraum gibt es keine Beiträge.')).toBeVisible();

  const filtered = page.waitForRequest(
    (request) => request.url().startsWith(`${API}/calendar`) && new URL(request.url()).searchParams.get('status') === 'scheduled',
  );
  await page.getByRole('combobox', { name: 'Status' }).click();
  await page.getByRole('option', { name: 'Geplant' }).click();
  const query = new URL((await filtered).url()).searchParams;
  expect(query.get('view'), 'the list view is requested').toBe('list');
  expect(query.get('days'), 'a list covers the default days').toBe('31');
});

test('an editor opens the planning dialog and gets the empty-environment answers, with no channel and no clip', async ({
  page,
}) => {
  await signInAs(page, 'editor');
  await rail(page).getByRole('link', { name: 'Kalender', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Kalender' })).toBeVisible();

  await page.getByRole('button', { name: 'Beitrag planen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Beitrag planen' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('Kein Kanal ist verbunden. Verbinden Sie zuerst einen Kanal.')).toBeVisible();

  await dialog.getByRole('button', { name: 'Entwurf anlegen' }).click();
  await expect(dialog.getByRole('alert')).toHaveText('Wählen Sie einen fertigen Clip aus dem Pool.');
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(dialog).toBeHidden();
});

test('an editor creates the calendar feed address once, sees it, and blocks it again', async ({ page }) => {
  await signInAs(page, 'editor');
  await rail(page).getByRole('link', { name: 'Kalender', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Kalender' })).toBeVisible();

  await page.getByRole('button', { name: 'Kalender-Adresse erstellen' }).click();
  const address = page.getByRole('textbox', { name: 'Feed-Adresse' });
  await expect(address).toHaveValue(/\/calendar\/feed\/[^/]+\.[^/]+$/);
  await expect(page.getByText('Die Adresse wird nur jetzt angezeigt.')).toBeVisible();

  await page.getByRole('button', { name: 'Adresse sperren' }).click();
  await expect(address).toHaveCount(0);
});

test('a viewer reads the calendar without any planning control', async ({ page }) => {
  await signInAs(page, 'viewer');
  await rail(page).getByRole('link', { name: 'Kalender', exact: true }).click();

  await expect(page.getByRole('heading', { level: 1, name: 'Kalender' })).toBeVisible();
  await expect(page.getByRole('group', { name: 'Montag, 9. November 2026' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Beitrag planen' })).toHaveCount(0);
});

// The full path needs a connected channel and a finished asset in the e2e environment, and neither can be created there
// without a platform's consent or the object storage. Until the e2e environment can seed them, this stays a visible
// skip rather than a test that passes without the flow.
test.fixme('an editor plans a finished asset on a channel, the calendar shows it, a publisher approves and schedules it', async () => {
  // Plan on a connected channel, see the draft on 9 November, submit, approve and schedule as a publisher; the dry run
  // refuses a schedule without a real connection and the calendar shows its problem.
});
