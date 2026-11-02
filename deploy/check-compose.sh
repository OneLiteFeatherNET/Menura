#!/usr/bin/env bash
# Checks the development compose file at the repository root:
#   - it parses (docker compose config --quiet),
#   - no service image is unpinned or uses the `latest` tag,
#   - PostgreSQL is major version 17.
# Requires: docker (with compose v2), jq.
set -euo pipefail

cd "$(dirname "$0")/.."

fail=0
report() {
  echo "FAIL: $*" >&2
  fail=1
}

docker compose config --quiet || { echo "FAIL: docker compose config does not parse" >&2; exit 1; }

config_json="$(docker compose config --format json)"
images="$(jq -r '.services[] | .image // empty' <<<"$config_json")"

if [[ -z "$images" ]]; then
  report "no service declares an image"
fi

postgres_seen=0
while IFS= read -r image; do
  [[ -z "$image" ]] && continue

  ref="${image%@*}"        # a digest pin is allowed, drop it for the tag test
  last="${ref##*/}"        # last path segment carries the tag
  if [[ "$last" != *:* ]]; then
    report "$image has no tag"
    continue
  fi
  tag="${last##*:}"
  repo="${ref%:"$tag"}"

  if [[ "$tag" == "latest" ]]; then
    report "$image uses the latest tag"
  fi

  if [[ "$repo" == "postgres" || "$repo" == */postgres ]]; then
    postgres_seen=1
    if [[ ! "$tag" =~ ^17\.[0-9]+([.-].*)?$ ]]; then
      report "$image is not PostgreSQL 17.x"
    fi
  fi
done <<<"$images"

if [[ "$postgres_seen" -eq 0 ]]; then
  report "no PostgreSQL service found"
fi

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "compose check passed"
