import { randomUUID } from 'node:crypto';
import type { Page } from '@playwright/test';
import { API, APP, SEEDED_ASSET_LABEL, SEEDED_CHANNEL, expect, signInAs, test } from './fixtures';

// The content calendar against the real server. The test-only seed (web/e2e/support/seed.sql) adds one connected channel
// and one registered finished asset; no platform is contacted, and the fixed e2e clock makes today 9 November 2026.

const rail = (page: Page) => page.getByRole('navigation', { name: 'Hauptnavigation' });
const panel = (page: Page) => page.getByRole('complementary', { name: 'Beitrag und Kalender-Abo' });
// The notices of the page and of the side panel are both role=status; each is found by its own text.
const notice = (page: Page, text: string) => page.getByRole('status').filter({ hasText: text });

async function openCalendar(page: Page) {
  await rail(page).getByRole('link', { name: 'Kalender', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Kalender' })).toBeVisible();
}

test('an editor opens the calendar from the rail and sees the month of the e2e clock', async ({ page }) => {
  await signInAs(page, 'editor');
  await expect(page.getByTestId('session-role')).toBeVisible();
  await rail(page).getByRole('link', { name: 'Kalender', exact: true }).click();

  await expect(page).toHaveURL(`${APP}kalender`);
  await expect(page.getByRole('heading', { level: 1, name: 'Kalender' })).toBeVisible();
  await expect(page.getByText('November 2026', { exact: true })).toBeVisible();
  await expect(page.getByRole('group', { name: 'Montag, 9. November 2026' })).toBeVisible();
});

test('an editor switches to the list view and filters by a status without posts, and the server receives the filter', async ({
  page,
}) => {
  await signInAs(page, 'editor');
  await openCalendar(page);

  await page.getByRole('button', { name: 'Liste' }).click();
  // No e2e test publishes a post, so the published filter stays empty whatever the other tests have planned.
  const filtered = page.waitForRequest(
    (request) => request.url().startsWith(`${API}/calendar`) && new URL(request.url()).searchParams.get('status') === 'published',
  );
  await page.getByRole('combobox', { name: 'Status' }).click();
  await page.getByRole('option', { name: 'Veröffentlicht' }).click();
  const query = new URL((await filtered).url()).searchParams;
  expect(query.get('view'), 'the list view is requested').toBe('list');
  expect(query.get('days'), 'a list covers the default days').toBe('31');
  await expect(page.getByText('Im gewählten Zeitraum gibt es keine Beiträge.')).toBeVisible();
});

test('an editor opens the planning dialog and sees the seeded clip and the seeded channel as choices', async ({ page }) => {
  await signInAs(page, 'editor');
  await openCalendar(page);

  await page.getByRole('button', { name: 'Beitrag planen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Beitrag planen' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('checkbox', { name: SEEDED_CHANNEL })).toBeVisible();

  await dialog.getByRole('combobox', { name: 'Fertiger Clip' }).click();
  await expect(page.getByRole('option', { name: SEEDED_ASSET_LABEL })).toBeVisible();
  await page.getByRole('option', { name: SEEDED_ASSET_LABEL }).click();

  await dialog.getByRole('button', { name: 'Entwurf anlegen' }).click();
  await expect(dialog.getByRole('alert')).toHaveText('Wählen Sie mindestens einen Kanal aus.');
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(dialog).toBeHidden();
});

test('an editor creates the calendar feed address once, sees it, and blocks it again', async ({ page }) => {
  await signInAs(page, 'editor');
  await openCalendar(page);

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

// The full flow: an editor plans the seeded clip onto the seeded channel for Tuesday 10 November at 18:00 in the
// installation zone; a publisher approves it and schedules it. The schedule dry run passes for this channel, so the post
// reaches the status scheduled. Each step is asserted on the page the person sees.
test('an editor plans a finished clip on a channel, submits it, and a publisher approves and schedules it', async ({
  page,
}) => {
  const title = `Kalender-Test ${randomUUID().slice(0, 8)}`;

  await signInAs(page, 'editor');
  await openCalendar(page);

  // 1. The editor plans the clip and sees the draft on the calendar.
  await page.getByRole('button', { name: 'Beitrag planen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Beitrag planen' });
  await dialog.getByRole('combobox', { name: 'Fertiger Clip' }).click();
  await page.getByRole('option', { name: SEEDED_ASSET_LABEL }).click();
  await dialog.getByRole('checkbox', { name: SEEDED_CHANNEL }).check();
  await dialog.getByRole('textbox', { name: 'Titel' }).fill(title);
  await dialog.getByLabel('Datum (Installationszeitzone)').fill('2026-11-10');
  await dialog.getByLabel('Uhrzeit').fill('18:00');
  await dialog.getByRole('button', { name: 'Entwurf anlegen' }).click();
  await expect(dialog).toBeHidden();
  await await expect(notice(page, '1 Entwürfe angelegt.')).toBeVisible();

  const tuesday = page.getByRole('group', { name: 'Dienstag, 10. November 2026' });
  const entry = tuesday.getByRole('button', { name: new RegExp(`18:00.*${title}.*Entwurf`) });
  await expect(entry).toBeVisible();

  // 2. The editor submits the draft for review.
  await entry.click();
  await expect(panel(page).getByRole('heading', { name: title })).toBeVisible();
  await expect(panel(page).getByText(SEEDED_CHANNEL, { exact: false })).toBeVisible();
  await panel(page).getByRole('button', { name: 'Zur Prüfung senden' }).click();
  await await expect(notice(page, 'Der Beitrag wurde zur Prüfung gesendet.')).toBeVisible();
  await expect(panel(page).getByText('In Prüfung', { exact: true })).toBeVisible();

  // 3. A publisher, a new person of that role, approves it.
  await signInAs(page, 'publisher');
  await openCalendar(page);
  await page.getByRole('group', { name: 'Dienstag, 10. November 2026' })
    .getByRole('button', { name: new RegExp(`18:00.*${title}.*In Prüfung`) }).click();
  await panel(page).getByRole('button', { name: 'Freigeben' }).click();
  await await expect(notice(page, 'Der Beitrag wurde freigegeben.')).toBeVisible();
  await expect(panel(page).getByText('Freigegeben', { exact: true })).toBeVisible();

  // 4. The publisher schedules it; the dry run passes for the connected channel, so the post is scheduled.
  await panel(page).getByRole('button', { name: 'Einplanen' }).click();
  await await expect(notice(page, 'Der Beitrag ist eingeplant.')).toBeVisible();
  await expect(panel(page).getByText('Geplant', { exact: true })).toBeVisible();
  await expect(page.getByRole('group', { name: 'Dienstag, 10. November 2026' })
    .getByRole('button', { name: new RegExp(`18:00.*${title}.*Geplant`) })).toBeVisible();
});
