import { APP, expect, signInAs, test } from './fixtures';

// The brand kit is seeded from the overlay theme on the first read of a fresh environment, so an editor who signs in
// sees version 1 with the overlay's primary colour and the short 9:16 preview with its sample parameters.
test('an editor opens the brand kit and sees the seeded version with the short preview', async ({ page }) => {
  await signInAs(page, 'editor');
  await expect(page.getByTestId('session-role')).toBeVisible();
  await page.getByRole('navigation', { name: 'Hauptnavigation' }).getByRole('link', { name: 'Brand-Kit', exact: true }).click();
  await expect(page).toHaveURL(`${APP}brand-kit`);

  await expect(page.getByRole('heading', { name: 'Markenkit' })).toBeVisible();
  await expect(page.getByText('Version 1')).toBeVisible();
  await expect(page.getByText('Hell #6638e4')).toBeVisible();
  await expect(page.getByText('Dunkel #ccbdff')).toBeVisible();

  const preview = page.getByRole('group', { name: /Vorschau Short 9:16/ });
  await expect(preview).toBeVisible();
  await expect(preview.getByText('Beispieltitel')).toBeVisible();
  await expect(preview.getByText('join.example')).toBeVisible();
});
