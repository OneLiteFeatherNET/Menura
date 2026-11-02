#!/usr/bin/env bash
# Checks the development compose file at the repository root:
#   - it parses (docker compose config --quiet),
#   - no service image is unpinned or uses the `latest` tag,
#   - PostgreSQL is major version 17,
#   - PostgreSQL sets no POSTGRES_PASSWORD, and when it uses trust authentication its
#     port is bound to 127.0.0.1 only.
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

# Local-dev PostgreSQL trusts local connections without a password. That is only acceptable
# while the port stays on loopback, so trust and the 127.0.0.1 binding are checked together.
if jq -e '.services.postgres.environment | has("POSTGRES_PASSWORD")' <<<"$config_json" >/dev/null; then
  report "postgres must not set POSTGRES_PASSWORD (local development uses trust)"
fi

pg_auth="$(jq -r '.services.postgres.environment.POSTGRES_HOST_AUTH_METHOD // empty' <<<"$config_json")"
if [[ "$pg_auth" == "trust" ]]; then
  pg_port_count="$(jq -r '.services.postgres.ports | length' <<<"$config_json")"
  if [[ "$pg_port_count" -eq 0 ]]; then
    report "postgres uses trust but publishes no port; expected 127.0.0.1 only"
  fi
  while IFS= read -r host_ip; do
    if [[ "$host_ip" != "127.0.0.1" ]]; then
      report "postgres uses trust but its port is bound to '${host_ip:-all interfaces}' instead of 127.0.0.1"
    fi
  done < <(jq -r '.services.postgres.ports[]? | .host_ip // "0.0.0.0"' <<<"$config_json")
fi

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "compose check passed"
