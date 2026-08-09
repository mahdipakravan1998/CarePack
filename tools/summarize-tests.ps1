param(
    [Parameter(Mandatory = $true)]
    [string]$Root
)

$ErrorActionPreference = "Stop"

$files = Get-ChildItem -Path $Root -Recurse -Filter "TEST-*.xml" -File
if ($files.Count -eq 0) {
    throw "No JUnit XML files were found under $Root."
}

$totals = [ordered]@{
    executed = 0
    skipped = 0
    assumptions = 0
    failed = 0
    errors = 0
    suites = $files.Count
}

foreach ($file in $files) {
    [xml]$document = Get-Content -LiteralPath $file.FullName -Raw
    $suite = $document.testsuite
    if ($null -eq $suite) {
        continue
    }

    $tests = [int]$suite.tests
    $skipped = [int]$suite.skipped
    $failures = [int]$suite.failures
    $errors = [int]$suite.errors

    $totals.executed += $tests - $skipped
    $totals.skipped += $skipped
    $totals.failed += $failures
    $totals.errors += $errors

    foreach ($testcase in $suite.testcase) {
        if ($null -ne $testcase.skipped) {
            $message = [string]$testcase.skipped.message
            if ($message -match "assum") {
                $totals.assumptions += 1
            }
        }
    }
}

New-Item -ItemType Directory -Path "build" -Force | Out-Null
$totals | ConvertTo-Json | Set-Content -Path "build/test-summary.json" -Encoding UTF8

Write-Host ($totals | ConvertTo-Json -Compress)

if ($totals.failed -gt 0 -or $totals.errors -gt 0) {
    throw "Test failures or errors were reported."
}

if ($totals.skipped -gt 0) {
    throw "Skipped or assumption-based tests are not permitted in the release gate."
}
