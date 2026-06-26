param(
    [string]$PackageName = "ir.carepack.debug"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RemoteXmlPath = "/sdcard/carepack-permission-ui.xml"
$ArtifactDirectory = Join-Path $PSScriptRoot "xiaomi-ui-artifacts"

New-Item `
    -ItemType Directory `
    -Path $ArtifactDirectory `
    -Force |
    Out-Null

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [switch]$AllowFailure
    )

    $previousPreference = $ErrorActionPreference
    $output = @()
    $exitCode = -1

    try {
        $ErrorActionPreference = "Continue"

        $output =
            & adb @Arguments 2>&1

        $exitCode =
            $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference =
            $previousPreference
    }

    $text =
        (
            (
                $output |
                    ForEach-Object {
                        [string]$_
                    }
            ) -join
            [Environment]::NewLine
        ).Trim()

    if (
        (-not $AllowFailure.IsPresent) -and
        $exitCode -ne 0
    ) {
        throw @"
ADB command failed.

Command:
adb $($Arguments -join " ")

Exit code:
$exitCode

Output:
$text
"@
    }

    return [PSCustomObject]@{
        ExitCode = $exitCode
        Text = $text
    }
}

function Save-UiArtifact {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [xml]$Xml
    )

    $timestamp =
        Get-Date `
            -Format "yyyyMMdd-HHmmss-fff"

    $path =
        Join-Path `
            $ArtifactDirectory `
            "$timestamp-$Name.xml"

    Set-Content `
        -Path $path `
        -Value $Xml.OuterXml `
        -Encoding UTF8

    Write-Host ""
    Write-Host "Diagnostic XML:"
    Write-Host $path
}

function Get-FreshUiXml {
    for (
        $attempt = 1;
        $attempt -le 12;
        $attempt++
    ) {
        Invoke-Adb `
            -Arguments @(
                "shell",
                "rm",
                "-f",
                $RemoteXmlPath
            ) `
            -AllowFailure |
            Out-Null

        $dumpResult =
            Invoke-Adb `
                -Arguments @(
                    "shell",
                    "uiautomator",
                    "dump",
                    "--compressed",
                    $RemoteXmlPath
                ) `
                -AllowFailure

        if ($dumpResult.ExitCode -ne 0) {
            Start-Sleep `
                -Milliseconds 350

            continue
        }

        $readResult =
            Invoke-Adb `
                -Arguments @(
                    "exec-out",
                    "cat",
                    $RemoteXmlPath
                ) `
                -AllowFailure

        if (
            $readResult.ExitCode -eq 0 -and
            $readResult.Text -match
            "<hierarchy"
        ) {
            try {
                $xml =
                    [xml]$readResult.Text

                return ,$xml
            }
            catch {
                Start-Sleep `
                    -Milliseconds 350
            }
        }
        else {
            Start-Sleep `
                -Milliseconds 350
        }
    }

    throw "Could not obtain a fresh Android UI hierarchy."
}

function Find-OtherPermissionsNode {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml
    )

    $node =
        $Xml.SelectSingleNode(
            "//node[@text='Other permissions']"
        )

    if ($null -eq $node) {
        $node =
            $Xml.SelectSingleNode(
                "//node[@text='Additional permissions']"
            )
    }

    return $node
}

function Find-TargetPermissionNode {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml
    )

    $node =
        $Xml.SelectSingleNode(
            "//node[@text='Open new windows while running in the background']"
        )

    if ($null -eq $node) {
        $node =
            $Xml.SelectSingleNode(
                "//node[contains(@text,'Open new windows') and contains(@text,'background')]"
            )
    }

    if ($null -eq $node) {
        $node =
            $Xml.SelectSingleNode(
                "//node[contains(@text,'Display pop-up windows') and contains(@text,'background')]"
            )
    }

    return $node
}

function Find-AlwaysAllowNode {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml
    )

    $node =
        $Xml.SelectSingleNode(
            "//node[@resource-id='com.miui.securitycenter:id/permission_always']"
        )

    if ($null -eq $node) {
        $node =
            $Xml.SelectSingleNode(
                "//node[@text='Always allow']"
            )
    }

    return $node
}

