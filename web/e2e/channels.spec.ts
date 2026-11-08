import { APP, expect, signInAs, test } from './fixtures';

// The consent of a real platform is never reached here: the flow stops at the platform list, the empty channel list
// and the audit status, which are all answered by the server without an external call.
test('an administrator sees the platforms and an empty channel list, and records an audit status', async ({ page }) => {
  await signInAs(page, 'admin');
  await expect(page.getByTestId('session-role')).toHaveText('Administration');

  await page.getByRole('link', { name: 'Kanäle' }).click();

  await expect(page).toHaveURL(`${APP}channels`);
  await expect(page.getByRole('heading', { level: 1, name: 'Kanäle' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Kanal verbinden' })).toBeEnabled();
  await expect(page.getByText('Noch kein Kanal verbunden.')).toBeVisible();

  const platform = page.getByRole('row', { name: /YouTube/ });
  await expect(platform.getByText('Unbekannt')).toBeVisible();

  await platform.getByRole('button', { name: 'Prüfstatus erfassen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Prüfstatus: YouTube' });
  await dialog.getByRole('combobox', { name: 'Status' }).click();
  await page.getByRole('option', { name: 'Nicht geprüft' }).click();
  await dialog.getByLabel('Quelle der Entscheidung').fill('E2E-Prüfung');
  await dialog.getByRole('button', { name: 'Speichern' }).click();

  await expect(dialog).toBeHidden();
  await expect(page.getByRole('status')).toHaveText('Der Prüfstatus wurde gespeichert.');
  await expect(platform.getByText('Nicht geprüft')).toBeVisible();
  await expect(platform.getByText('Öffentliche Sichtbarkeit nicht verfügbar')).toBeVisible();
});
