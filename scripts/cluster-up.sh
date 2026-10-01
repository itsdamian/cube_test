#!/usr/bin/env bash
# Create the local Kubernetes cluster and install the platform (spec k8s-gitops-cicd).
#
#   scripts/cluster-up.sh [--mode=gitops|direct]
#
#   --mode=gitops  (default) install Argo CD, then let it deploy everything from the main branch
#                  (deploy/argocd/root.yaml -> deploy/argocd/apps, generated from components.tsv).
#   --mode=direct  install the platform components of deploy/platform/components.tsv directly with
#                  helm / kubectl - same versions and values, no GitHub needed (local verification
#                  before the repository is pushed). The cube-* applications are not installed.
#
# The reported time INCLUDES downloading every image (stricter than AC1, which excludes downloads).
# Pre-pulling into the host and importing was dropped (task 7): Docker Desktop's containerd image
# store keeps only the host platform of multi-platform images, which k3s cannot import.
#
# Requires: docker, k3d, kubectl, helm (v4). Delete everything again: scripts/cluster-down.sh
# Written for macOS' bash 3.2 (no mapfile; empty arrays are never expanded under set -u).
set -euo pipefail

MODE=gitops
for arg in "$@"; do
  case "$arg" in
    --mode=gitops|--mode=direct) MODE="${arg#--mode=}" ;;
    -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLUSTER=cube
COMPONENTS="$ROOT/deploy/platform/components.tsv"
SEALED_KEY_BACKUP="${SEALED_SECRETS_KEY_BACKUP:-$HOME/cube-secrets/sealed-secrets-key.yaml}"

log() { printf '\n==> %s\n' "$*"; }
seconds() { date +%s; }

for tool in docker k3d kubectl helm; do
  command -v "$tool" >/dev/null || { echo "missing tool: $tool (see docs/kubernetes.md)" >&2; exit 1; }
done
if k3d cluster list "$CLUSTER" >/dev/null 2>&1; then
  echo "cluster '$CLUSTER' already exists - run scripts/cluster-down.sh first" >&2
  exit 1
fi

build_start=$(seconds)
log "creating k3d cluster '$CLUSTER'"
k3d cluster create --config "$ROOT/deploy/k3d/cluster.yaml"
# --- Sealed Secrets key: reuse the backed-up key so committed SealedSecrets still decrypt -------
if [[ -f "$SEALED_KEY_BACKUP" ]]; then
  log "restoring the Sealed Secrets key from $SEALED_KEY_BACKUP"
  kubectl create namespace sealed-secrets
  kubectl apply -n sealed-secrets -f "$SEALED_KEY_BACKUP"
else
  echo "(no Sealed Secrets key backup at $SEALED_KEY_BACKUP - a new key will be generated; see docs/kubernetes.md)"
fi

# Grafana reads its admin password from the Secret "grafana-admin" (values: grafana.admin.
# existingSecret). It comes from the SealedSecret in deploy/platform/secrets, which only this
# cluster's key can decrypt. Without a key backup that cannot work, so a random password is
# generated instead (re-seal and commit to make it permanent: docs/kubernetes.md).
if [[ ! -f "$SEALED_KEY_BACKUP" ]]; then
  kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
  kubectl create secret generic grafana-admin -n monitoring \
    --from-literal=admin-user=admin --from-literal=admin-password="$(openssl rand -base64 18)"
  echo "(Grafana admin password generated for this cluster only - the committed SealedSecret needs the backed-up key)"
fi

# --- install ---------------------------------------------------------------------------------
helm_install() {  # name namespace repo version chart values
  helm upgrade --install "$1" "$5" --repo "$3" --version "$4" \
    --namespace "$2" --create-namespace \
    --values "$ROOT/deploy/platform/values/$6" \
    --wait --timeout 10m
}

if [[ "$MODE" == gitops ]]; then
  IFS=$'\t' read -r _ ns _ _ repo version chart values _ < <(awk -F'\t' '$1=="argocd"' "$COMPONENTS")
  log "installing Argo CD $version"
  helm_install argocd "$ns" "$repo" "$version" "$chart" "$values"
  log "applying the root Application (Argo CD deploys the rest from the main branch)"
  kubectl apply -f "$ROOT/deploy/argocd/root.yaml"
  log "waiting for every Application to be Synced and Healthy (up to 25 min)"
  deadline=$(( $(seconds) + 1500 ))
  while :; do
    status=$(kubectl get applications -n argocd \
      -o jsonpath='{range .items[*]}{.metadata.name}{"="}{.status.sync.status}{"/"}{.status.health.status}{"\n"}{end}' 2>/dev/null || true)
    total=$(grep -c . <<<"$status" || true)
    ready=$(grep -c '=Synced/Healthy$' <<<"$status" || true)
    printf '\r%s/%s applications Synced/Healthy ' "$ready" "$total"
    if (( total > 1 && ready == total )); then echo; break; fi
    if (( $(seconds) > deadline )); then echo; echo "$status"; echo "timed out" >&2; exit 1; fi
    sleep 10
  done
else
  # Install in sync-wave order; the cube-* applications are deployed separately (local images).
  while IFS=$'\t' read -r name ns _wave type source ref path values _options; do
    [[ -z "$name" || "$name" == \#* || "$name" == cube-* ]] && continue
    log "installing $name"
    case "$type" in
      helm) helm_install "$name" "$ns" "$source" "$ref" "$path" "$values" ;;
      git)
        # Same as Argo CD: a directory with a kustomization is built with kustomize, any other
        # directory is applied as plain manifests (e.g. gateway-api's config/crd/standard).
        if [[ "$source" == self ]]; then
          dir="$ROOT/$path"
        else
          checkout="$(mktemp -d)"
          git clone --quiet --depth 1 --branch "$ref" "$source" "$checkout"
          dir="$checkout/$path"
        fi
        if [[ -f "$dir/kustomization.yaml" ]]; then
          kubectl apply --server-side -k "$dir"
        else
          kubectl apply --server-side -f "$dir"
        fi ;;
    esac
  done < <(grep -v '^#' "$COMPONENTS" | sort -t$'\t' -k3,3n -s)
fi

log "cluster ready in $(( $(seconds) - build_start )) s (mode: $MODE)"
cat <<EOF
  Argo CD : http://argocd.localhost   user admin, password:
            kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d
  Grafana : http://grafana.localhost  user admin, password:
            kubectl -n monitoring get secret grafana-admin -o jsonpath='{.data.admin-password}' | base64 -d
  cube    : http://cube.localhost (prod)   http://dev.cube.localhost (dev)
EOF
