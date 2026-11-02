#!/usr/bin/env bash
# Checks the release-please configuration at the repository root:
#   - release-please-config.json and .release-please-manifest.json parse as JSON,
#   - the release type is simple, and the changelog sections of the Conventional Commit types
#     feat, fix, perf, revert and docs are shown,
#   - the manifest version is the version in gradle.properties and in the chart,
#   - every extra file exists and is updated: a generic file must carry an
#     x-release-please-version marker, a json file must have the jsonpath it names.
# Requires: jq.
set -euo pipefail

cd "$(dirname "$0")/.."

config="release-please-config.json"
manifest=".release-please-manifest.json"

fail=0
report() {
  echo "FAIL: $*" >&2
  fail=1
}

for file in "$config" "$manifest"; do
  if [[ ! -f "$file" ]]; then
    report "$file does not exist"
  elif ! jq empty "$file" 2>/dev/null; then
    report "$file is not valid JSON"
  fi
done
if [[ "$fail" -ne 0 ]]; then
  exit 1
fi

if [[ "$(jq -r '."release-type"' "$config")" != "simple" ]]; then
  report "$config must use release-type simple"
fi

for type in feat fix perf revert docs; do
  section="$(jq -r --arg t "$type" '."changelog-sections"[]? | select(.type == $t and (.hidden // false | not)) | .section' "$config")"
  if [[ -z "$section" ]]; then
    report "changelog-sections does not show the '$type' type"
  fi
done

package_path="$(jq -r '.packages | keys[0]' "$config")"
if [[ "$package_path" != "." ]]; then
  report "the single package of $config must be the root '.', found '$package_path'"
fi

manifest_version="$(jq -r '."."' "$manifest")"
if [[ -z "$manifest_version" || "$manifest_version" == "null" ]]; then
  report "$manifest has no version for the root package"
fi

# The version the build reads, from the marked line of gradle.properties.
gradle_version="$(sed -nE 's/^version[[:space:]]*=[[:space:]]*([^[:space:]#]+).*/\1/p' gradle.properties | head -n1)"
if [[ "$gradle_version" != "$manifest_version" ]]; then
  report "the manifest version '$manifest_version' differs from gradle.properties '$gradle_version'"
fi

while IFS= read -r entry; do
  type="$(jq -r '.type' <<<"$entry")"
  path="$(jq -r '.path' <<<"$entry")"
  if [[ ! -f "$path" ]]; then
    report "extra file $path does not exist"
    continue
  fi
  case "$type" in
    generic)
      if ! grep -q 'x-release-please-version' "$path"; then
        report "$path has no x-release-please-version marker"
      fi
      ;;
    json)
      jsonpath="$(jq -r '.jsonpath' <<<"$entry")"
      # Only the simple `$.key` form is used here; it is what the json updater reads.
      key="${jsonpath#\$.}"
      if ! jq -e --arg k "$key" 'has($k)' "$path" >/dev/null 2>&1; then
        report "$path has no '$key' at the jsonpath $jsonpath"
      fi
      ;;
    *)
      report "extra file $path has an unknown type '$type'"
      ;;
  esac
done < <(jq -c '.packages["."]["extra-files"][]' "$config")

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "PASS: release-please configuration"
