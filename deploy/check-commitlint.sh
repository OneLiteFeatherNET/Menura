#!/usr/bin/env bash
# Runs commitlint.config.mjs against sample messages, the way the pr-lint workflow lints a commit:
#   - accepted: Conventional Commits with a known type, an optional scope and a lowercase subject,
#   - refused: no type, a capitalised subject, a trailing full stop, a scope in capitals and an
#     unknown type,
#   - merge commits are skipped (their bodies list conflicts and long lines), but a feature commit
#     with the same long body line is still refused.
# The configuration is copied into a scratch directory with its own commitlint install, so the
# repository gets no node_modules. Requires: node and npm.
set -euo pipefail

cd "$(dirname "$0")/.."

config="commitlint.config.mjs"
if [[ ! -f "$config" ]]; then
  echo "FAIL: $config does not exist" >&2
  exit 1
fi

scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
cp "$config" "$scratch/commitlint.config.mjs"
(cd "$scratch" && npm install --silent --no-audit --no-fund @commitlint/cli @commitlint/config-conventional)

# Exit status of commitlint for one message on stdin.
lint() {
  printf '%s\n' "$1" | "$scratch/node_modules/.bin/commitlint" --config "$scratch/commitlint.config.mjs" >/dev/null 2>&1
}

fail=0
accept() {
  if ! lint "$1"; then
    echo "FAIL: refused, should be accepted: $1" >&2
    fail=1
  fi
}
refuse() {
  if lint "$1"; then
    echo "FAIL: accepted, should be refused: $1" >&2
    fail=1
  fi
}

accept "feat(mcp): add hub mcp server"
accept "ci(github): add ci pipeline"
accept "fix: correct the health route"
refuse "Add hub"
refuse "feat: add hub."
refuse "feat(mcp): Add hub"
refuse "feat(MCP): add hub"
refuse "wip: add hub"

# A 150-character body line: over the 100-character body-max-line-length of config-conventional.
long_line="$(printf 'x%.0s' {1..150})"
accept "$(printf "Merge remote-tracking branch 'origin/build/project-skeleton' into HEAD\n\n%s" "$long_line")"
refuse "$(printf "feat: x\n\n%s" "$long_line")"

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "PASS: commitlint checks"
