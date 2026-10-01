#!/usr/bin/env bash
# Delete the local cluster and ALL its data (Kafka, PostgreSQL, Prometheus volumes live inside it).
#
#   scripts/cluster-down.sh
#
# Back up the Sealed Secrets key first if committed SealedSecrets must keep working on the next
# cluster (docs/kubernetes.md):
#   scripts/seal-secret.sh --backup-key
set -euo pipefail
k3d cluster delete cube
