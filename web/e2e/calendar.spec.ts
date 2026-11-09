import { randomUUID } from 'node:crypto';
import type { Locator, Page } from '@playwright/test';
import { API, APP, SEEDED_ASSET_LABEL, SEEDED_CHANNEL, expect, signInAs, test } from './fixtures';

// The content calendar against the real server. The test-only seed (web/e2e/support/seed.sql) adds one connected YouTube
// channel and one registered finished asset; no platform is contacted, and the fixed e2e clock makes today 9 November 2026.

const rail = (page: Page) => page.getByRole('navigation', { name: 'Hauptnavigation' });
const panel = (page: Page) => page.getByRole('complementary', { name: 'Beitrag und Kalender-Abo' });
// The notices of the page and of the side panel are both role=status; each is found by its own text.
const notice = (page: Page, text: string) => page.getByRole('status').filter({ hasText: text });
const TUESDAY = 'Dienstag, 10. November 2026';

async function openCalendar(page: Page) {
  await rail(page).getByRole('link', { name: 'Kalender', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Kalender' })).toBeVisible();
}

/** The entry of a post on Tuesday, found by its time, title and status text. */
function entryOn(page: Page, time: string, title: string, status: string): Locator {
  return page.getByRole('group', { name: TUESDAY }).getByRole('button', { name: new RegExp(`${time}.*${title}.*${status}`) });
}

/** Plans the seeded clip as a draft on the seeded channel at a local time on Tuesday 10 November. */
async function planDraft(page: Page, title: string, time: string) {
  await page.getByRole('button', { name: 'Beitrag planen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Beitrag planen' });
  await dialog.getByRole('combobox', { name: 'Fertiger Clip' }).click();
  await page.getByRole('option', { name: SEEDED_ASSET_LABEL }).click();
  await dialog.getByRole('checkbox', { name: SEEDED_CHANNEL }).check();
  await dialog.getByRole('textbox', { name: 'Titel' }).fill(title);
  await dialog.getByLabel('Datum (Installationszeitzone)').fill('2026-11-10');
  await dialog.getByLabel('Uhrzeit').fill(time);
  await dialog.getByRole('button', { name: 'Entwurf anlegen' }).click();
  await expect(dialog).toBeHidden();
  await expect(notice(page, '1 Entwürfe angelegt.')).toBeVisible();
  await expect(entryOn(page, time, title, 'Entwurf')).toBeVisible();
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

// The full flow. Two posts of the seeded clip go onto the seeded YouTube channel for Tuesday 10 November, in the
// installation zone. The first post gets its declaration (visibility and made-for-kids answer), is submitted, approved by a
// publisher and scheduled: it reaches the status scheduled. The second post gets no declaration, so the publisher's
// scheduling is refused and the German texts of the two missing answers are shown. Each step is asserted on the page.
test('an editor plans a finished clip on a YouTube channel, declares it, a publisher schedules it; a missing declaration blocks', async ({
  page,
}) => {
  const id = randomUUID().slice(0, 8);
  const declared = `Kalender-Test ${id}`;
  const undeclared = `Ohne Angabe ${id}`;

  await signInAs(page, 'editor');
  await openCalendar(page);

  // 1. The editor plans both posts; they appear as drafts on the calendar.
  await planDraft(page, declared, '18:00');
  await planDraft(page, undeclared, '20:00');

  // 2. The editor declares the first post: a private video, not made for kids, not a Short.
  await entryOn(page, '18:00', declared, 'Entwurf').click();
  await expect(panel(page).getByRole('heading', { name: declared })).toBeVisible();
  await expect(panel(page).getByText(SEEDED_CHANNEL, { exact: false })).toBeVisible();
  await expect(panel(page).getByText('Kontingent heute', { exact: false })).toBeVisible();
  await panel(page).getByRole('combobox', { name: 'Sichtbarkeit' }).click();
  await page.getByRole('option', { name: 'Privat' }).click();
  await panel(page).getByRole('radio', { name: /Nein, das Video ist nicht für Kinder bestimmt/ }).check();
  await panel(page).getByRole('button', { name: 'Angaben speichern' }).click();
  await expect(notice(page, 'Die Angaben zur Veröffentlichung wurden gespeichert.')).toBeVisible();

  // 3. The editor submits both posts for review.
  await panel(page).getByRole('button', { name: 'Zur Prüfung senden' }).click();
  await expect(notice(page, 'Der Beitrag wurde zur Prüfung gesendet.')).toBeVisible();
  await expect(panel(page).getByText('In Prüfung', { exact: true })).toBeVisible();
  await entryOn(page, '20:00', undeclared, 'Entwurf').click();
  await panel(page).getByRole('button', { name: 'Zur Prüfung senden' }).click();
  await expect(notice(page, 'Der Beitrag wurde zur Prüfung gesendet.')).toBeVisible();

  // 4. A publisher, a new person of that role, approves both and schedules the declared post.
  await signInAs(page, 'publisher');
  await openCalendar(page);
  await entryOn(page, '18:00', declared, 'In Prüfung').click();
  await panel(page).getByRole('button', { name: 'Freigeben' }).click();
  await expect(notice(page, 'Der Beitrag wurde freigegeben.')).toBeVisible();
  await panel(page).getByRole('button', { name: 'Einplanen' }).click();
  await expect(notice(page, 'Der Beitrag ist eingeplant.')).toBeVisible();
  await expect(panel(page).getByText('Geplant', { exact: true })).toBeVisible();
  await expect(entryOn(page, '18:00', declared, 'Geplant')).toBeVisible();

  // 5. The undeclared post is approved, and its scheduling is refused with the two missing answers named in German.
  await entryOn(page, '20:00', undeclared, 'In Prüfung').click();
  await panel(page).getByRole('button', { name: 'Freigeben' }).click();
  await expect(notice(page, 'Der Beitrag wurde freigegeben.')).toBeVisible();
  await panel(page).getByRole('button', { name: 'Einplanen' }).click();
  await expect(panel(page).getByText('Die Prüfung hat Probleme gefunden. Der Beitrag wurde nicht eingeplant.')).toBeVisible();
  await expect(panel(page).getByText('Wählen Sie die Sichtbarkeit des Beitrags, bevor er eingeplant wird.')).toBeVisible();
  await expect(panel(page).getByText('Beantworten Sie, ob das Video für Kinder bestimmt ist, bevor es eingeplant wird.')).toBeVisible();
  await expect(panel(page).getByText('Freigegeben', { exact: true })).toBeVisible();
});
