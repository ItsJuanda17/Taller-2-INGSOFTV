"""
CircleGuard – performance & stress test suite.

Run locally (web UI on http://localhost:8089):

    pip install -r tests/performance/requirements.txt
    locust -f tests/performance/locustfile.py

Run headless (used from the Jenkins pipeline):

    locust -f tests/performance/locustfile.py --headless \\
           -u 50 -r 10 -t 2m --csv results

Service URLs default to localhost ports that match the local
gradlew bootRun setup. Override via CIRCLEGUARD_<SERVICE>_URL env vars
when pointing at the K8s cluster.
"""
import json
import os
import time
import uuid
from typing import List

import jwt  # PyJWT
from locust import HttpUser, between, events, task

# -----------------------------------------------------------------------------
# Configuration: per-service base URLs are resolved from env vars at startup.
# CIRCLEGUARD_<NAME>_URL pattern matches the env vars Jenkins / K8s set.
# -----------------------------------------------------------------------------
AUTH_URL = os.getenv("CIRCLEGUARD_AUTH_SERVICE_URL", "http://localhost:8180")
IDENTITY_URL = os.getenv("CIRCLEGUARD_IDENTITY_SERVICE_URL", "http://localhost:8083")
PROMOTION_URL = os.getenv("CIRCLEGUARD_PROMOTION_SERVICE_URL", "http://localhost:8088")
DASHBOARD_URL = os.getenv("CIRCLEGUARD_DASHBOARD_SERVICE_URL", "http://localhost:8084")
GATEWAY_URL = os.getenv("CIRCLEGUARD_GATEWAY_SERVICE_URL", "http://localhost:8087")

# Must match jwt.secret in services' application.yml. Same default the dev
# config uses; pipeline overrides via the JWT_SECRET env var.
JWT_SECRET = os.getenv(
    "JWT_SECRET", "my-super-secret-dev-key-32-chars-long-12345678"
)


def mint_jwt(anonymous_id: str, permissions: List[str]) -> str:
    """Sign a token in the same shape auth-service issues."""
    now = int(time.time())
    payload = {
        "sub": anonymous_id,
        "permissions": permissions,
        "iat": now,
        "exp": now + 3600,
    }
    return jwt.encode(payload, JWT_SECRET, algorithm="HS256")


# -----------------------------------------------------------------------------
# Each Locust user simulates one campus member doing a mix of activities.
# Weights are calibrated to peak-hour traffic: gate validations dominate,
# dashboards are read often, visitor registration is rare, building listings
# happen whenever someone opens the campus map.
# -----------------------------------------------------------------------------
class CampusMember(HttpUser):
    # `host` is required by HttpUser but we override per-task with absolute URLs.
    host = AUTH_URL
    wait_time = between(1, 3)

    def on_start(self):
        """Each simulated user gets a fresh anonymous ID + JWT."""
        self.anonymous_id = str(uuid.uuid4())
        self.user_jwt = mint_jwt(self.anonymous_id, ["ROLE_STUDENT"])
        self.qr_token = None  # cached after first /auth/qr/generate call

    # ------------------------------------------------------------------- task 1
    @task(5)
    def validate_qr(self):
        """Heaviest weight: gate validations during the morning rush."""
        if not self.qr_token:
            # Lazily fetch a QR token from auth-service once per user; reuse
            # it across many gate validations (mirrors real-world behavior:
            # a student scans the same QR multiple times in a session).
            with self.client.get(
                f"{AUTH_URL}/api/v1/auth/qr/generate",
                headers={"Authorization": f"Bearer {self.user_jwt}"},
                name="auth /qr/generate",
                catch_response=True,
            ) as resp:
                if resp.status_code == 200:
                    self.qr_token = resp.json().get("qrToken")
                else:
                    resp.failure(f"qr/generate -> {resp.status_code}")
                    return

        with self.client.post(
            f"{GATEWAY_URL}/api/v1/gate/validate",
            json={"token": self.qr_token},
            name="gateway /gate/validate",
            catch_response=True,
        ) as resp:
            if resp.status_code != 200:
                resp.failure(f"unexpected status {resp.status_code}")

    # ------------------------------------------------------------------- task 2
    @task(3)
    def fetch_health_board(self):
        """Dashboard hit. Internally fans out to promotion-service."""
        self.client.get(
            f"{DASHBOARD_URL}/api/v1/analytics/health-board",
            name="dashboard /health-board",
        )

    # ------------------------------------------------------------------- task 3
    @task(2)
    def list_buildings(self):
        """Public catalog read; safe under heavy concurrency (read-only)."""
        self.client.get(
            f"{PROMOTION_URL}/api/v1/buildings",
            name="promotion /buildings",
        )

    # ------------------------------------------------------------------- task 4
    @task(1)
    def register_visitor(self):
        """Write-heavy path: hits Postgres + the encryption converter."""
        payload = {
            "name": f"Locust Guest {uuid.uuid4()}",
            "email": f"locust-{uuid.uuid4()}@example.org",
            "reason_for_visit": "load test",
        }
        self.client.post(
            f"{IDENTITY_URL}/api/v1/identities/visitor",
            json=payload,
            name="identity /visitor",
        )


# -----------------------------------------------------------------------------
# Hook: print a one-line summary at run end. Useful in Jenkins logs.
# -----------------------------------------------------------------------------
@events.quitting.add_listener
def _print_summary(environment, **_):
    stats = environment.stats.total
    summary = {
        "total_requests": stats.num_requests,
        "total_failures": stats.num_failures,
        "median_response_time_ms": stats.median_response_time,
        "p95_response_time_ms": stats.get_response_time_percentile(0.95),
        "p99_response_time_ms": stats.get_response_time_percentile(0.99),
        "avg_response_time_ms": round(stats.avg_response_time, 1),
        "rps": round(stats.total_rps, 2),
        "error_rate_pct": round(stats.fail_ratio * 100, 2),
    }
    print("LOCUST_SUMMARY=" + json.dumps(summary))
