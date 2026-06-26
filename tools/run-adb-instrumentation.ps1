param(
    [Parameter(Mandatory = $true)]
    [string]$Component,

    [Parameter(Mandatory = $true)]
    [string]$ReportFile,

    [Parameter(Mandatory = $true)]
    [string]$TargetPackage,

    [Parameter(Mandatory = $true)]
    [string]$TestPackage,

    [Parameter(Mandatory = $true)]
    [ValidateSet(
        "class",
        "notClass"
    )]
    [string]$FilterArgumentName,

    [Parameter(Mandatory = $true)]
    [string]$FilterValue,

    [ValidateRange(
        30,
        3600
    )]
    [int]$TimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$adbCommand =
    Get-Command `
        adb `
        -ErrorAction Stop

$adbPath =
    $adbCommand.Source

$reportDirectory =
    Split-Path `
        -Parent `
        $ReportFile

if (
    -not [string]::IsNullOrWhiteSpace(
        $reportDirectory
    )
) {
    New-Item `
        -ItemType Directory `
        -Path $reportDirectory `
        -Force |
        Out-Null
}

$errorFile =
    "$ReportFile.stderr"

Remove-Item `
    -LiteralPath $ReportFile `
    -Force `
    -ErrorAction SilentlyContinue

Remove-Item `
    -LiteralPath $errorFile `
    -Force `
    -ErrorAction SilentlyContinue

$arguments =
    @(
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        $FilterArgumentName,
        $FilterValue,
        $Component
    )

Write-Host ""
Write-Host "Instrumentation filter:"
Write-Host "$FilterArgumentName=$FilterValue"
Write-Host ""
Write-Host "Per-session timeout:"
Write-Host "$TimeoutSeconds seconds"
Write-Host ""

$process =
    Start-Process `
        -FilePath $adbPath `
        -ArgumentList $arguments `
        -NoNewWindow `
        -RedirectStandardOutput $ReportFile `
        -RedirectStandardError $errorFile `
        -PassThru

$completed =
    $process.WaitForExit(
        $TimeoutSeconds * 1000
    )

if (-not $completed) {
    Write-Host ""
    Write-Host "Instrumentation session timed out."

    Stop-Process `
        -Id $process.Id `
        -Force `
        -ErrorAction SilentlyContinue

    $process.WaitForExit()

    & $adbPath `
        shell `
        am `
        force-stop `
        $TargetPackage `
        2>$null |
        Out-Null

    & $adbPath `
        shell `
        am `
        force-stop `
        $TestPackage `
        2>$null |
        Out-Null

    $timeoutMessage =
        @"

CAREPACK_INSTRUMENTATION_TIMEOUT
Filter: $FilterArgumentName=$FilterValue
TimeoutSeconds: $TimeoutSeconds
"@

    [System.IO.File]::AppendAllText(
        $ReportFile,
        $timeoutMessage
    )

    if (
        Test-Path `
            -LiteralPath $errorFile `
            -PathType Leaf
    ) {
        $errorText =
            [System.IO.File]::ReadAllText(
                $errorFile
            )

        if (
            -not [string]::IsNullOrWhiteSpace(
                $errorText
            )
        ) {
            [System.IO.File]::AppendAllText(
                $ReportFile,
                [Environment]::NewLine +
                    $errorText
            )
        }
    }

    Remove-Item `
        -LiteralPath $errorFile `
        -Force `
        -ErrorAction SilentlyContinue

    exit 124
}

$process.WaitForExit()

$exitCode =
    $process.ExitCode

if (
    Test-Path `
        -LiteralPath $errorFile `
        -PathType Leaf
) {
    $errorText =
        [System.IO.File]::ReadAllText(
            $errorFile
        )

    if (
        -not [string]::IsNullOrWhiteSpace(
            $errorText
        )
    ) {
        [System.IO.File]::AppendAllText(
            $ReportFile,
            [Environment]::NewLine +
                $errorText
        )
    }
}

Remove-Item `
    -LiteralPath $errorFile `
    -Force `
    -ErrorAction SilentlyContinue

exit $exitCode