import { API, expect, signInAs, test } from './fixtures';

// The pool of the content library against the real server: a fresh environment has no items, so the listing shows
// its empty state, and the filters are sent to the API as query parameters.
test('an editor opens the pool, filters it by tag and resets the filter', async ({ page }) => {
  await signInAs(page, 'editor');
  await expect(page.getByTestId('session-role')).toBeVisible();

  await expect(page.getByRole('table', { name: 'Content-Pool' })).toBeVisible();
  await expect(page.getByText('Keine Einträge gefunden.')).toBeVisible();

  const tagSearch = page.getByRole('searchbox', { name: 'Tag' });
  const filtered = page.waitForRequest((request) =>
    request.url().startsWith(`${API}/library/items`) && new URL(request.url()).searchParams.get('tag') === 'speedrun');
  await tagSearch.fill('speedrun');
  expect((await filtered).url()).toContain('limit=50');

  await page.getByRole('button', { name: 'Filter zurücksetzen' }).click();
  await expect(tagSearch).toHaveValue('');
  await expect(page.getByRole('button', { name: 'Filter zurücksetzen' })).toHaveCount(0);
});

test('a viewer reads the same pool without any editing control', async ({ page }) => {
  await signInAs(page, 'viewer');

  await expect(page.getByRole('table', { name: 'Content-Pool' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Entfernen' })).toHaveCount(0);
});
