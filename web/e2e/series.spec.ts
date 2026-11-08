import { APP, expect, signInAs, test } from './fixtures';

// The server of an e2e run starts with an empty database, so the series name is unique within the run.
test('an editor opens the series area from the rail, creates a series and finds it in the list', async ({ page }) => {
  await signInAs(page, 'editor');
  await expect(page.getByTestId('session-role')).toBeVisible();
  await page.getByRole('navigation', { name: 'Hauptnavigation' }).getByRole('link', { name: 'Serien', exact: true }).click();
  await expect(page).toHaveURL(`${APP}serien`);

  await expect(page.getByRole('heading', { name: 'Serien' })).toBeVisible();
  await page.getByRole('button', { name: 'Neue Serie' }).click();
  await page.getByRole('textbox', { name: 'Name' }).fill('Kubernetes in 30 s');
  await page.getByRole('textbox', { name: 'Badge-Text (Deutsch)' }).fill('Kurz erklärt');
  await page.getByRole('textbox', { name: 'Hashtag' }).fill('#kubernetes30s');
  await page.getByRole('button', { name: 'Serie anlegen' }).click();

  const list = page.getByRole('table', { name: 'Serien' });
  await expect(list).toContainText('Kubernetes in 30 s');
  await expect(list).toContainText('#kubernetes30s');
});
