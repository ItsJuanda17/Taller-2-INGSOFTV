#!/bin/sh
# Smoke local: misma imagen y rutas /tmp/stage* que en Jenkins, pero ~30s de carga.
# Requisitos: Docker, kubectl, contexto apuntando al cluster (p. ej. docker-desktop),
#             namespace circleguard con servicios desplegados y secret circleguard-secrets.
#
# Desde la raíz del repo (Git Bash / WSL):
#   sh scripts/ci/run-locust-in-cluster-smoke.sh
#
# Variables opcionales: KUBECONFIG, NAMESPACE, IMAGE_TAG

set -e
ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"
NAMESPACE="${NAMESPACE:-circleguard}"
IMAGE_TAG="${IMAGE_TAG:-circleguard-locust:local-smoke}"
POD="locust-local-$(date +%s)"

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl no está en PATH" >&2
  exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "docker no está en PATH" >&2
  exit 1
fi

echo "Building $IMAGE_TAG ..."
docker build -f tests/performance/Dockerfile -t "$IMAGE_TAG" .

rm -rf locust-reports && mkdir -p locust-reports

JWT="$(kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" get secret circleguard-secrets -o jsonpath='{.data.JWT_SIGNING_KEY}' | base64 -d)"

kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" delete pod "$POD" --ignore-not-found

echo "Running Locust pod $POD (short run) ..."
kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" run "$POD" \
  --image="$IMAGE_TAG" \
  --image-pull-policy=IfNotPresent \
  --restart=Never \
  --env="JWT_SECRET=${JWT}" \
  --env="CIRCLEGUARD_AUTH_SERVICE_URL=http://auth-service.${NAMESPACE}.svc.cluster.local:8180" \
  --env="CIRCLEGUARD_IDENTITY_SERVICE_URL=http://identity-service.${NAMESPACE}.svc.cluster.local:8083" \
  --env="CIRCLEGUARD_PROMOTION_SERVICE_URL=http://promotion-service.${NAMESPACE}.svc.cluster.local:8088" \
  --env="CIRCLEGUARD_DASHBOARD_SERVICE_URL=http://dashboard-service.${NAMESPACE}.svc.cluster.local:8084" \
  --env="CIRCLEGUARD_GATEWAY_SERVICE_URL=http://gateway-service.${NAMESPACE}.svc.cluster.local:8087" \
  --command -- \
  sh -c "cd /locust && locust -f locustfile.py --headless -u 3 -r 1 -t 30s --csv /tmp/stage --html /tmp/stage.html; echo \$? > /tmp/exit_code; sleep infinity"

kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" wait --for=condition=Ready "pod/$POD" --timeout=2m

kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" logs -f "$POD" &
LOG_PID=$!

while ! kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" exec "$POD" -- test -f /tmp/exit_code 2>/dev/null; do
  sleep 3
done

kill "$LOG_PID" 2>/dev/null || true
wait "$LOG_PID" 2>/dev/null || true

EXIT="$(kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" exec "$POD" -- cat /tmp/exit_code)"

sh scripts/ci/copy-locust-reports-from-pod.sh "$KUBECONFIG" "$NAMESPACE" "$POD" stage

kubectl --kubeconfig="$KUBECONFIG" -n "$NAMESPACE" delete pod "$POD" --ignore-not-found

echo "--- locust-reports ---"
ls -la locust-reports

test -s locust-reports/stage.html
exit "$EXIT"
