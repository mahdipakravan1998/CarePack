$ErrorActionPreference = "Stop"

$forbiddenTracked = git rev-list --objects --all |
    Select-String -Pattern "(?i)(keystore\.properties|signing\.properties|secrets\.properties|local\.properties|\.jks$|\.keystore$|(^|/)\.env(?:\..*)?$)"

if ($forbiddenTracked.Count -gt 0) {
    $forbiddenTracked | ForEach-Object { Write-Error $_.Line }
    throw "Forbidden secret or local file names remain in reachable Git history."
}

if (Get-Command gitleaks -ErrorAction SilentlyContinue) {
    gitleaks git --redact --no-banner .
    if ($LASTEXITCODE -ne 0) {
        throw "Gitleaks reported a secret in Git history."
    }
} else {
    throw "gitleaks is required for full history verification."
}