#!/usr/bin/env bash
# Lints the Helm chart and renders it, then asserts the public surface:
#   - exactly three HTTPRoutes, for menura, api-menura and mcp-menura
#     .onelitefeather.net, and no other host anywhere in the chart,
#   - each host forwards to the right Service (web for the app, server for
#     the API and MCP endpoint),
#   - the migrate Helm hook runs `jobs run migrate`,
#   - the server, jobs and migrate pods get MENURA_DB_URL (not DATASOURCES_*),
#   - the server liveness probe is a TCP check and readiness is /health,
#   - every route sets HSTS, Referrer-Policy no-referrer and nosniff,
#   - the rate limits: per IP and per Authorization token, local, per minute, with a 429 answer as Problem
#     Details and Retry-After,
#   - no ClientTrafficPolicy and no SecurityPolicy: TLS 1.3 and the client address belong to the shared listener
#     (cluster repository), and CORS is answered only by the server (no Access-Control-Allow-Origin from the gateway),
#   - no wildcard anywhere in the rendered edge policies,
#   - the images come from harbor.onelitefeather.dev/onelitefeather/menura-*,
#   - with httpRoute.enabled=false the chart renders no HTTPRoute and no Envoy policy at all,
#   - with imagePullSecrets set, every pod spec (server, jobs, web and the
#     migrate hook) carries them.
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
rendered_disabled="$(mktemp)"
rendered_pull="$(mktemp)"
trap 'rm -f "$rendered" "$rendered_disabled" "$rendered_pull"' EXIT
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

# Database settings: the server, the jobs and the migrate hook read MENURA_DB_URL (core MenuraSettings
# and server application.yml). The chart must not set the Micronaut DATASOURCES_* names instead.
db_url="$(yq -r '.database.url' "$chart/values.yaml")"
if grep -q 'DATASOURCES_DEFAULT' "$rendered"; then
  report "the chart sets DATASOURCES_DEFAULT_*, but the code reads MENURA_DB_*"
fi
for component in server jobs migrate; do
  db_urls="$(component="$component" yq -N 'select(.kind == "Deployment" or .kind == "Job") | select(.metadata.labels."app.kubernetes.io/component" == strenv(component)) | .spec.template.spec.containers[].env[] | select(.name == "MENURA_DB_URL") | .value' "$rendered")"
  if [[ "$db_urls" != "$db_url" ]]; then
    report "the $component pods must get MENURA_DB_URL=$db_url (found: ${db_urls:-none})"
  fi
done

# Probes: liveness is a TCP check on the http port, so a database outage cannot restart the server;
# readiness is /health, which reports the database.
server_container='select(.kind == "Deployment") | select(.metadata.labels."app.kubernetes.io/component" == "server") | .spec.template.spec.containers[0]'
liveness_tcp_port="$(yq -N "$server_container | .livenessProbe.tcpSocket.port // \"\"" "$rendered")"
liveness_http_path="$(yq -N "$server_container | .livenessProbe.httpGet.path // \"\"" "$rendered")"
readiness_http_path="$(yq -N "$server_container | .readinessProbe.httpGet.path // \"\"" "$rendered")"
if [[ "$liveness_tcp_port" != "http" || -n "$liveness_http_path" ]]; then
  report "the server liveness probe must be a tcpSocket probe on port http (found tcp port '$liveness_tcp_port', http path '$liveness_http_path')"
fi
if [[ "$readiness_http_path" != "/health" ]]; then
  report "the server readiness probe must be httpGet /health (found '$readiness_http_path')"
fi

# Edge policies on the shared Envoy Gateway (principles 11 and 14). The gateway and its listener belong to the cluster
# repository; the chart renders only the rate limits that attach to its routes.
count_kind() {
  yq -N "select(.kind == \"$1\") | .kind" "$rendered" | grep -c . || true
}

# Security response headers: every route sends HSTS, no referrer and nosniff, the web route included because
# the static web pod does not pass through the server's filter.
expected_headers="$(for route in api mcp web; do
  printf '%s\n' \
    "$release-$route Referrer-Policy no-referrer" \
    "$release-$route Strict-Transport-Security max-age=31536000" \
    "$release-$route X-Content-Type-Options nosniff"
