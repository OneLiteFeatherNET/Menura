import type { Page } from '@playwright/test';
import { APP, ROLES, ROLE_LABELS, expect, signInAs, test, type TestRole } from './fixtures';

// The items of the navigation per role. Every existing area is open to any signed-in person, so each role sees the
// same four items; a role that is later shut out of an area is listed without that area (web/src/navigation/areas.ts).
const EXPECTED_NAV: Record<TestRole, string[]> = {
  viewer: ['Übersicht', 'Pool', 'Brand-Kit', 'Kanäle'],
  editor: ['Übersicht', 'Pool', 'Brand-Kit', 'Kanäle'],
  publisher: ['Übersicht', 'Pool', 'Brand-Kit', 'Kanäle'],
  operator: ['Übersicht', 'Pool', 'Brand-Kit', 'Kanäle'],
  admin: ['Übersicht', 'Pool', 'Brand-Kit', 'Kanäle'],
};

const rail = (page: Page) => page.getByRole('navigation', { name: 'Hauptnavigation' });

for (const role of ROLES) {
  test(`signing in as ${role} shows the navigation of that role and no other items`, async ({ page }) => {
    await signInAs(page, role);
    await expect(page.getByTestId('session-role')).toHaveText(ROLE_LABELS[role]);

    await expect(rail(page).getByRole('link')).toHaveText(EXPECTED_NAV[role]);
  });
}

test('an editor moves from the pool to the brand kit and to the channels with the rail', async ({ page }) => {
  await signInAs(page, 'editor');
  await expect(page.getByRole('heading', { level: 1, name: 'Übersicht' })).toBeVisible();

  await rail(page).getByRole('link', { name: 'Pool', exact: true }).click();
  await expect(page).toHaveURL(`${APP}pool`);
  await expect(page.getByRole('heading', { level: 1, name: 'Content-Pool' })).toBeVisible();
  await expect(rail(page).getByRole('link', { name: 'Pool', exact: true })).toHaveAttribute('aria-current', 'page');

  await rail(page).getByRole('link', { name: 'Brand-Kit', exact: true }).click();
  await expect(page).toHaveURL(`${APP}brand-kit`);
  await expect(page.getByRole('heading', { level: 1, name: 'Markenkit' })).toBeVisible();

  await rail(page).getByRole('link', { name: 'Kanäle', exact: true }).click();
  await expect(page).toHaveURL(`${APP}channels`);
  await expect(page.getByRole('heading', { level: 1, name: 'Kanäle' })).toBeVisible();
  await expect(rail(page).getByRole('link', { name: 'Brand-Kit', exact: true })).not.toHaveAttribute('aria-current');
});

test('a reload of a deep link opens its page, for the pool, the brand kit and the channels', async ({ page }) => {
  await signInAs(page, 'editor');
  await expect(page.getByTestId('session-role')).toBeVisible();

  for (const [path, heading] of [
    ['pool', 'Content-Pool'],
    ['brand-kit', 'Markenkit'],
    ['channels', 'Kanäle'],
  ] as const) {
    await page.goto(`${APP}${path}`);
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();

    await page.reload();
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();
    await expect(page).toHaveURL(`${APP}${path}`);
  }
});

test('an unknown path shows the not-found view with a link back to the start page', async ({ page }) => {
  await signInAs(page, 'viewer');
  await expect(page.getByTestId('session-role')).toBeVisible();

  await page.goto(`${APP}gibt-es-nicht`);

  await expect(page.getByRole('heading', { level: 1, name: 'Seite nicht gefunden' })).toBeVisible();
  await page.getByRole('link', { name: 'Zur Übersicht' }).click();
  await expect(page).toHaveURL(APP);
});
