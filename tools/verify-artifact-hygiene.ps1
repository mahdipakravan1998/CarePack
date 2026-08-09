param(
    [Parameter(Mandatory = $true)]
    [string]$Root,

    [switch]$AllowReleaseArtifacts
)

$ErrorActionPreference = "Stop"

$resolved = Resolve-Path -LiteralPath $Root
$scanRoot = $resolved.Path
$tempDirectory = $null

function Get-RelativePathWithinRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BasePath,

        [Parameter(Mandatory = $true)]
        [string]$TargetPath
    )

    $baseFull = [System.IO.Path]::GetFullPath($BasePath)
    $targetFull = [System.IO.Path]::GetFullPath($TargetPath)

    $trimCharacters =
        [char[]]@(
            [System.IO.Path]::DirectorySeparatorChar,
            [System.IO.Path]::AltDirectorySeparatorChar
        )

    $basePrefix =
        $baseFull.TrimEnd($trimCharacters) +
        [System.IO.Path]::DirectorySeparatorChar

    $comparison =
        if (
            [Environment]::OSVersion.Platform -eq
            [PlatformID]::Win32NT
        ) {
            [StringComparison]::OrdinalIgnoreCase
        }
        else {
            [StringComparison]::Ordinal
        }

    if (-not $targetFull.StartsWith($basePrefix, $comparison)) {
        throw "Scanned file escaped the artifact root: $targetFull"
    }

    return $targetFull.Substring($basePrefix.Length)
}

try {
    if (Test-Path -LiteralPath $scanRoot -PathType Leaf) {
        if ([System.IO.Path]::GetExtension($scanRoot) -ne ".zip") {
            throw "Artifact hygiene accepts a directory or a ZIP archive."
        }

        $tempDirectory = Join-Path ([System.IO.Path]::GetTempPath()) (
            "carepack-artifact-" + [System.Guid]::NewGuid().ToString("N")
        )
        New-Item -ItemType Directory -Path $tempDirectory -Force | Out-Null
        Expand-Archive -LiteralPath $scanRoot -DestinationPath $tempDirectory -Force
        $scanRoot = $tempDirectory
    }

    $forbiddenNames = @(
        "keystore.properties",
        "signing.properties",
        "secrets.properties",
        "local.properties"
    )

    $forbiddenExtensions = @(
        ".jks",
        ".keystore",
        ".env",
        ".log"
    )

    if (-not $AllowReleaseArtifacts) {
        $forbiddenExtensions += @(
            ".apk",
            ".aab"
        )
    }

    $violations = Get-ChildItem -Path $scanRoot -Recurse -File | Where-Object {
        $forbiddenNames -contains $_.Name -or
        $_.Name -match "(?i)^\.env(?:\..*)?$" -or
        $forbiddenExtensions -contains $_.Extension.ToLowerInvariant()
    } | Where-Object {
        $relativePath =
            Get-RelativePathWithinRoot `
                -BasePath $scanRoot `
                -TargetPath $_.FullName
        $relativePath -notmatch "(^|[\\/])build([\\/]|$)" -and
        $relativePath -notmatch "(^|[\\/])\.gradle([\\/]|$)"
    }

    if ($violations.Count -gt 0) {
        $violations.FullName | ForEach-Object { Write-Error $_ }
        throw "Sensitive, local-only, or disallowed build files were found in the source or artifact inventory."
    }

    if (Test-Path -LiteralPath ".git" -PathType Container) {
        $tracked = git ls-files
        if ($LASTEXITCODE -ne 0) {
            throw "Git tracked-file inventory failed."
        }

        $trackedViolations = $tracked | Where-Object {
            $_ -match "(?i)(^|/)(keystore\.properties|signing\.properties|secrets\.properties|local\.properties|\.env(?:\..*)?|.*\.(jks|keystore|log|apk|aab))$"
        }

        if ($trackedViolations.Count -gt 0) {
            $trackedViolations | ForEach-Object { Write-Error $_ }
            throw "Sensitive, local-only, or generated release files are tracked by Git."
        }
    }

    Write-Host "Artifact hygiene scan completed. The inventory result must be retained as release evidence."
} finally {
    if ($null -ne $tempDirectory -and (Test-Path -LiteralPath $tempDirectory)) {
        Remove-Item -LiteralPath $tempDirectory -Recurse -Force
    }
}