function Get-ClickableNode {
    param(
        [Parameter(Mandatory = $true)]
        [System.Xml.XmlElement]$Node
    )

    $current = $Node

    while (
        $null -ne $current -and
        $current.Name -eq "node"
    ) {
        if (
            $current.GetAttribute(
                "clickable"
            ) -eq "true"
        ) {
            return $current
        }

        $current =
            $current.ParentNode
    }

    return $Node
}

function Tap-Node {
    param(
        [Parameter(Mandatory = $true)]
        [System.Xml.XmlElement]$Node
    )

    $target =
        Get-ClickableNode `
            -Node $Node

    $bounds =
        $target.GetAttribute(
            "bounds"
        )

    if (
        $bounds -notmatch
        "^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$"
    ) {
        throw "Invalid UI bounds: $bounds"
    }

    $left =
        [int]$Matches[1]

    $top =
        [int]$Matches[2]

    $right =
        [int]$Matches[3]

    $bottom =
        [int]$Matches[4]

    $x =
        [int](
            ($left + $right) / 2
        )

    $y =
        [int](
            ($top + $bottom) / 2
        )

    $text =
        $Node.GetAttribute(
            "text"
        )

    $resourceId =
        $Node.GetAttribute(
            "resource-id"
        )

    Write-Host (
        "Tap [{0}, {1}] {2} {3}" -f
        $x,
        $y,
        $text,
        $resourceId
    )

    Invoke-Adb `
        -Arguments @(
            "shell",
            "input",
            "tap",
            "$x",
            "$y"
        ) |
        Out-Null

    Start-Sleep `
        -Milliseconds 900
}

function Wait-ForTargetPermission {
    for (
        $attempt = 1;
        $attempt -le 20;
        $attempt++
    ) {
        $xml =
            Get-FreshUiXml

        $node =
            Find-TargetPermissionNode `
                -Xml $xml

        if ($null -ne $node) {
            return [PSCustomObject]@{
                Xml = $xml
                Node = $node
            }
        }

        Start-Sleep `
            -Milliseconds 400
    }

    return $null
}

function Wait-ForAlwaysAllow {
    for (
        $attempt = 1;
        $attempt -le 20;
        $attempt++
    ) {
        $xml =
            Get-FreshUiXml

        $node =
            Find-AlwaysAllowNode `
                -Xml $xml

        if ($null -ne $node) {
            return [PSCustomObject]@{
                Xml = $xml
                Node = $node
            }
        }

        Start-Sleep `
            -Milliseconds 400
    }

    return $null
}

function Open-AlwaysAllowPage {
    for (
        $attempt = 1;
        $attempt -le 20;
        $attempt++
    ) {
        $xml =
            Get-FreshUiXml

        $alwaysNode =
            Find-AlwaysAllowNode `
                -Xml $xml

        if ($null -ne $alwaysNode) {
            return [PSCustomObject]@{
                Xml = $xml
                Node = $alwaysNode
            }
        }

        $targetNode =
            Find-TargetPermissionNode `
                -Xml $xml

        if ($null -ne $targetNode) {
            Write-Host "Target permission row found."

            Tap-Node `
                -Node $targetNode

            $alwaysResult =
                Wait-ForAlwaysAllow

            if ($null -ne $alwaysResult) {
                return $alwaysResult
            }
        }

        $otherNode =
            Find-OtherPermissionsNode `
                -Xml $xml

        if ($null -ne $otherNode) {
            Write-Host "Other permissions row found."

            Tap-Node `
                -Node $otherNode

            $targetResult =
                Wait-ForTargetPermission

            if ($null -eq $targetResult) {
                $failureXml =
                    Get-FreshUiXml

                Save-UiArtifact `
                    -Name "target-row-not-found" `
                    -Xml $failureXml

                throw "The target permission row was not found after opening Other permissions."
            }

            Write-Host "Target permission row found."

            Tap-Node `
                -Node $targetResult.Node

            $alwaysResult =
                Wait-ForAlwaysAllow

            if ($null -ne $alwaysResult) {
                return $alwaysResult
            }
        }

        Start-Sleep `
            -Milliseconds 400
    }

    $lastXml =
        Get-FreshUiXml

    Save-UiArtifact `
        -Name "permission-navigation-failed" `
        -Xml $lastXml

    throw "Could not navigate to the Always allow option."
}

