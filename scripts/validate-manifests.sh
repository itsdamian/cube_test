#!/usr/bin/env bash
# Validate the Kubernetes manifests offline (spec k8s-gitops-cicd, task 4; also run in CI).
#
#   scripts/validate-manifests.sh
#
# 1. Renders deploy/apps/cube/overlays/{dev,prod}, deploy/platform/policies and
#    deploy/monitoring, deploy/platform/routes with kustomize, plus the Argo CD Applications
#    (checked to be up to date with deploy/platform/components.tsv).
# 2. Converts the CRDs of the operators we use (pinned versions below) into JSON schemas with
#    kubeconform's own openapi2jsonschema.py, so custom resources (Strimzi Kafka, CNPG Cluster,
#    Gateway API HTTPRoute, Prometheus Operator monitors) are checked as strictly as built-ins.
# 3. Runs kubeconform -strict against the Kubernetes version of the k3d cluster.
# Only Docker is required (kustomize, Python and kubeconform run in containers).
set -euo pipefail

K8S_VERSION=1.35.0                    # rancher/k3s v1.35.5 (deploy/k3d/cluster.yaml)
KUBECONFORM_IMAGE=ghcr.io/yannh/kubeconform:v0.8.0
KUSTOMIZE_IMAGE=registry.k8s.io/kustomize/kustomize:v5.8.0@sha256:98424842862ed35fa666dbaac02159623567e1e9e184d91382fa665e89023258
PYTHON_IMAGE=python:3.13-slim
STRIMZI_VERSION=1.2.0
CNPG_VERSION=1.30.1
GATEWAY_API_VERSION=v1.6.2
PROMETHEUS_OPERATOR_VERSION=v0.94.1
ARGOCD_VERSION=v3.5.3                 # app version of the argo-cd chart in components.tsv
SEALED_SECRETS_VERSION=v0.40.0        # app version of the sealed-secrets chart

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="$ROOT/.cache/manifest-schemas"
RENDERED="$ROOT/.cache/rendered"
mkdir -p "$CACHE/crds" "$CACHE/schemas" "$RENDERED"

crd_sources=(
  "strimzi-$STRIMZI_VERSION.yaml|https://github.com/strimzi/strimzi-kafka-operator/releases/download/$STRIMZI_VERSION/strimzi-crds-$STRIMZI_VERSION.yaml"
  "cnpg-$CNPG_VERSION.yaml|https://github.com/cloudnative-pg/cloudnative-pg/releases/download/v$CNPG_VERSION/cnpg-$CNPG_VERSION.yaml"
  "gateway-api-$GATEWAY_API_VERSION.yaml|https://github.com/kubernetes-sigs/gateway-api/releases/download/$GATEWAY_API_VERSION/standard-install.yaml"
  "sealed-secrets-$SEALED_SECRETS_VERSION.yaml|https://raw.githubusercontent.com/bitnami/sealed-secrets/$SEALED_SECRETS_VERSION/helm/sealed-secrets/crds/bitnami.com_sealedsecrets.yaml"
  "argocd-application-$ARGOCD_VERSION.yaml|https://raw.githubusercontent.com/argoproj/argo-cd/$ARGOCD_VERSION/manifests/crds/application-crd.yaml"
  "prometheus-operator-$PROMETHEUS_OPERATOR_VERSION.yaml|https://github.com/prometheus-operator/prometheus-operator/releases/download/$PROMETHEUS_OPERATOR_VERSION/stripped-down-crds.yaml"
)

# --- 1. CRD -> JSON schema (cached per pinned version) -------------------------------------
stamp="$CACHE/schemas/.versions"
wanted="fullgroup $STRIMZI_VERSION $CNPG_VERSION $GATEWAY_API_VERSION $PROMETHEUS_OPERATOR_VERSION $ARGOCD_VERSION $SEALED_SECRETS_VERSION"
if [[ ! -f "$stamp" || "$(cat "$stamp")" != "$wanted" ]]; then
  rm -rf "${CACHE:?}/schemas" "${CACHE:?}/crds" && mkdir -p "$CACHE/schemas" "$CACHE/crds"
  for entry in "${crd_sources[@]}"; do
    file="${entry%%|*}"; url="${entry#*|}"
    curl -fsSL --retry 3 -o "$CACHE/crds/$file" "$url"
  done
  curl -fsSL --retry 3 -o "$CACHE/openapi2jsonschema.py" \
    "https://raw.githubusercontent.com/yannh/kubeconform/v0.8.0/scripts/openapi2jsonschema.py"
  docker run --rm -v "$CACHE:/work" -w /work/schemas -e FILENAME_FORMAT='{kind}-{fullgroup}-{version}' \
    "$PYTHON_IMAGE" sh -c 'pip install -q pyyaml >/dev/null 2>&1 && python /work/openapi2jsonschema.py /work/crds/*.yaml >/dev/null'
  echo "$wanted" > "$stamp"
fi

# --- 2. render -----------------------------------------------------------------------------
render() { docker run --rm --platform linux/amd64 -v "$ROOT:/repo:ro" "$KUSTOMIZE_IMAGE" build "/repo/$1"; }
render deploy/apps/cube/overlays/dev  > "$RENDERED/cube-dev.yaml"
render deploy/apps/cube/overlays/prod > "$RENDERED/cube-prod.yaml"
render deploy/platform/policies       > "$RENDERED/policies.yaml"
render deploy/monitoring              > "$RENDERED/monitoring.yaml"
render deploy/platform/routes         > "$RENDERED/routes.yaml"
render deploy/platform/secrets        > "$RENDERED/secrets.yaml"
for f in "$ROOT"/deploy/argocd/apps/*.yaml "$ROOT/deploy/argocd/root.yaml"; do echo '---'; cat "$f"; done > "$RENDERED/argocd-apps.yaml"
# The Argo CD Applications must match deploy/platform/components.tsv (single source of truth).
python3 "$ROOT/scripts/gen-argocd-apps.py" --check

# --- 3. validate ---------------------------------------------------------------------------
docker run --rm -v "$CACHE/schemas:/schemas:ro" -v "$RENDERED:/rendered:ro" "$KUBECONFORM_IMAGE" \
  -strict -summary -output text \
  -kubernetes-version "$K8S_VERSION" \
  -schema-location default \
  -schema-location '/schemas/{{.ResourceKind}}-{{.Group}}-{{.ResourceAPIVersion}}.json' \
  /rendered/cube-dev.yaml /rendered/cube-prod.yaml /rendered/policies.yaml /rendered/monitoring.yaml /rendered/routes.yaml /rendered/secrets.yaml /rendered/argocd-apps.yaml
