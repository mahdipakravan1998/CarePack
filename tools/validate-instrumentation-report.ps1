param(
    [Parameter(Mandatory = $true)]
    [string]$ReportFile,

    [Parameter(Mandatory = $true)]
    [int]$AdbExitCode
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (
    -not (
        Test-Path `
            -LiteralPath $ReportFile `
            -PathType Leaf
    )
) {
    Write-Host ""
    Write-Host "Instrumentation report was not generated:"
    Write-Host $ReportFile

    exit 2
}

$resolvedReportFile =
    (
        Resolve-Path `
            -LiteralPath $ReportFile
    ).Path

$report =
    [System.IO.File]::ReadAllText(
        $resolvedReportFile
    )

Write-Host ""
Write-Host "Validating instrumentation report..."

if ($AdbExitCode -ne 0) {
    Write-Host ""
    Write-Host "ADB process returned a non-zero exit code:"
    Write-Host $AdbExitCode

    exit 1
}

$failureMarkers =
    @(
        "FAILURES!!!",
        "INSTRUMENTATION_FAILED",
        "INSTRUMENTATION_ABORTED",
        "Process crashed.",
        "Test run failed to complete",
        "ComposeTimeoutException",
        "INSTRUMENTATION_CODE: 0"
    )

foreach ($marker in $failureMarkers) {
    if (
        $report.Contains(
            $marker
        )
    ) {
        Write-Host ""
        Write-Host "Failure marker found:"
        Write-Host $marker

        exit 1
    }
}

if (
    -not $report.Contains(
        "OK ("
    )
) {
    Write-Host ""
    Write-Host "JUnit success marker was not found:"
    Write-Host "OK ("

    exit 2
}

if (
    -not $report.Contains(
        "INSTRUMENTATION_CODE: -1"
    )
) {
    Write-Host ""
    Write-Host "Instrumentation success code was not found:"
    Write-Host "INSTRUMENTATION_CODE: -1"

    exit 2
}

Write-Host ""
Write-Host "Instrumentation report validated successfully."

exit 0