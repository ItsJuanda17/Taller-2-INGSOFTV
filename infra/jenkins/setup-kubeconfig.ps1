# =============================================================================
# CircleGuard - Generate a Jenkins-friendly kubeconfig
# -----------------------------------------------------------------------------
# Docker Desktop / Windows writes its kubeconfig with a server URL like
# "https://127.0.0.1:NNNNN" and a cert valid only for that loopback. From
# INSIDE the Jenkins container, "127.0.0.1" is the container's own loopback,
# so it cannot reach the K8s API server. We have to:
#   1. Replace 127.0.0.1 (or kubernetes.docker.internal) with host.docker.internal
#   2. Set insecure-skip-tls-verify and remove certificate-authority-data,
#      because the original cert is signed for 127.0.0.1 only.
#
# Run from the repo root:
#   pwsh ./infra/jenkins/setup-kubeconfig.ps1
# (or use Windows PowerShell 5.1: it works there too).
#
# The output file infra/jenkins/.kubeconfig-incontainer is gitignored.
# =============================================================================

$ErrorActionPreference = 'Stop'

$src = Join-Path $env:USERPROFILE '.kube\config'
if (-not (Test-Path $src)) {
    Write-Error "No kubeconfig found at $src. Is Docker Desktop's Kubernetes enabled?"
    exit 1
}

$dst = Join-Path $PSScriptRoot '.kubeconfig-incontainer'

$content = Get-Content $src -Raw

# 1) Rewrite the server URL: anything pointing at host loopback or
#    kubernetes.docker.internal -> host.docker.internal (same port).
$content = [regex]::Replace($content,
    'server:\s*https://(127\.0\.0\.1|kubernetes\.docker\.internal):(\d+)',
    'server: https://host.docker.internal:$2')

# 2) Drop the certificate-authority-data line; the original cert isn't valid
#    for host.docker.internal. We rely on insecure-skip-tls-verify instead.
$content = [regex]::Replace($content,
    '(?m)^\s*certificate-authority-data:\s*\S+\s*\r?\n', '')

# 3) Inject "insecure-skip-tls-verify: true" right BEFORE each "server:" line
#    inside a cluster block. Reusing the server line's indentation keeps the
#    new line aligned regardless of how the YAML was originally formatted.
$content = [regex]::Replace($content,
    '(?m)^(\s+)server: ',
    "`$1insecure-skip-tls-verify: true`r`n`$1server: ")

# 4) Write with Unix newlines so the YAML parses cleanly inside the container.
$content = $content -replace "`r`n", "`n"
[IO.File]::WriteAllText($dst, $content)

Write-Output "Wrote container-ready kubeconfig: $dst"
