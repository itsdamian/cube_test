#!/usr/bin/env bash
# Sealed Secrets helpers (spec k8s-gitops-cicd, requirement 5).
#
#   scripts/seal-secret.sh <namespace> <name> <key>=<value> [<key>=<value> ...] > sealed.yaml
#       Encrypt a Secret for this cluster's controller; the output is safe to commit.
#       Values are read from the arguments - prefer <key>=@file to keep them out of shell history.
#   scripts/seal-secret.sh --backup-key [file]
#       Save the controller's private key OUTSIDE the repository (default
#       ~/cube-secrets/sealed-secrets-key.yaml). scripts/cluster-up.sh restores it, so committed
#       SealedSecrets still decrypt after the cluster is rebuilt. Never commit this file.
set -euo pipefail

CONTROLLER=(--controller-namespace sealed-secrets --controller-name sealed-secrets-controller)

if [[ "${1:-}" == --backup-key ]]; then
  out="${2:-$HOME/cube-secrets/sealed-secrets-key.yaml}"
  mkdir -p "$(dirname "$out")"
  umask 077
  kubectl get secret -n sealed-secrets -l sealedsecrets.bitnami.com/sealed-secrets-key -o yaml > "$out"
  echo "private key saved to $out (keep it outside the repository)"
  exit 0
fi

if (( $# < 3 )); then
  sed -n '2,12p' "$0" >&2
  exit 2
fi
namespace="$1" name="$2"
shift 2
literals=()
for pair in "$@"; do
  key="${pair%%=*}" value="${pair#*=}"
  if [[ "$value" == @* ]]; then
    literals+=("--from-file=$key=${value#@}")
  else
    literals+=("--from-literal=$key=$value")
  fi
done
kubectl create secret generic "$name" -n "$namespace" "${literals[@]}" --dry-run=client -o yaml \
  | kubeseal "${CONTROLLER[@]}" --format yaml
