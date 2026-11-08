import type { Asset, Person } from './types';

/** Editors and admins may tag assets and add material; viewers read only. */
export function canEditPool(roles: readonly string[]): boolean {
  return roles.includes('EDITOR') || roles.includes('ADMIN');
}

/** The most used tags over the given assets, most used first; ties in alphabetical order. */
export function topTags(assets: readonly Asset[], limit = 12): string[] {
  const counts = new Map<string, number>();
  for (const asset of assets) {
    for (const tag of asset.tags) {
      counts.set(tag, (counts.get(tag) ?? 0) + 1);
    }
  }
  return [...counts.entries()]
    .sort(([tagA, countA], [tagB, countB]) => countB - countA || tagA.localeCompare(tagB))
    .slice(0, limit)
    .map(([tag]) => tag);
}

/** The display name of a person id; the id itself when the person is unknown. */
export function personName(people: readonly Person[], id: string | null): string {
  if (id === null) {
    return 'ohne Person';
  }
  return people.find((person) => person.id === id)?.name ?? id;
}
