param(
    [Parameter(Mandatory = $true)]
    [string]$Apk,

    [Parameter(Mandatory = $true)]
    [string]$Aab
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) {
    throw "Release APK is missing."
}

if (-not (Test-Path -LiteralPath $Aab -PathType Leaf)) {
    throw "Release AAB is missing."
}

$buildToolsRoot = Join-Path $env:ANDROID_HOME "build-tools"
$buildTools = Get-ChildItem -Path $buildToolsRoot -Directory |
    Sort-Object Name -Descending |
    Select-Object -First 1

if ($null -eq $buildTools) {
    throw "Android build-tools are unavailable."
}

$apksigner = Join-Path $buildTools.FullName "apksigner"
if ($IsWindows) {
    $apksigner = "$apksigner.bat"
}

New-Item -ItemType Directory -Path "build/release-inspection" -Force | Out-Null

& $apksigner verify --verbose --print-certs $Apk |
    Tee-Object -FilePath "build/release-inspection/apk-signature.txt"

if ($LASTEXITCODE -ne 0) {
    throw "APK signature verification failed."
}

Get-FileHash -Algorithm SHA256 -LiteralPath $Apk |
    Format-List |
    Out-File "build/release-inspection/apk-sha256.txt"

Get-FileHash -Algorithm SHA256 -LiteralPath $Aab |
    Format-List |
    Out-File "build/release-inspection/aab-sha256.txt"