done | sort)"
actual_headers="$(yq -N 'select(.kind == "HTTPRoute") | .metadata.name as $r | .spec.rules[].filters[]? | select(.type == "ResponseHeaderModifier") | .responseHeaderModifier.set[] | $r + " " + .name + " " + .value' "$rendered" | sort)"
if [[ "$actual_headers" != "$expected_headers" ]]; then
  report "the HTTPRoutes must set exactly the three security response headers on every route"
  echo "expected:" >&2
  echo "$expected_headers" >&2
  echo "actual:" >&2
  echo "$actual_headers" >&2
fi

# Rate limits per IP (IPv4 and IPv6 as separate Distinct rules) on every route, and per Authorization token on the
# API and the MCP endpoint. Every budget is per minute with at least one request.
expected_limits="$(printf '%s\n' \
  "$release-api header Distinct Authorization" \
  "$release-api ip Distinct 0.0.0.0/0" \
  "$release-api ip Distinct ::/0" \
  "$release-mcp header Distinct Authorization" \
  "$release-mcp ip Distinct 0.0.0.0/0" \
  "$release-mcp ip Distinct ::/0" \
  "$release-web ip Distinct 0.0.0.0/0" \
  "$release-web ip Distinct ::/0" | sort)"
actual_limits="$({
  yq -N 'select(.kind == "BackendTrafficPolicy") | .spec.targetRefs[0].name as $t | .spec.rateLimit.local.rules[].clientSelectors[] | select(.sourceCIDR != null) | $t + " ip " + .sourceCIDR.type + " " + .sourceCIDR.value' "$rendered"
  yq -N 'select(.kind == "BackendTrafficPolicy") | .spec.targetRefs[0].name as $t | .spec.rateLimit.local.rules[].clientSelectors[] | select(.headers != null) | .headers[] | $t + " header " + .type + " " + .name' "$rendered"
} | sort)"
if [[ "$actual_limits" != "$expected_limits" ]]; then
  report "the rate limits differ from the per-IP and per-client selectors of the routes"
  echo "expected:" >&2
  echo "$expected_limits" >&2
  echo "actual:" >&2
  echo "$actual_limits" >&2
fi
if [[ "$(count_kind BackendTrafficPolicy)" -ne 3 ]]; then
  report "expected exactly 3 BackendTrafficPolicy resources, one per route"
fi
if yq -N 'select(.kind == "BackendTrafficPolicy") | select(.spec.rateLimit.type != "Local")' "$rendered" | grep -q .; then
  report "the rate limits must be Envoy Gateway local rate limits (no rate limit service runs in the cluster)"
fi
if yq -N 'select(.kind == "BackendTrafficPolicy") | .spec.rateLimit.local.rules[].limit | select(.unit != "Minute" or .requests < 1)' "$rendered" | grep -q .; then
  report "every rate limit must be per minute with at least one request"
fi
# The gateway's own 429 answers Problem Details with Retry-After (principle 11), on every route.
override_summary="$(yq -N 'select(.kind == "BackendTrafficPolicy") | .spec.responseOverride[0] | .match.statusCodes[0].value | tostring' "$rendered" | sort -u)"
if [[ "$override_summary" != "429" || "$(yq -N 'select(.kind == "BackendTrafficPolicy") | .spec.responseOverride[0].response | select(.contentType == "application/problem+json" and .header.set[0].name == "Retry-After" and .header.set[0].value != null) | .contentType' "$rendered" | grep -c . || true)" -ne 3 ]]; then
  report "every rate-limit policy must answer 429 as application/problem+json with a Retry-After header"
fi

# The chart renders no ClientTrafficPolicy: TLS 1.3 and CF-Connecting-IP are set on the shared https listener by the
# cluster repository (README.md, "Shared listener"). A policy here would change every host on that listener.
if [[ "$(count_kind ClientTrafficPolicy)" -ne 0 ]]; then
  report "the chart must not render a ClientTrafficPolicy: it targets the shared listener of every host (see README.md)"
