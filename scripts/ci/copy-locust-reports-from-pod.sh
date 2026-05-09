#!/bin/sh
# Copia informes Locust desde el pod (vivo gracias al `sleep infinity` del wrapper)
# hacia ./locust-reports/.
# Uso: sh scripts/ci/copy-locust-reports-from-pod.sh <kubeconfig> <namespace> <pod> <stage|master>

set -e

KUBECONFIG="$1"
NAMESPACE="$2"
POD="$3"
PREFIX="$4"

if [ -z "$KUBECONFIG" ] || [ -z "$NAMESPACE" ] || [ -z "$POD" ] || [ -z "$PREFIX" ]; then
  echo "Usage: $0 <kubeconfig> <namespace> <pod-name> <stage|master>" >&2
  exit 2
fi

case "$PREFIX" in
  stage|master) ;;
  *)
    echo "PREFIX must be stage or master" >&2
    exit 2
    ;;
esac

OUT=locust-reports
mkdir -p "$OUT"

sleep 2
CTR="$(kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" get pod "$POD" -o jsonpath='{.spec.containers[0].name}' 2>/dev/null || true)"

copy_req() {
  rem="$1"
  loc="$2"
  if [ -n "$CTR" ]; then
    kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" cp -c "$CTR" "${POD}:${rem}" "$loc"
  else
    kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" cp "${POD}:${rem}" "$loc"
  fi
}

copy_opt() {
  rem="$1"
  loc="$2"
  if [ -n "$CTR" ]; then
    kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" cp -c "$CTR" "${POD}:${rem}" "$loc" || true
  else
    kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" cp "${POD}:${rem}" "$loc" || true
  fi
}

copy_req "/tmp/${PREFIX}.html" "${OUT}/${PREFIX}.html"
copy_opt "/tmp/${PREFIX}_stats.csv" "${OUT}/${PREFIX}_stats.csv"
copy_opt "/tmp/${PREFIX}_failures.csv" "${OUT}/${PREFIX}_failures.csv"
copy_opt "/tmp/${PREFIX}_stats_history.csv" "${OUT}/${PREFIX}_stats_history.csv"

if [ ! -s "${OUT}/${PREFIX}.html" ]; then
  echo "ERROR: ${OUT}/${PREFIX}.html missing or empty after kubectl cp." >&2
  kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" get pod "$POD" -o wide || true
  kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" logs "$POD" --tail=120 || true
  exit 1
fi

echo "OK: wrote ${OUT}/${PREFIX}.html ($(wc -c < "${OUT}/${PREFIX}.html") bytes)"
