# =============================================================================
# CircleGuard - Locust runner image
# -----------------------------------------------------------------------------
# Used by the Jenkins performance stage to run Locust headless against the
# deployed services. Build context = repo root, mirroring the service Dockerfiles:
#
#   docker build -f tests/performance/Dockerfile \
#                -t circleguard-locust:local .
#
# Run headless (used inside the pipeline):
#   docker run --rm --network=host \
#     -e CIRCLEGUARD_AUTH_SERVICE_URL=http://auth-service:8180 \
#     ... (other CIRCLEGUARD_*_URL vars) \
#     -v ${PWD}/locust-reports:/reports \
#     circleguard-locust:local \
#     -u 50 -r 10 -t 2m --csv /reports/run --html /reports/run.html
# =============================================================================
FROM python:3.12-slim

WORKDIR /locust

# Install dependencies first so re-runs cache when the test code changes.
COPY tests/performance/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY tests/performance/locustfile.py .

# --headless is enforced via the entrypoint; everything after it is forwarded
# to locust as flags (e.g. -u, -r, -t, --csv).
ENTRYPOINT ["locust", "-f", "locustfile.py", "--headless"]
CMD ["-u", "20", "-r", "5", "-t", "1m"]
