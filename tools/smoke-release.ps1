param(
    [Parameter(Mandatory = $true)]
    [string]$Apk,

    [string]$PackageName = "ir.carepack",

    [string]$ActivityName = "ir.carepack.MainActivity"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) {
    throw "Release APK is missing."
}

$devices = adb devices | Select-String -Pattern "\tdevice$"
if ($devices.Count -ne 1) {
    throw "Exactly one authorized Android device must be connected."
}

adb logcat -c
adb install -r $Apk
if ($LASTEXITCODE -ne 0) {
    throw "Release APK installation failed."
}

adb shell am force-stop $PackageName
adb shell am start -W -n "$PackageName/$ActivityName" |
    Tee-Object -FilePath "build/release-inspection/startup.txt"
if ($LASTEXITCODE -ne 0) {
    throw "Release activity startup failed."
}

Start-Sleep -Seconds 3

$appPid = (adb shell pidof $PackageName).Trim()
if ([string]::IsNullOrWhiteSpace($appPid)) {
    throw "Release process is not running after startup."
}

$log = adb logcat -d -v threadtime
New-Item -ItemType Directory -Path "build/release-inspection" -Force | Out-Null
$log | Set-Content -Path "build/release-inspection/startup-logcat.txt" -Encoding UTF8

$fatal = $log | Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime.*Process: $PackageName"
if ($fatal.Count -gt 0) {
    $fatal | ForEach-Object { Write-Error $_.Line }
    throw "Release startup log contains a fatal process failure."
}

Write-Host "Release startup smoke completed. The output must be reviewed and retained as evidence."
