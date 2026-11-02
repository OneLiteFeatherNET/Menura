// Conventional Commits enforcement (commits + PR title).
// https://www.conventionalcommits.org
// release-please (release-please-config.json) parses commit types to decide version bumps and
// changelog sections, so this isn't just style - a non-conventional commit silently fails to
// bump the version or gets dropped from the changelog.
// The pr-lint workflow reads this file for the commit-lint job.
export default {
  extends: ['@commitlint/config-conventional'],
  // Merge commits inside a pull request never reach main (squash merge), and their bodies list
  // conflicts and long lines, so they are not linted. defaultIgnores stays on.
  defaultIgnores: true,
  ignores: [(message) => /^Merge /.test(message)],
  rules: {
    'header-max-length': [2, 'always', 100],
    'scope-case': [2, 'always', 'lower-case'],
    'type-enum': [
      2,
      'always',
      ['build', 'chore', 'ci', 'docs', 'feat', 'fix', 'perf', 'refactor', 'revert', 'style', 'test'],
    ],
  },
};
