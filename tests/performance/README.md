# Performance & stress tests (`tests/performance`)

Locust suite that simulates a campus-day mix of CircleGuard traffic. The
weights on each task are calibrated to the real shape of peak-hour load:

| Task | Weight | What it exercises |
|---|---|---|
| `validate_qr` | 5 | gateway `/gate/validate` (cached QR token, hot path) |
| `fetch_health_board` | 3 | dashboard → promotion fan-out |
| `list_buildings` | 2 | promotion `/buildings` (read-only catalog) |
| `register_visitor` | 1 | identity `/visitor` (Postgres write + encryption) |

Two user classes are defined:

- `CampusMember` — realistic think-time (1-3 s between actions).
- `StressUser` — same tasks but **no** wait, used for spike runs.

## Local run

```powershell
# 1. Install Python deps in a venv
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r tests\performance\requirements.txt

# 2. Make sure the services are up (see tests/e2e/README.md for options).

# 3. Launch Locust web UI on http://localhost:8089
locust -f tests\performance\locustfile.py
```

In the web UI, set "Number of users" + "Spawn rate" and hit *Start swarming*.
The stats tab shows live RPS, percentiles and failures.

## Headless run (CI / Jenkins)

```powershell
# Smoke-style profile: 50 users, 10/s ramp-up, 2 minutes
locust -f tests\performance\locustfile.py --headless `
       -u 50 -r 10 -t 2m `
       --csv tests\performance\results\smoke `
       --html tests\performance\results\smoke.html

# Stress profile: 200 users, 50/s ramp-up, 5 minutes, no think-time
locust -f tests\performance\locustfile.py --headless `
       --tags stress -u 200 -r 50 -t 5m `
       --csv tests\performance\results\stress `
       --html tests\performance\results\stress.html
```

The CSV outputs (`*_stats.csv`, `*_failures.csv`, `*_stats_history.csv`)
are what the Bloque D pipeline archives; the HTML is for human review.

## Containerised run (used by Jenkins)

The bundled Dockerfile builds the suite into an image so the pipeline does
not depend on a Python environment on the agent:

```powershell
docker build -f tests\performance\Dockerfile -t circleguard-locust:local .

# Run against services exposed on the host network
docker run --rm --network=host `
  -e CIRCLEGUARD_AUTH_SERVICE_URL=http://localhost:8180 `
  -e CIRCLEGUARD_IDENTITY_SERVICE_URL=http://localhost:8083 `
  -e CIRCLEGUARD_PROMOTION_SERVICE_URL=http://localhost:8088 `
  -e CIRCLEGUARD_DASHBOARD_SERVICE_URL=http://localhost:8084 `
  -e CIRCLEGUARD_GATEWAY_SERVICE_URL=http://localhost:8087 `
  -v ${PWD}\locust-reports:/reports `
  circleguard-locust:local `
  -u 50 -r 10 -t 1m --csv /reports/run --html /reports/run.html
```

## Service URLs

All URLs come from environment variables matching the names baked into the
Kubernetes ConfigMap (`infra/k8s/01-configmap.yaml`):

| Variable | Default |
|---|---|
| `CIRCLEGUARD_AUTH_SERVICE_URL` | `http://localhost:8180` |
| `CIRCLEGUARD_IDENTITY_SERVICE_URL` | `http://localhost:8083` |
| `CIRCLEGUARD_PROMOTION_SERVICE_URL` | `http://localhost:8088` |
| `CIRCLEGUARD_DASHBOARD_SERVICE_URL` | `http://localhost:8084` |
| `CIRCLEGUARD_GATEWAY_SERVICE_URL` | `http://localhost:8087` |
| `JWT_SECRET` | `my-super-secret-dev-key-32-chars-long-12345678` |

## Reading the results

A trailing line `LOCUST_SUMMARY={...}` is printed on shutdown. Jenkins
parses it to fail the build when key thresholds are crossed (see
Bloque D pipeline). Key fields:

- `p95_response_time_ms` / `p99_response_time_ms` — latency tails
- `rps` — sustained throughput
- `error_rate_pct` — share of failed requests
- `total_requests` / `total_failures` — raw counters

Sensible thresholds for the dev cluster (local Docker Desktop):
`p95 < 500 ms`, `error_rate_pct < 1%`. For stage these tighten;
for master they tighten further.
