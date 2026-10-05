#!/usr/bin/env bash
# Unit tests for the Application health check in deploy/platform/values/argocd.yaml (the Lua that
# makes the root Application wait for each child before the next sync wave; also in CI).
#
#   scripts/test-argocd-health.sh
#
# Each fixture in deploy/platform/tests/application-health/ starts with "# expect: <status>". The
# Lua is taken from the Helm values, put into an argocd-cm, and evaluated by the same Argo CD
# version the cluster runs (argocd admin settings resource-overrides health). Only Docker is needed.
set -euo pipefail

ARGOCD_IMAGE=quay.io/argoproj/argocd:v3.5.3@sha256:dd3f47d5a5e4da563a7a398506e892481b358a7cec50abdf320c71aa55904bfa

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FIXTURES="$ROOT/deploy/platform/tests/application-health"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# The block scalar under resource.customizations.health.argoproj.io_Application (indented 6).
python3 - "$ROOT/deploy/platform/values/argocd.yaml" > "$WORK/argocd-cm.yaml" <<'EOF'
import sys
key = "resource.customizations.health.argoproj.io_Application: |"
lines = open(sys.argv[1]).read().splitlines()
start = next(i for i, l in enumerate(lines) if l.strip() == key) + 1
body = []
for line in lines[start:]:
    if line.strip() and not line.startswith("      "):
        break
    body.append(line[6:])
print("apiVersion: v1\nkind: ConfigMap\nmetadata: {name: argocd-cm, namespace: argocd}\ndata:")
print("  resource.customizations.health.argoproj.io_Application: |")
for line in body:
    print(("    " + line) if line else "")
EOF

cp "$FIXTURES"/*.yaml "$WORK/"
# The argocd image runs as a non-root user: let it read the (mktemp, 700) directory.
chmod 755 "$WORK"; chmod 644 "$WORK"/*.yaml
# One container for all fixtures; prints "<file> <status>" per fixture.
results=$(docker run --rm -v "$WORK:/work:ro" -w /work --entrypoint sh "$ARGOCD_IMAGE" -c '
  for f in [0-9]*.yaml; do
    status=$(argocd admin settings resource-overrides health "$f" --argocd-cm-path argocd-cm.yaml 2>/dev/null \
      | sed -n "s/^STATUS: //p")
    echo "$f ${status:-error}"
  done')

failed=0
while read -r file got; do
  want=$(sed -n 's/^# expect: //p' "$FIXTURES/$file")
  if [[ "$got" == "$want" ]]; then
    echo "ok    ${file%.yaml}: $got"
  else
    echo "FAIL  ${file%.yaml}: expected $want, got $got"; failed=1
  fi
done <<< "$results"
[[ $(grep -c . <<< "$results") -eq $(find "$FIXTURES" -name "*.yaml" | wc -l) ]] || { echo "FAIL  not every fixture was evaluated"; failed=1; }
exit "$failed"
