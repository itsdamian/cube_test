#!/usr/bin/env bash
# Tests for scripts/release-preflight.sh in a throw-away git repository, with a stub instead of the
# GitHub ci-ok lookup (also in CI).
#
#   scripts/test-release-preflight.sh
#
#   main:   A - B - C        ci-ok: A success, B failure, C success (C also has overlays/dev -> A)
#   other:       \- D        (never merged)
set -euo pipefail

PREFLIGHT="$(cd "$(dirname "$0")" && pwd)/release-preflight.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
REPO="$WORK/repo"
mkdir "$REPO"
cd "$REPO"

git init -q -b main
git config user.name test
git config user.email test@example.invalid
commit() { git commit -q --allow-empty -m "$1"; git rev-parse HEAD; }

overlay() {   # overlay <backend-tag> <frontend-tag>
  cat <<EOF
images:
- digest: sha256:1111111111111111111111111111111111111111111111111111111111111111
  name: ghcr.io/itsdamian/cube-backend
  newName: ghcr.io/itsdamian/cube-backend
  newTag: $1
- digest: sha256:2222222222222222222222222222222222222222222222222222222222222222
  name: ghcr.io/itsdamian/cube-frontend
  newName: ghcr.io/itsdamian/cube-frontend
  newTag: $2
EOF
}

A=$(commit A); B=$(commit B)
mkdir -p deploy/apps/cube/overlays/dev
overlay "sha-${A:0:7}" "sha-${A:0:7}" > deploy/apps/cube/overlays/dev/kustomization.yaml
git add deploy; C=$(commit C)
git switch -q -c other "$B"; D=$(commit D); git switch -q main

# Stub for the GitHub lookup: A and C passed CI, everything else failed.
cat > "$WORK/ci-status" <<EOF
#!/usr/bin/env bash
case "\$1" in $A|$C) echo success ;; *) echo failure ;; esac
EOF
chmod +x "$WORK/ci-status"
export CI_STATUS_CMD="$WORK/ci-status" MAIN_REF=main

overlay "sha-${C:0:7}" "sha-${C:0:7}" > "$WORK/good.yaml"
overlay "sha-${B:0:7}" "sha-${B:0:7}" > "$WORK/failed-ci.yaml"
overlay "sha-${C:0:7}" "sha-${B:0:7}" > "$WORK/one-failed.yaml"
overlay "sha-0000000" "sha-0000000" > "$WORK/rewritten.yaml"
printf 'resources:\n- ../../base\n' > "$WORK/no-images.yaml"
overlay "v0.1.0" "v0.1.0" > "$WORK/not-sha.yaml"

failed=0
check() {   # check <name> <expected exit> <expected text> <args...>
  local name="$1" want_exit="$2" want_text="$3" out rc=0
  shift 3
  out=$("$PREFLIGHT" "$@" 2>&1) || rc=$?
  if [[ "$rc" == "$want_exit" && "$out" == *"$want_text"* ]]; then
    echo "ok    $name (exit $rc)"
  else
    echo "FAIL  $name: expected exit $want_exit with '$want_text', got exit $rc:"; printf '        %s\n' "${out//$'\n'/$'\n'        }"; failed=1
  fi
}

check "images passed CI"                  0 "ci-ok success"          "$C" "$WORK/good.yaml"
check "overlay from the tagged commit"    0 "sha-${A:0:7} (${A}): ci-ok success" "$C"
check "tag not on main"                   1 "is not on main"         "$D" "$WORK/good.yaml"
check "images did not pass CI"            1 "did not pass CI"        "$C" "$WORK/failed-ci.yaml"
check "one of the two images failed CI"   1 "did not pass CI"        "$C" "$WORK/one-failed.yaml"
check "image commit gone (rewritten)"     1 "not in the repository's history" "$C" "$WORK/rewritten.yaml"
check "overlay file missing"              1 "not found"              "$C" "$WORK/missing.yaml"
check "no overlay in the tagged commit"   1 "not found at"           "$B"
check "overlay without images"            1 "cannot read the images" "$C" "$WORK/no-images.yaml"
check "overlay tag not sha-<7>"           1 "cannot read the images" "$C" "$WORK/not-sha.yaml"

out_file="$WORK/github-output"
GITHUB_OUTPUT="$out_file" "$PREFLIGHT" "$C" "$WORK/good.yaml" > /dev/null
if grep -qx "backend_tag=sha-${C:0:7}" "$out_file" && grep -qx "frontend_digest=sha256:2222222222222222222222222222222222222222222222222222222222222222" "$out_file"; then
  echo "ok    writes the step outputs"
else
  echo "FAIL  step outputs:"; cat "$out_file"; failed=1
fi
exit "$failed"
