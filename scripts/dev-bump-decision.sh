#!/usr/bin/env bash
# May dev move to the images built from <new-sha>? (spec k8s-gitops-cicd, task 16; used by
# .github/workflows/deploy-dev.yml, tested by scripts/test-dev-bump-decision.sh).
#
#   scripts/dev-bump-decision.sh <new-sha> <current-tag> [main-ref]
#
#   new-sha      the commit CI built the images from (workflow_run.head_sha)
#   current-tag  the tag dev runs now, from overlays/dev (sha-<7>)
#   main-ref     default origin/main
#
# Prints ONE line and always exits 0 for a decision (exit 2 = bad usage):
#   bump                  new is on main and newer than what dev runs
#   bump-unknown-current  new is on main, but dev's commit is not in the history (main was
#                         rewritten): allowed, otherwise dev would never update again
#   skip: <reason>        new is not on main, is the same commit, or is older than dev's commit -
#                         e.g. a manual "Re-run" of an old Deploy dev run must not downgrade dev
#
# Run inside a clone with the full history of main-ref (actions/checkout fetch-depth: 0).
set -euo pipefail

[[ $# -ge 2 ]] || { echo "usage: $0 <new-sha> <current-tag> [main-ref]" >&2; exit 2; }
new_sha="$1"
current_tag="$2"
main_ref="${3:-origin/main}"

if ! new=$(git rev-parse --verify --quiet "$new_sha^{commit}") \
    || ! git merge-base --is-ancestor "$new" "$main_ref"; then
  echo "skip: ${new_sha:0:7} is not on $main_ref (re-run from another branch, or a rewritten commit)"
  exit 0
fi

current_short="${current_tag#sha-}"
if [[ "$current_tag" != sha-* ]] || ! current=$(git rev-parse --verify --quiet "$current_short^{commit}") \
    || ! git merge-base --is-ancestor "$current" "$main_ref"; then
  echo "bump-unknown-current"
  exit 0
fi

if [[ "$new" == "$current" ]]; then
  echo "skip: dev already runs ${new:0:7}"
elif git merge-base --is-ancestor "$current" "$new"; then
  echo "bump"
else
  echo "skip: ${new:0:7} is older than ${current:0:7}, which dev runs now (re-run of an old run?)"
fi