function Test-AlwaysAllowChecked {
    param(
        [Parameter(Mandatory = $true)]
        [System.Xml.XmlElement]$Node
    )

    return (
        $Node.GetAttribute(
            "checked"
        ) -eq "true"
    )
}

function Verify-AlwaysAllow {
    for (
        $attempt = 1;
        $attempt -le 20;
        $attempt++
    ) {
        $xml =
            Get-FreshUiXml

        $alwaysNode =
            Find-AlwaysAllowNode `
                -Xml $xml

        if ($null -ne $alwaysNode) {
            if (
                Test-AlwaysAllowChecked `
                    -Node $alwaysNode
            ) {
                return $true
            }

            return $false
        }

        $targetNode =
            Find-TargetPermissionNode `
                -Xml $xml

        if ($null -ne $targetNode) {
            Tap-Node `
                -Node $targetNode

            Start-Sleep `
                -Milliseconds 500

            continue
        }

        $otherNode =
            Find-OtherPermissionsNode `
                -Xml $xml

        if ($null -ne $otherNode) {
            Tap-Node `
                -Node $otherNode

            Start-Sleep `
                -Milliseconds 500

            continue
        }

        Start-Sleep `
            -Milliseconds 400
    }

    return $false
}

if (
    $null -eq
    (
        Get-Command `
            adb `
            -ErrorAction SilentlyContinue
    )
) {
    throw "adb was not found in PATH."
}

$deviceState =
    Invoke-Adb `
        -Arguments @(
            "get-state"
        ) `
        -AllowFailure

if (
    $deviceState.ExitCode -ne 0 -or
    $deviceState.Text.Trim() -ne "device"
) {
    throw "No authorized Android device was found."
}

$packageState =
    Invoke-Adb `
        -Arguments @(
            "shell",
            "pm",
            "path",
            $PackageName
        ) `
        -AllowFailure

if (
    $packageState.ExitCode -ne 0 -or
    $packageState.Text -notmatch
    "^package:"
) {
    throw "Package is not installed: $PackageName"
}

Write-Host ""
Write-Host "Target package:"
Write-Host $PackageName
Write-Host ""

Write-Host "Waking and unlocking device..."

Invoke-Adb `
    -Arguments @(
        "shell",
        "input",
        "keyevent",
        "224"
    ) `
    -AllowFailure |
    Out-Null

Invoke-Adb `
    -Arguments @(
        "shell",
        "wm",
        "dismiss-keyguard"
    ) `
    -AllowFailure |
    Out-Null

Write-Host ""
Write-Host "Opening MIUI permission editor..."

$startResult =
    Invoke-Adb `
        -Arguments @(
            "shell",
            "am",
            "start",
            "-a",
            "miui.intent.action.APP_PERM_EDITOR",
            "-n",
            "com.miui.securitycenter/com.miui.permcenter.permissions.PermissionsEditorActivity",
            "--es",
            "extra_pkgname",
            $PackageName
        ) `
        -AllowFailure

if (
    -not [string]::IsNullOrWhiteSpace(
        $startResult.Text
    )
) {
    Write-Host (
        $startResult.Text `
            -replace
            "\r?\n",
            " "
    )
}

Start-Sleep `
    -Milliseconds 1300

$alwaysResult =
    Open-AlwaysAllowPage

if (
    Test-AlwaysAllowChecked `
        -Node $alwaysResult.Node
) {
    Write-Host ""
    Write-Host "Permission is already set to Always allow."
}
else {
    Write-Host ""
    Write-Host "Selecting Always allow..."

    Tap-Node `
        -Node $alwaysResult.Node

    $verified =
        Verify-AlwaysAllow

    if (-not $verified) {
        $failureXml =
            Get-FreshUiXml

        Save-UiArtifact `
            -Name "always-allow-verification-failed" `
            -Xml $failureXml

        throw "MIUI did not report Always allow as checked."
    }

    Write-Host ""
    Write-Host "Permission verified: Always allow."
}

Invoke-Adb `
    -Arguments @(
        "shell",
        "input",
        "keyevent",
        "3"
    ) `
    -AllowFailure |
    Out-Null

Write-Host ""
Write-Host "Xiaomi background-window permission granted and verified."
Write-Host ""