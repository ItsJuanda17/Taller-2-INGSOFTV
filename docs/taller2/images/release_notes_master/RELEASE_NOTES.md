# CircleGuard — Release Notes

## v1.0.7 — 2026-05-09

_Changes since 1be23bb645f36818f69c53b47c8b698e74d3960c._

### Bug Fixes
- bump qr token expiration so it survives the auth-to-gateway hop (4ef97b0 — Juan Acevedo)
- make V2 flyway migration idempotent (6416b8a — Juan Acevedo)

### Tests
- scope testcontainers host override to jenkins so local windows runs hit localhost (377741d — Juan Acevedo)
- remove identity authorization assertions blocked by JWT validation bug (400199d — Juan Acevedo)
- drop reachability skips and run gradle through kubectl port-forward (4982595 — Juan Acevedo)
- relax promotion benchmark timing assertion for CI runs (e553fe2 — Juan Acevedo)
- route testcontainers port checks through host.docker.internal (4bea54b — Juan Acevedo)
- disable testcontainers ryuk inside the jenkins container (9852723 — Juan Acevedo)
- fix preexisting identity controller tests and harden Testcontainers on Linux (953482e — Juan Acevedo)
- fix preexisting promotion-service test failures (05f3625 — Juan Acevedo)
- add locust performance suite (aab94ac — Juan Acevedo)
- add e2e tests for cross-service user flows (b187fe8 — Juan Acevedo)
- add integration tests for inter-service communication (50eb30e — Juan Acevedo)
- add unit tests to microservices (a5c3b8a — Juan Acevedo)

### CI / Build
- track helper script that copies locust reports out of the pod (499f26d — Juan Acevedo)
- keep locust container alive until reports are copied (b596d30 — Juan Acevedo)
- generate release notes from git log directly to dodge docker-in-docker mount (3539fc7 — Juan Acevedo)
- add release pipeline with strict tests and git-cliff release notes (59c2311 — Juan Acevedo)
- run Locust inside the cluster with cluster JWT secret (35e1f66 — Juan Acevedo)
- add host-gateway mapping so Locust container can reach NodePorts (47ca283 — Juan Acevedo)
- rename locust stage and drop stress user that overwhelmed services (8ab7a88 — Juan Acevedo)
- serialize stage Build & Test to avoid CPU saturation (273af61 — Juan Acevedo)
- remove manual approval gate from stage pipeline (5bdc1df — Juan Acevedo)
- add stage pipeline with approval gate and strict tests (c823b86 — Juan Acevedo)
- bump deploy timeout to 8m and capture diagnostics on rollout failure (38e8213 — Juan Acevedo)
- pre-warm gradle wrapper before parallel build stage (4890486 — Juan Acevedo)
- add dev pipelines and per-service k8s deployments (93bd65f — Juan Acevedo)

### Chores
- ignore python bytecode artifacts (9398270 — Juan Acevedo)

### Other
- docker: switch service images to slim single-stage with prebuilt jar (ae1c5d0 — Juan Acevedo)
- infra: fix jenkins access to docker socket and k8s api on Docker Desktop (7645f40 — Juan Acevedo)
- infra: fix kafka and neo4j K8s deployments by disabling service links (04ec0dd — Juan Acevedo)
- docker: add remaining Dockerfiles and Jenkins/K8s infra for Taller 2 (a06b3f5 — Juan Acevedo)
- docker: add multi-stage Dockerfile for identity-service (47d29d3 — Juan Acevedo)
- docker: add multi-stage Dockerfile for auth-service and root .dockerignore (a56b60d — Juan Acevedo)
- merge: fix conflicts after pull actions (1338b5d — Juan Acevedo)
- complement front (538bd0f — Juan Carlos Muñoz)
- startup and tests fixed (a1d5f41 — Juan Carlos Muñoz)
- Add implementation on front and back (dce49ac — Juan Carlos Muñoz)
- first commit (f959b8b — Juan Carlos Muñoz)

