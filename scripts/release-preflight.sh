#!/usr/bin/env bash
# Release checks (spec k8s-gitops-cicd, task 17; used by .github/workflows/release.yml, tested by
# scripts/test-release-preflight.sh).
#
#   scripts/release-preflight.sh <tag-commit> [overlay-file]
#
#   1. the tagged commit must be on main ($MAIN_REF, default origin/main);
#   2. the images dev runs at that commit (overlays/dev - the file given, or the one in the tagged
#      commit) - tags are often put on a deploy-only or docs commit with no image of its own;
#   3. the commit those images were built from must have passed CI (ci-ok success).
#
# Prints the images; with $GITHUB_OUTPUT set, also writes backend_tag / backend_digest /
# frontend_tag / frontend_digest there. Exit 0 = release may go ahead, 1 = refused (::error::).
#
#   VERSION        tag name, for messages (default: the commit)
#   REGISTRY       default ghcr.io/itsdamian
#   CI_STATUS_CMD  prints the ci-ok conclusion of a full commit sha (success / failure / missing);
#                  default asks GitHub (gh api, needs GH_TOKEN and GITHUB_REPOSITORY). Tests use a stub.
set -euo pipefail

[[ $# -ge 1 ]] || { echo "usage: $0 <tag-commit> [overlay-file]" >&2; exit 2; }
TAG_COMMIT="$1"
OVERLAY_PATH=deploy/apps/cube/overlays/dev/kustomization.yaml
MAIN_REF="${MAIN_REF:-origin/main}"
VERSION="${VERSION:-$TAG_COMMIT}"
export REGISTRY="${REGISTRY:-ghcr.io/itsdamian}"

ci_ok_from_github() {
  gh api "repos/$GITHUB_REPOSITORY/commits/$1/check-runs?check_name=ci-ok" \
    --jq '[.check_runs[] | select(.app.slug == "github-actions") | .conclusion] | first // "missing"'
}

# 1. The tag must be on main.
git merge-base --is-ancestor "$TAG_COMMIT" "$MAIN_REF" 2>/dev/null \
  || { echo "::error::$VERSION ($TAG_COMMIT) is not on main"; exit 1; }

# 2. Images dev runs at this commit, and the commit they were built from.
overlay="$(mktemp)"
trap 'rm -f "$overlay"' EXIT
if [[ $# -ge 2 ]]; then
  cp "$2" "$overlay" 2>/dev/null || { echo "::error::overlay $2 not found"; exit 1; }
else
  git show "$TAG_COMMIT:$OVERLAY_PATH" > "$overlay" 2>/dev/null \
    || { echo "::error::$OVERLAY_PATH not found at $TAG_COMMIT"; exit 1; }
fi
images=$(python3 - "$overlay" <<'EOF'
import os, re, sys
try:
    text = open(sys.argv[1]).read()
    blocks = re.split(r"\n(?=- )", text.split("images:", 1)[1])
    registry = os.environ["REGISTRY"]
    for image in ("cube-backend", "cube-frontend"):
        block = next(b for b in blocks if re.search(rf"name: {re.escape(registry)}/{image}\s", b + "\n"))
        tag = re.search(r"newTag: (\S+)", block).group(1)
        digest = re.search(r"digest: (\S+)", block).group(1)
        assert tag.startswith("sha-") and digest.startswith("sha256:"), (image, tag, digest)
        print(image.removeprefix("cube-"), tag, digest)
except Exception as e:
    print(f"::error::cannot read the images from overlays/dev: {e!r}")
    sys.exit(1)
EOF
) || { echo "$images"; exit 1; }
while read -r key tag digest; do
  echo "cube-$key: $tag@$digest"
  [[ -z "${GITHUB_OUTPUT:-}" ]] || printf '%s_tag=%s\n%s_digest=%s\n' "$key" "$tag" "$key" "$digest" >> "$GITHUB_OUTPUT"
done <<< "$images"

# 3. Those images passed CI (ci-ok on their commit).
while read -r tag; do
  # Not found also after a rewrite of main's history: the image's commit no longer exists.
  commit=$(git rev-parse --verify --quiet "${tag#sha-}^{commit}") \
    || { echo "::error::images $tag: commit ${tag#sha-} is not in the repository's history (rewritten?). Release only after CI has rebuilt the images and the dev bump PR has merged, then tag again."; exit 1; }
  result=$(${CI_STATUS_CMD:-ci_ok_from_github} "$commit" < /dev/null)
  echo "$tag ($commit): ci-ok $result"
  [[ "$result" == success ]] || { echo "::error::images $tag did not pass CI"; exit 1; }
done < <(awk '{print $2}' <<< "$images" | sort -u)
