import { APP, expect, signInAs, test } from './fixtures';

// The e2e database has no metrics, so the flow checks the tabs and the empty or loaded state of each tab, never a number.
test('analytics tab navigation', async ({ page }) => {
  await signInAs(page, 'viewer');
  await expect(page.getByTestId('session-role')).toBeVisible();
  await page.goto(`${APP}analytics`);

  await expect(page.getByTestId('analytics-page')).toBeVisible();
  const tabs = page.getByRole('tablist');
  await expect(tabs.getByRole('tab', { name: 'Überblick', exact: true })).toBeVisible();
  await expect(tabs.getByRole('tab', { name: 'Kanäle', exact: true })).toBeVisible();
  await expect(tabs.getByRole('tab', { name: 'Serien', exact: true })).toBeVisible();
  await expect(tabs.getByRole('tab', { name: 'Uhrzeit/Wochentag', exact: true })).toBeVisible();

  const panel = page.getByTestId('analytics-panel');
  await expect(page.getByTestId('analytics-tab-overview')).toHaveAttribute('aria-selected', 'true');
  await expect(panel.getByRole('heading', { level: 2, name: 'Überblick' })).toBeVisible();

  await page.getByTestId('analytics-tab-channels').click();
  await expect(page.getByTestId('analytics-tab-channels')).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByTestId('analytics-tab-overview')).toHaveAttribute('aria-selected', 'false');
  await expect(panel.getByRole('heading', { level: 2, name: 'Kanäle' })).toBeVisible();

  await page.getByTestId('analytics-tab-series').click();
  await expect(page.getByTestId('analytics-tab-series')).toHaveAttribute('aria-selected', 'true');
  await expect(panel.getByRole('heading', { level: 2, name: 'Serien' })).toBeVisible();

  await page.getByTestId('analytics-tab-time').click();
  await expect(page.getByTestId('analytics-tab-time')).toHaveAttribute('aria-selected', 'true');
  await expect(panel.getByRole('heading', { level: 2, name: 'Uhrzeit/Wochentag' })).toBeVisible();
});
