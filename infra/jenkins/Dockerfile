# =============================================================================
# CircleGuard - Jenkins controller (custom image)
# -----------------------------------------------------------------------------
# We extend the official LTS Jenkins image so that EVERY tool the pipelines
# need is already inside the container:
#   - Docker CLI       -> to build images and push them to a registry
#   - kubectl          -> to apply manifests to the local Kubernetes cluster
#   - JDK 21 + Gradle  -> to run ./gradlew (Spring Boot services)
#   - Python + pip     -> to run the Locust performance tests
#   - jq, curl, git    -> common utilities used throughout the pipelines
#
# Base image:
#   jenkins/jenkins:lts-jdk21 already ships with JDK 21 and the Jenkins
#   controller. We only add the missing tools on top.
# =============================================================================
FROM jenkins/jenkins:lts-jdk21

# Run privileged installs as root, then drop back to "jenkins" at the end.
USER root

# 1) System packages (curl, git, jq, ca-certificates, lsb-release, gnupg, python).
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
        curl \
        git \
        jq \
        ca-certificates \
        gnupg \
        lsb-release \
        python3 \
        python3-pip \
        python3-venv \
 && rm -rf /var/lib/apt/lists/*

# 2) Docker CLI only (NOT the daemon — we mount the host socket from compose).
#    Using Docker's official apt repo so the CLI version stays reasonably fresh.
RUN install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg \
        | gpg --dearmor -o /etc/apt/keyrings/docker.gpg \
 && chmod a+r /etc/apt/keyrings/docker.gpg \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
        https://download.docker.com/linux/debian $(lsb_release -cs) stable" \
        > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli \
 && rm -rf /var/lib/apt/lists/*

# 3) kubectl (matches the Docker Desktop Kubernetes minor version 1.35).
ARG KUBECTL_VERSION=v1.35.3
RUN curl -fsSLo /usr/local/bin/kubectl \
        "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
 && chmod +x /usr/local/bin/kubectl

# 4) Pre-install the Jenkins plugins listed in plugins.txt so the controller
#    boots ready-to-use (no manual "Install suggested plugins" step).
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# 5) Add the "jenkins" user to the docker group so the container can talk to
#    the host's Docker daemon through the mounted socket /var/run/docker.sock.
#    The GID is overridden at runtime (see docker-compose.jenkins.yml) to match
#    the host's docker socket GID.
RUN groupadd -f docker && usermod -aG docker jenkins

USER jenkins
