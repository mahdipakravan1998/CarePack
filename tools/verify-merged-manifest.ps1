param(
    [Parameter(Mandatory = $true)]
    [string]$Manifest
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Manifest -PathType Leaf)) {
    throw "Merged manifest is missing."
}

$content = Get-Content -LiteralPath $Manifest -Raw

$forbidden = @(
    "android.permission.USE_FULL_SCREEN_INTENT",
    "android.permission.INTERNET",
    'android:showWhenLocked="true"',
    'android:turnScreenOn="true"'
)

foreach ($value in $forbidden) {
    if ($content.Contains($value)) {
        throw "Merged manifest contains a forbidden release contract value."
    }
}

$required = @(
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.VIBRATE"
)

foreach ($value in $required) {
    if (-not $content.Contains($value)) {
        throw "Merged manifest is missing a required release contract permission."
    }
}

Write-Host "Merged manifest contract is structurally valid. Execution evidence must be retained with the release artifacts."
