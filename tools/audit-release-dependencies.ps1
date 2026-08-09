param(
    [string]$OutputRoot = "build/release-inspection"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repository =
    [string]$env:GITHUB_REPOSITORY

if ([string]::IsNullOrWhiteSpace($repository)) {
    throw "GITHUB_REPOSITORY is required for the online release dependency audit."
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) is required for the online release dependency audit."
}

if (
    [string]::IsNullOrWhiteSpace(
        [string]$env:GH_TOKEN
    ) -and
    [string]::IsNullOrWhiteSpace(
        [string]$env:GITHUB_TOKEN
    )
) {
    throw "GH_TOKEN or GITHUB_TOKEN is required for the online release dependency audit."
}

New-Item `
    -ItemType Directory `
    -Path $OutputRoot `
    -Force |
    Out-Null

function Invoke-GhJson {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [Parameter(Mandatory = $true)]
        [string]$EvidencePath
    )

    $oldErrorActionPreference = $ErrorActionPreference

    try {
        $ErrorActionPreference = "Continue"
        $raw =
            @(
                & gh @Arguments 2>&1
            )
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $oldErrorActionPreference
    }

    $rawText =
        $raw -join [Environment]::NewLine

    [IO.File]::WriteAllText(
        $EvidencePath,
        $rawText,
        (New-Object Text.UTF8Encoding($false))
    )

    if ($exitCode -ne 0) {
        throw (
            "GitHub API dependency audit query failed. Evidence: $EvidencePath"
        )
    }

    try {
        return $rawText | ConvertFrom-Json
    }
    catch {
        throw (
            "GitHub API returned non-JSON dependency audit evidence. " +
            "Evidence: $EvidencePath"
        )
    }
}

$allAlerts =
    New-Object System.Collections.Generic.List[object]

$page = 1

while ($true) {
    $pageEvidence =
        Join-Path `
            $OutputRoot `
            ("dependabot-open-alerts-page-" + $page + ".json")

    $pageResult =
        Invoke-GhJson `
            -Arguments @(
                "api",
                "-H",
                "Accept: application/vnd.github+json",
                (
                    "repos/$repository/dependabot/alerts" +
                    "?state=open&per_page=100&page=$page"
                )
            ) `
            -EvidencePath $pageEvidence

    $items = @($pageResult)

    foreach ($item in $items) {
        $allAlerts.Add($item)
    }

    if ($items.Count -lt 100) {
        break
    }

    $page += 1

    if ($page -gt 100) {
        throw "Dependabot alert pagination exceeded the safety limit."
    }
}

$combinedAlertPath =
    Join-Path $OutputRoot "dependabot-open-alerts.json"

$allAlerts |
    ConvertTo-Json -Depth 32 |
    Set-Content `
        -LiteralPath $combinedAlertPath `
        -Encoding UTF8

if ($allAlerts.Count -gt 0) {
    foreach ($alert in $allAlerts) {
        Write-Host (
            "OPEN DEPENDABOT ALERT: severity={0} package={1} advisory={2}" -f
            $alert.security_advisory.severity,
            $alert.dependency.package.name,
            $alert.security_advisory.ghsa_id
        )
    }

    throw (
        "Open Dependabot alerts exist for the release repository. " +
        "Count=$($allAlerts.Count)"
    )
}

$sbomPath =
    Join-Path $OutputRoot "dependency-sbom-spdx.json"

$sbomResponse =
    Invoke-GhJson `
        -Arguments @(
            "api",
            "-H",
            "Accept: application/vnd.github+json",
            "repos/$repository/dependency-graph/sbom"
        ) `
        -EvidencePath $sbomPath

$packages =
    @($sbomResponse.sbom.packages)

if ($packages.Count -eq 0) {
    throw "GitHub dependency graph returned an empty SPDX package inventory."
}

$licenseInventory =
    foreach ($package in $packages) {
        [pscustomobject]@{
            name = [string]$package.name
            versionInfo = [string]$package.versionInfo
            supplier = [string]$package.supplier
            licenseDeclared = [string]$package.licenseDeclared
            licenseConcluded = [string]$package.licenseConcluded
            downloadLocation = [string]$package.downloadLocation
        }
    }

$licensePath =
    Join-Path $OutputRoot "dependency-license-inventory.csv"

$licenseInventory |
    Export-Csv `
        -LiteralPath $licensePath `
        -NoTypeInformation `
        -Encoding UTF8

$unknownLicenseCount =
    @(
        $licenseInventory |
            Where-Object {
                (
                    [string]::IsNullOrWhiteSpace(
                        $_.licenseDeclared
                    ) -or
                    $_.licenseDeclared -eq "NOASSERTION"
                ) -and
                (
                    [string]::IsNullOrWhiteSpace(
                        $_.licenseConcluded
                    ) -or
                    $_.licenseConcluded -eq "NOASSERTION"
                )
            }
    ).Count

$summary =
    [ordered]@{
        repository = $repository
        scannedAtUtc =
            [DateTime]::UtcNow.ToString("o")
        openDependabotAlerts =
            $allAlerts.Count
        spdxPackageCount =
            $packages.Count
        packagesWithoutDeclaredOrConcludedLicense =
            $unknownLicenseCount
    }

$summaryPath =
    Join-Path $OutputRoot "dependency-audit-summary.json"

$summary |
    ConvertTo-Json |
    Set-Content `
        -LiteralPath $summaryPath `
        -Encoding UTF8

Write-Host (
    "Online release dependency audit: PASS. " +
    "Open alerts=0; SPDX packages=$($packages.Count); " +
    "license metadata gaps=$unknownLicenseCount"
)
Write-Host "License inventory: $licensePath"
Write-Host "Audit summary: $summaryPath"