fi

# The chart renders no SecurityPolicy: CORS is answered only by the server, so the gateway adds no
# Access-Control-Allow-Origin and no duplicate can reach the browser.
if [[ "$(count_kind SecurityPolicy)" -ne 0 ]]; then
  report "the chart must not render a SecurityPolicy: CORS is answered only by the server"
fi
if grep -qiE 'access-control-allow|allowOrigins|allowCredentials' "$rendered"; then
  report "the chart must not configure CORS at the gateway"
fi

if grep -qE '(^|[^A-Za-z0-9_.*/-])\*([^A-Za-z0-9_.*/-]|$)' "$rendered"; then
  report "no edge policy may use a wildcard"
fi

# Nothing outside the three public hosts may appear as a host in the rendered chart.
other_hosts="$(grep -oE '[a-z0-9-]+\.onelitefeather\.net' "$rendered" | sort -u \
  | grep -vxE 'menura\.onelitefeather\.net|api-menura\.onelitefeather\.net|mcp-menura\.onelitefeather\.net' || true)"
if [[ -n "$other_hosts" ]]; then
  report "unexpected host(s) in the rendered chart: $other_hosts"
fi

# The images: every component pulls from the OLF Harbor project under its menura-* name.
bad_images="$(yq -N 'select(.kind == "Deployment" or .kind == "Job") | .spec.template.spec.containers[].image' "$rendered" \
  | grep -vE '^harbor\.onelitefeather\.dev/onelitefeather/menura-(server|jobs|web):' || true)"
if [[ -n "$bad_images" ]]; then
  report "images outside harbor.onelitefeather.dev/onelitefeather/menura-*: $bad_images"
fi

# httpRoute.enabled=false: no HTTPRoute is rendered, the workloads still are.
helm template "$release" "$chart" --set httpRoute.enabled=false >"$rendered_disabled"
disabled_routes="$(yq -N 'select(.kind == "HTTPRoute") | .metadata.name' "$rendered_disabled" | grep -c . || true)"
if [[ "$disabled_routes" -ne 0 ]]; then
  report "with httpRoute.enabled=false expected 0 HTTPRoutes, found $disabled_routes"
fi
# The Envoy policies attach to the HTTPRoutes, so they go with them: no gateway.envoyproxy.io resource at all.
disabled_envoy="$(yq -N 'select(.apiVersion == "gateway.envoyproxy.io/v1alpha1") | .kind' "$rendered_disabled" | grep -c . || true)"
if [[ "$disabled_envoy" -ne 0 ]]; then
  report "with httpRoute.enabled=false expected 0 Envoy policy resources, found $disabled_envoy"
fi
if [[ "$(yq -N 'select(.kind == "Deployment") | .metadata.name' "$rendered_disabled" | grep -c . || true)" -eq 0 ]]; then
  report "with httpRoute.enabled=false the Deployments must still be rendered"
fi

# imagePullSecrets set: every pod spec carries the secret, including the migrate hook Job.
helm template "$release" "$chart" --set 'imagePullSecrets[0].name=harbor-pull' >"$rendered_pull"
pod_count="$(yq -N 'select(.kind == "Deployment" or .kind == "Job") | .spec.template.spec | select(. != null) | .containers[0].name' "$rendered_pull" | grep -c . || true)"
pull_count="$(yq -N 'select(.kind == "Deployment" or .kind == "Job") | .spec.template.spec | select(. != null) | select((.imagePullSecrets[]?.name) == "harbor-pull") | .containers[0].name' "$rendered_pull" | grep -c . || true)"
if [[ "$pod_count" -ne 4 ]]; then
  report "expected 4 pod specs (server, jobs, web, migrate), found $pod_count"
fi
if [[ "$pull_count" -ne "$pod_count" ]]; then
  report "every pod spec must carry imagePullSecrets harbor-pull ($pull_count of $pod_count do)"
fi

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "helm check passed"
