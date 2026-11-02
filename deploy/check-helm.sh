#!/usr/bin/env bash
# Lints the Helm chart and renders it, then asserts the public surface:
#   - exactly three HTTPRoutes, for menura, api-menura and mcp-menura
#     .onelitefeather.net, and no other host anywhere in the chart,
#   - each host forwards to the right Service (web for the app, server for
#     the API and MCP endpoint),
#   - the migrate Helm hook runs `jobs run migrate`.
# Requires: helm (v3), yq (mikefarah v4), jq is not needed.
set -euo pipefail

cd "$(dirname "$0")/.."

chart="deploy/helm/menura"
release="menura"

fail=0
report() {
  echo "FAIL: $*" >&2
  fail=1
}

helm lint "$chart"

rendered="$(mktemp)"
trap 'rm -f "$rendered"' EXIT
helm template "$release" "$chart" >"$rendered"

# Hosts across all HTTPRoutes, one per line, sorted.
actual_hosts="$(yq -N 'select(.kind == "HTTPRoute") | .spec.hostnames[]' "$rendered" | sort)"
expected_hosts="$(printf '%s\n' \
  api-menura.onelitefeather.net \
  mcp-menura.onelitefeather.net \
  menura.onelitefeather.net | sort)"
if [[ "$actual_hosts" != "$expected_hosts" ]]; then
  report "HTTPRoute hosts differ from the three public hosts"
  echo "expected:" >&2
  echo "$expected_hosts" >&2
  echo "actual:" >&2
  echo "$actual_hosts" >&2
fi

route_count="$(yq -N 'select(.kind == "HTTPRoute") | .metadata.name' "$rendered" | grep -c . || true)"
if [[ "$route_count" -ne 3 ]]; then
  report "expected exactly 3 HTTPRoutes, found $route_count"
fi

# Each route: one host, parent gateway set, forwards to the expected Service.
routes="$(yq -N 'select(.kind == "HTTPRoute") | (.spec.hostnames[0]) + " " + (.spec.rules[0].backendRefs[0].name)' "$rendered")"
expected_routes="$(printf '%s\n' \
  "menura.onelitefeather.net $release-web" \
  "api-menura.onelitefeather.net $release-server" \
  "mcp-menura.onelitefeather.net $release-server" | sort)"
if [[ "$(sort <<<"$routes")" != "$expected_routes" ]]; then
  report "HTTPRoute host-to-Service mapping differs from the expected one"
  echo "expected:" >&2
  echo "$expected_routes" >&2
  echo "actual:" >&2
  echo "$routes" >&2
fi

parent_refs="$(yq -N 'select(.kind == "HTTPRoute") | .spec.parentRefs | length' "$rendered" | sort -u)"
if [[ "$parent_refs" != "1" ]]; then
  report "every HTTPRoute must reference exactly one parent gateway"
fi

if yq -N 'select(.kind == "Ingress")' "$rendered" | grep -q .; then
  report "the chart must not render an Ingress, only the HTTPRoutes"
fi

# The migrate hook: a Job that runs `jobs run migrate` before install or upgrade.
migrate_hook="$(yq -N 'select(.kind == "Job") | select(.metadata.annotations."helm.sh/hook" != null) | .metadata.annotations."helm.sh/hook"' "$rendered")"
if [[ -z "$migrate_hook" ]]; then
  report "no Helm hook Job found"
elif ! grep -q 'pre-install' <<<"$migrate_hook" || ! grep -q 'pre-upgrade' <<<"$migrate_hook"; then
  report "the migrate hook must run on pre-install and pre-upgrade"
fi
if ! yq -N 'select(.kind == "Job") | .spec.template.spec.containers[].command | join(" ")' "$rendered" | grep -q 'jobs run migrate'; then
  report "no hook Job runs 'jobs run migrate'"
fi

# Nothing outside the three public hosts may appear as a host in the rendered chart.
other_hosts="$(grep -oE '[a-z0-9-]+\.onelitefeather\.net' "$rendered" | sort -u \
  | grep -vxE 'menura\.onelitefeather\.net|api-menura\.onelitefeather\.net|mcp-menura\.onelitefeather\.net' || true)"
if [[ -n "$other_hosts" ]]; then
  report "unexpected host(s) in the rendered chart: $other_hosts"
fi

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "helm check passed"
