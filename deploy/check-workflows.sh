#!/usr/bin/env bash
# Checks the GitHub Actions workflows and the files they reference, without running them:
#   - actionlint on .github/workflows, when actionlint is installed,
#   - every workflow parses as YAML and the six concerns of the pipeline exist,
#   - every reusable workflow of OneLiteFeatherNET/workflows is pinned to a full SemVer tag,
#   - no workflow starts on a tag push, and every publishing job is gated on release_created of
#     the release-please job,
#   - the images of release-please.yml are the three Menura components in the OLF Harbor project,
#   - every deploy/*.sh a workflow runs exists and is executable,
#   - every Gradle project a workflow names is included in settings.gradle.kts,
#   - the Renovate preset names the maintainer team, and the markdown lint config exists.
# Requires: python3 with PyYAML. actionlint is optional.
set -euo pipefail

cd "$(dirname "$0")/.."

if command -v actionlint >/dev/null 2>&1; then
  actionlint
  echo "PASS: actionlint"
else
  echo "SKIP: actionlint is not installed, only the structural checks run" >&2
fi

python3 - "$PWD" <<'PY'
import glob
import json
import os
import re
import sys

import yaml

root = sys.argv[1]
os.chdir(root)
problems = []


def fail(message):
    problems.append(message)


def load(path):
    with open(path, encoding="utf-8") as fh:
        return yaml.safe_load(fh)


def triggers(doc):
    # PyYAML reads the bare key `on` as the boolean True.
    return doc.get("on", doc.get(True)) or {}


def jobs_using(doc, suffix):
    return {
        job_id: job
        for job_id, job in (doc.get("jobs") or {}).items()
        if isinstance(job, dict) and suffix in str(job.get("uses", ""))
    }


def step_text(job):
    parts = []
    for step in job.get("steps") or []:
        if isinstance(step, dict):
            parts.append(str(step.get("run", "")))
    return "\n".join(parts)


workflows = {}
for path in sorted(glob.glob(".github/workflows/*.yml")):
    try:
        workflows[path] = load(path)
    except yaml.YAMLError as error:
        fail(f"{path} is not valid YAML: {error}")

required = [
    ".github/workflows/build.yml",
    ".github/workflows/pr-lint.yml",
    ".github/workflows/release-please.yml",
    ".github/workflows/security.yml",
    ".github/workflows/chart.yml",
    ".github/workflows/markdown-lint.yml",
]
for path in required:
    if path not in workflows:
        fail(f"{path} does not exist")

# Pins and triggers, for every workflow.
for path, doc in workflows.items():
    trig = triggers(doc)
    push = trig.get("push") if isinstance(trig, dict) else None
    if isinstance(push, dict) and "tags" in push:
        fail(f"{path} starts on a tag push, which GITHUB_TOKEN tags never trigger")
    for job_id, job in (doc.get("jobs") or {}).items():
        uses = str(job.get("uses", "")) if isinstance(job, dict) else ""
        if "OneLiteFeatherNET/workflows/" in uses and not re.search(r"@v\d+\.\d+\.\d+$", uses):
            fail(f"{path} job '{job_id}' uses {uses}, which is not pinned to a full SemVer tag")

# Publishing jobs: gated on the release-please output of this run.
publishing = ("docker-publish.yml", "sbom-publish.yml")
for path, doc in workflows.items():
    for job_id, job in (doc.get("jobs") or {}).items():
        if not isinstance(job, dict):
            continue
        uses = str(job.get("uses", ""))
        publishes = any(f"{name}@" in uses for name in publishing) or "gh release upload" in step_text(job)
        if not publishes:
            continue
        condition = str(job.get("if", ""))
        if "needs.release-please.outputs.release_created == 'true'" not in condition:
            fail(f"{path} job '{job_id}' publishes but is not gated on release_created of release-please")

# Gradle projects named by the workflows must be included in the build.
settings = open("settings.gradle.kts", encoding="utf-8").read()
included = set(re.findall(r'"([a-z-]+)"', " ".join(re.findall(r"include\(([^)]*)\)", settings))))
for path in workflows:
    text = open(path, encoding="utf-8").read()
    for module in set(re.findall(r"\./gradlew[^\n]*?:([a-z-]+):[a-zA-Z]+", text)):
        if module not in included:
            fail(f"{path} runs a task of '{module}', which settings.gradle.kts does not include")

# Scripts named by the workflows exist and are executable.
for path in workflows:
    text = open(path, encoding="utf-8").read()
    for script in set(re.findall(r"deploy/[A-Za-z0-9_.-]+\.sh", text)):
        if not os.path.isfile(script):
            fail(f"{path} runs {script}, which does not exist")
        elif not os.access(script, os.X_OK):
            fail(f"{script} is not executable")

