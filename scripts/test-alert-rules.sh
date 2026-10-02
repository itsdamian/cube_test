#!/usr/bin/env bash
# Unit-test the Prometheus alert rules with promtool (spec k8s-gitops-cicd, task 5; also in CI).
#
#   scripts/test-alert-rules.sh
#
# The rules live in a PrometheusRule (deploy/monitoring/cube-alerts.yaml) so the Prometheus
# Operator picks them up; promtool needs a plain rules file, so its .spec is extracted first.
# Then `promtool check rules` (syntax) and `promtool test rules` (behaviour, incl. a missing
# series) run in the official Prometheus image. Only Docker is required.
set -euo pipefail

PROMETHEUS_IMAGE=prom/prometheus:v3.15.0
PYTHON_IMAGE=python:3.13-slim

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/.cache/alert-rules"
mkdir -p "$WORK"
cp "$ROOT/deploy/monitoring/cube-alerts.test.yaml" "$WORK/"

docker run --rm -v "$ROOT/deploy/monitoring:/src:ro" -v "$WORK:/work" "$PYTHON_IMAGE" sh -c '
  pip install -q pyyaml >/dev/null 2>&1 && python - <<PY
import yaml
rule = yaml.safe_load(open("/src/cube-alerts.yaml"))
assert rule["kind"] == "PrometheusRule", rule["kind"]
yaml.safe_dump(rule["spec"], open("/work/cube-alerts.rules.yaml", "w"), allow_unicode=True, sort_keys=False)
PY'

docker run --rm --entrypoint promtool -v "$WORK:/work" "$PROMETHEUS_IMAGE" check rules /work/cube-alerts.rules.yaml
docker run --rm --entrypoint promtool -v "$WORK:/work" "$PROMETHEUS_IMAGE" test rules /work/cube-alerts.test.yaml
