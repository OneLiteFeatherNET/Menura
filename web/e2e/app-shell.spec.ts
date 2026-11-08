import { APP, SESSION_COOKIE, expect, signInAs, test } from './fixtures';

test('an anonymous visitor sees the shell with a sign-in link and no role', async ({ page }) => {
  await page.goto(APP);

  await expect(page.getByRole('heading', { name: 'Menura' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Anmelden' })).toHaveAttribute('href', /\/auth\/login$/);
  await expect(page.getByTestId('session-role')).toHaveCount(0);
});

test('signing out ends the session in the shell and clears the cookie', async ({ page }) => {
  await signInAs(page, 'admin');
  await expect(page.getByTestId('session-role')).toBeVisible();

  await page.getByRole('button', { name: 'Abmelden' }).click();

  await expect(page.getByRole('link', { name: 'Anmelden' })).toBeVisible();
  const cookies = await page.context().cookies();
  expect(cookies.find((cookie) => cookie.name === SESSION_COOKIE)?.value ?? '').toBe('');
});