# build.yml: Gradle (unit tests without integration), integration tests after them, web checks.
build = workflows.get(".github/workflows/build.yml", {})
gradle_jobs = jobs_using(build, "gradle-build-pr.yml@v2.9.1")
if not gradle_jobs:
    fail("build.yml has no job using the reusable gradle-build-pr workflow")
for job_id, job in gradle_jobs.items():
    if "excludeTags=integration" not in str((job.get("with") or {}).get("gradle-task", "")):
        fail(f"build.yml job '{job_id}' must run the unit tests without the integration tag")
integration = [
    job_id for job_id, job in (build.get("jobs") or {}).items()
    if isinstance(job, dict) and "IntegrationTest" in step_text(job)
]
if not integration:
    fail("build.yml has no job that runs the integration tests")
for job_id in integration:
    needs = build["jobs"][job_id].get("needs") or []
    needs = [needs] if isinstance(needs, str) else needs
    if not set(needs) & set(gradle_jobs):
        fail(f"build.yml job '{job_id}' must run after the unit tests")
web_text = "\n".join(step_text(job) for job in (build.get("jobs") or {}).values() if isinstance(job, dict))
for command in ("pnpm lint", "pnpm typecheck", "pnpm test", "pnpm build"):
    if command not in web_text:
        fail(f"build.yml does not run '{command}'")

# chart.yml: the three deploy checks and the schema validation of the rendered chart.
chart_text = "\n".join(
    step_text(job) for job in (workflows.get(".github/workflows/chart.yml", {}).get("jobs") or {}).values()
    if isinstance(job, dict)
)
for script in ("deploy/check-helm.sh", "deploy/check-compose.sh", "deploy/check-images.sh", "kubeconform"):
    if script not in chart_text:
        fail(f"chart.yml does not run {script}")

# release-please.yml: the three images, with keyless signing permission.
release = workflows.get(".github/workflows/release-please.yml", {})
images = set()
for job_id, job in jobs_using(release, "docker-publish.yml@v2.9.1").items():
    images.add((job.get("with") or {}).get("image-name"))
    if (job.get("permissions") or {}).get("id-token") != "write":
        fail(f"release-please.yml job '{job_id}' publishes an image without id-token: write")
if images != {"onelitefeather/menura-server", "onelitefeather/menura-jobs", "onelitefeather/menura-web"}:
    fail(f"release-please.yml publishes {sorted(images)}, expected onelitefeather/menura-server, -jobs and -web")
if not jobs_using(release, "release-please.yml@v2.9.1"):
    fail("release-please.yml does not call the reusable release-please workflow")

# security.yml: report-only, weekly.
security = workflows.get(".github/workflows/security.yml", {})
scan = jobs_using(security, "security-scan.yml@v2.9.1")
if not scan:
    fail("security.yml does not call the reusable security-scan workflow")
for job in scan.values():
    scan_with = job.get("with") or {}
    if scan_with.get("fail-on-findings") is not False:
        fail("security.yml must stay report-only (fail-on-findings: false)")
    if "secret" not in str(scan_with.get("scanners", "")):
        fail("security.yml must scan for secrets as well as vulnerabilities")
schedules = triggers(security).get("schedule") or []
if not any(str(item.get("cron", "")).split()[-1] == "1" for item in schedules if isinstance(item, dict)):
    fail("security.yml has no weekly schedule")

# markdown-lint.yml: the config file the reusable workflow reads must exist.
markdown = jobs_using(workflows.get(".github/workflows/markdown-lint.yml", {}), "markdown-lint.yml@v2.9.1")
for job in markdown.values():
    config_file = (job.get("with") or {}).get("config-file", ".markdownlint.json")
    if not os.path.isfile(config_file):
        fail(f"markdown-lint.yml reads {config_file}, which does not exist")

# pr-lint.yml reads commitlint.config.mjs of the repository.
if not os.path.isfile("commitlint.config.mjs"):
    fail("commitlint.config.mjs does not exist (the pr-lint workflow reads it)")

# renovate.json extends the central preset with the maintainer team.
try:
    renovate = json.load(open("renovate.json", encoding="utf-8"))
    presets = " ".join(renovate.get("extends", []))
    if "OneLiteFeatherNET/renovate:default(OneLiteFeatherNET/menura-maintainers)" not in presets:
        fail("renovate.json does not extend the central preset with OneLiteFeatherNET/menura-maintainers")
except (OSError, json.JSONDecodeError) as error:
    fail(f"renovate.json is not readable JSON: {error}")

if problems:
    for message in problems:
        print(f"FAIL: {message}", file=sys.stderr)
    sys.exit(1)
print("PASS: workflow structure checks")
PY
