param(
    [string]$Repo = "."
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot =
    (Resolve-Path -LiteralPath $Repo -ErrorAction Stop).Path

$schemaRelative =
    "app/schemas/ir.carepack.data.local.CarePackDatabase/1.json"

$schema =
    Join-Path $repoRoot $schemaRelative

if (-not (Test-Path -LiteralPath $schema -PathType Leaf)) {
    throw "Committed Room version 1 schema is missing."
}

$isWindowsHost =
    [Environment]::OSVersion.Platform -eq
    [PlatformID]::Win32NT

$gradleWrapper =
    if ($isWindowsHost) {
        Join-Path $repoRoot "gradlew.bat"
    } else {
        Join-Path $repoRoot "gradlew"
    }

if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle wrapper entry point is missing: $gradleWrapper"
}

if (-not $isWindowsHost) {
    $modeLine =
        @(
            & git -C $repoRoot ls-files --stage -- gradlew
        )

    if (
        $LASTEXITCODE -ne 0 -or
        $modeLine.Count -ne 1 -or
        -not ([string]$modeLine[0]).StartsWith("100755 ")
    ) {
        throw "gradlew must be tracked with Unix executable mode 100755."
    }
}

function Invoke-CarePackGradle {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $oldErrorActionPreference = $ErrorActionPreference

    try {
        $ErrorActionPreference = "Continue"
        & $gradleWrapper @Arguments
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $oldErrorActionPreference
    }

    if ($exitCode -ne 0) {
        throw (
            "Gradle failed with exit code $exitCode. Arguments: " +
            ($Arguments -join " ")
        )
    }
}

$beforeBytes =
    [IO.File]::ReadAllBytes($schema)

$beforeHash =
    (
        Get-FileHash `
            -Algorithm SHA256 `
            -LiteralPath $schema
    ).Hash

$mismatchEvidence = $null

Push-Location $repoRoot

try {
    Invoke-CarePackGradle `
        -Arguments @(
            "--no-daemon",
            "--no-configuration-cache",
            "--dependency-verification",
            "strict",
            "clean",
            ":app:kspDebugKotlin"
        )

    $afterHash =
        (
            Get-FileHash `
                -Algorithm SHA256 `
                -LiteralPath $schema
        ).Hash

    if ($beforeHash -ne $afterHash) {
        $evidenceRoot =
            Join-Path `
                ([IO.Path]::GetTempPath()) `
                (
                    "carepack-room-schema-mismatch-" +
                    [DateTime]::UtcNow.ToString("yyyyMMdd-HHmmss-fff") +
                    "-" +
                    [Guid]::NewGuid().ToString("N")
                )

        New-Item `
            -ItemType Directory `
            -Path $evidenceRoot `
            -Force |
            Out-Null

        $mismatchEvidence =
            Join-Path $evidenceRoot "1.generated.json"

        Copy-Item `
            -LiteralPath $schema `
            -Destination $mismatchEvidence `
            -Force

        [IO.File]::WriteAllBytes(
            $schema,
            $beforeBytes
        )

        throw (
            "Generated Room schema differs from the committed baseline. " +
            "The generated candidate was preserved at: $mismatchEvidence"
        )
    }

    Invoke-CarePackGradle `
        -Arguments @(
            "--no-daemon",
            "--no-configuration-cache",
            "--dependency-verification",
            "strict",
            "verifyRoomSchemaCommitted"
        )
}
finally {
    Pop-Location

    $currentHash =
        (
            Get-FileHash `
                -Algorithm SHA256 `
                -LiteralPath $schema
        ).Hash

    if ($currentHash -ne $beforeHash) {
        [IO.File]::WriteAllBytes(
            $schema,
            $beforeBytes
        )
    }
}

Write-Host (
    "Room schema baseline matches generated entities. " +
    "Cross-platform wrapper execution and strict dependency verification passed."
)
