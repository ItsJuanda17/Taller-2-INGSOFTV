# End-to-End tests (`tests/e2e`)

REST Assured tests that drive the running CircleGuard services across the
HTTP boundary. They cover **5 distinct user-facing flows**:

| File | Flow under test | Services touched |
|---|---|---|
| `VisitorRegistrationE2ETest` | Guest sign-in returns a deterministic anonymous UUID | identity |
| `QrToGateE2ETest` | Auth issues QR → gateway grants/denies entry | auth → gateway (via Redis) |
| `HealthBoardE2ETest` | Dashboard fans out to promotion for aggregated stats | dashboard → promotion |
| `BuildingAdminE2ETest` | Non-admin gets 403 / admin can create-list-delete a building | promotion |
| `IdentityLookupAuthorizationE2ETest` | Vault lookup is gated by `identity:lookup` permission | identity |

These tests **assume the services are already running**. Each class has a
JUnit `@EnabledIf` guard that probes its primary service and skips silently
if the process is unreachable, so partial environments don't cause spurious
red builds.

## How URLs are resolved

| Variable | Default | Used by |
|---|---|---|
| `AUTH_URL` | `http://localhost:8180` | auth-service |
| `IDENTITY_URL` | `http://localhost:8083` | identity-service |
| `PROMOTION_URL` | `http://localhost:8088` | promotion-service |
| `DASHBOARD_URL` | `http://localhost:8084` | dashboard-service |
| `GATEWAY_URL` | `http://localhost:8087` | gateway-service |
| `NOTIFICATION_URL` | `http://localhost:8082` | notification-service |
| `JWT_SECRET` | `my-super-secret-dev-key-32-chars-long-12345678` | JWT minter |

Override any of them at runtime, e.g. when pointing at a stage cluster:

```powershell
$env:AUTH_URL  = "http://auth.circleguard.svc.cluster.local:8180"
$env:JWT_SECRET = (kubectl -n circleguard get secret circleguard-secrets -o jsonpath='{.data.JWT_SIGNING_KEY}' | base64 -d)
.\gradlew.bat :tests:e2e:test
```

## Running locally

You need the middleware (Postgres, Neo4j, Kafka, Redis) plus the six
services up. Two paths:

### Option A — services on your host (one terminal each)

```powershell
# 1. Middleware in K8s (already deployed by infra/k8s/10-middleware.yaml)
kubectl -n circleguard port-forward svc/postgres 5432:5432
kubectl -n circleguard port-forward svc/neo4j    7687:7687
kubectl -n circleguard port-forward svc/kafka    9092:9092
kubectl -n circleguard port-forward svc/redis    6379:6379

# 2. Each service in its own terminal:
.\gradlew.bat :services:circleguard-auth-service:bootRun
.\gradlew.bat :services:circleguard-identity-service:bootRun
.\gradlew.bat :services:circleguard-promotion-service:bootRun
.\gradlew.bat :services:circleguard-dashboard-service:bootRun
.\gradlew.bat :services:circleguard-gateway-service:bootRun
.\gradlew.bat :services:circleguard-notification-service:bootRun

# 3. Run the E2E suite
.\gradlew.bat :tests:e2e:test
```

### Option B — the whole stack already in Kubernetes

The Bloque D / E pipelines deploy every service into the `circleguard`
namespace. To run the suite against that cluster, port-forward the six
services (or set ingress URLs) and re-export the env vars listed above.

## What "skipped" means here

If a test class is marked `Skipped` in the report it means the JUnit
`@EnabledIf` probe couldn't reach the underlying service. That's by
design: the suite stays green when only some services are up, while still
catching real cross-service regressions when the whole stack runs.
