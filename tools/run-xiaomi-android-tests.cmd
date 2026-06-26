@echo off
setlocal EnableExtensions

rem ------------------------------------------------------------
rem CarePack Xiaomi physical-device instrumented test runner
rem
rem Full suite, isolated into two instrumentation sessions:
rem   tools\run-xiaomi-android-tests.cmd
rem
rem One class:
rem   tools\run-xiaomi-android-tests.cmd "ir.carepack.ui.CarePackComposeTest"
rem
rem One method:
rem   tools\run-xiaomi-android-tests.cmd "ir.carepack.ui.CarePackComposeTest#fullSetup_navigatesToToday_andRecordsGiven"
rem ------------------------------------------------------------

cd /d "%~dp0.."

set "GRADLEW=%CD%\gradlew.bat"

set "TARGET_PACKAGE=ir.carepack.debug"
set "TEST_PACKAGE=ir.carepack.debug.test"
set "TEST_RUNNER=androidx.test.runner.AndroidJUnitRunner"
set "TEST_COMPONENT=%TEST_PACKAGE%/%TEST_RUNNER%"

set "COMPOSE_TEST_CLASS=ir.carepack.ui.CarePackComposeTest"

set "TARGET_APK=%CD%\app\build\outputs\apk\debug\app-debug.apk"
set "TEST_APK=%CD%\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"

set "REPORT_DIRECTORY=%CD%\app\build\reports\xiaomiAndroidTest"

set "NON_UI_REPORT_FILE=%REPORT_DIRECTORY%\instrumentation-non-ui-result.txt"
set "UI_REPORT_FILE=%REPORT_DIRECTORY%\instrumentation-ui-result.txt"
set "SELECTED_REPORT_FILE=%REPORT_DIRECTORY%\instrumentation-selected-result.txt"

set "PERMISSION_SCRIPT=%CD%\tools\grant-xiaomi-ui-permissions.ps1"
set "VALIDATOR_SCRIPT=%CD%\tools\validate-instrumentation-report.ps1"

echo.
echo ============================================================
echo CarePack Xiaomi instrumented tests
echo ============================================================
echo.

if not exist "%GRADLEW%" (
    echo ERROR: gradlew.bat was not found.
    echo Current directory:
    echo %CD%
    exit /b 1
)

if not exist "%PERMISSION_SCRIPT%" (
    echo ERROR: Xiaomi permission PowerShell script was not found:
    echo %PERMISSION_SCRIPT%
    exit /b 1
)

if not exist "%VALIDATOR_SCRIPT%" (
    echo ERROR: Instrumentation report validator was not found:
    echo %VALIDATOR_SCRIPT%
    exit /b 1
)

where adb >nul 2>&1

if errorlevel 1 (
    echo ERROR: adb was not found in PATH.
    exit /b 1
)

where powershell.exe >nul 2>&1

if errorlevel 1 (
    echo ERROR: Windows PowerShell was not found.
    exit /b 1
)

adb get-state >nul 2>&1

if errorlevel 1 (
    echo ERROR: No authorized Android device is available.
    echo.
    echo Run:
    echo adb devices
    exit /b 1
)

for /f "usebackq delims=" %%M in (`adb shell getprop ro.product.manufacturer`) do (
    set "DEVICE_MANUFACTURER=%%M"
)

for /f "usebackq delims=" %%D in (`adb shell getprop ro.product.model`) do (
    set "DEVICE_MODEL=%%D"
)

for /f "usebackq delims=" %%V in (`adb shell getprop ro.build.version.release`) do (
    set "ANDROID_VERSION=%%V"
)

echo Device manufacturer: %DEVICE_MANUFACTURER%
echo Device model:        %DEVICE_MODEL%
echo Android version:     %ANDROID_VERSION%
echo.

echo [1/7] Building application and test APKs...

call "%GRADLEW%" ^
    :app:assembleDebug ^
    :app:assembleDebugAndroidTest ^
    --stacktrace

if errorlevel 1 (
    echo.
    echo ERROR: Gradle build failed.
    exit /b 1
)

if not exist "%TARGET_APK%" (
    echo.
    echo ERROR: Target APK was not generated:
    echo %TARGET_APK%
    exit /b 1
)

if not exist "%TEST_APK%" (
    echo.
    echo ERROR: Test APK was not generated:
    echo %TEST_APK%
    exit /b 1
)

echo.
echo [2/7] Stopping previous processes...

adb shell am force-stop "%TARGET_PACKAGE%" >nul 2>&1
adb shell am force-stop "%TEST_PACKAGE%" >nul 2>&1

echo.
echo [3/7] Updating application APK...

adb install -r -t -g "%TARGET_APK%"

if errorlevel 1 (
    echo.
    echo ERROR: Application APK installation failed.
    exit /b 1
)

echo.
echo [4/7] Updating Android test APK...

adb install -r -t -g "%TEST_APK%"

if errorlevel 1 (
    echo.
    echo ERROR: Android test APK installation failed.
    exit /b 1
)

echo.
echo [5/7] Granting Xiaomi permission through UI automation...

powershell.exe ^
    -NoLogo ^
    -NoProfile ^
    -ExecutionPolicy Bypass ^
    -File "%PERMISSION_SCRIPT%"

if errorlevel 1 (
    echo.
    echo ERROR: Xiaomi UI permission automation failed.
    exit /b 1
)

echo.
echo [6/7] Preparing device...

adb shell input keyevent 224 >nul 2>&1
adb shell wm dismiss-keyguard >nul 2>&1

adb shell am set-standby-bucket "%TARGET_PACKAGE%" active >nul 2>&1
adb shell am set-standby-bucket "%TEST_PACKAGE%" active >nul 2>&1

adb shell dumpsys deviceidle whitelist +"%TARGET_PACKAGE%" >nul 2>&1
adb shell dumpsys deviceidle whitelist +"%TEST_PACKAGE%" >nul 2>&1

rem The Xiaomi permission editor may remain in the foreground.
rem Do not launch CarePack manually here. Instrumentation creates
rem and manages its own test host.
adb shell am force-stop "%TARGET_PACKAGE%" >nul 2>&1
adb shell am force-stop "%TEST_PACKAGE%" >nul 2>&1

if not exist "%REPORT_DIRECTORY%" (
    mkdir "%REPORT_DIRECTORY%"
)

if exist "%NON_UI_REPORT_FILE%" (
    del /f /q "%NON_UI_REPORT_FILE%" >nul 2>&1
)

if exist "%UI_REPORT_FILE%" (
    del /f /q "%UI_REPORT_FILE%" >nul 2>&1
)

if exist "%SELECTED_REPORT_FILE%" (
    del /f /q "%SELECTED_REPORT_FILE%" >nul 2>&1
)

echo.
echo [7/7] Running instrumented tests...
echo.

if not "%~1"=="" goto :run_selected_test
goto :run_full_suite


:run_full_suite

rem ------------------------------------------------------------
rem Session A:
rem Run all instrumented tests except the Compose Navigation class.
rem ------------------------------------------------------------

echo [7A/7] Running non-UI instrumented tests...
echo Excluded class:
echo %COMPOSE_TEST_CLASS%
echo.

adb shell am force-stop "%TARGET_PACKAGE%" >nul 2>&1
adb shell am force-stop "%TEST_PACKAGE%" >nul 2>&1

adb shell am instrument ^
    -w ^
    -r ^
    -e notClass "%COMPOSE_TEST_CLASS%" ^
    "%TEST_COMPONENT%" ^
    > "%NON_UI_REPORT_FILE%" 2>&1

set "NON_UI_ADB_EXIT_CODE=%ERRORLEVEL%"

echo.
echo Non-UI instrumentation adb process returned.
echo ADB exit code: %NON_UI_ADB_EXIT_CODE%
echo.

if not exist "%NON_UI_REPORT_FILE%" goto :non_ui_report_missing

type "%NON_UI_REPORT_FILE%"

powershell.exe ^
    -NoLogo ^
    -NoProfile ^
    -ExecutionPolicy Bypass ^
    -File "%VALIDATOR_SCRIPT%" ^
    -ReportFile "%NON_UI_REPORT_FILE%" ^
    -AdbExitCode "%NON_UI_ADB_EXIT_CODE%"

set "NON_UI_VALIDATION_EXIT_CODE=%ERRORLEVEL%"

if not "%NON_UI_VALIDATION_EXIT_CODE%"=="0" goto :non_ui_tests_failed

rem ------------------------------------------------------------
rem Completely end the first instrumentation and app lifecycle
rem before starting Compose Navigation tests.
rem ------------------------------------------------------------

echo.
echo Isolating Compose UI test session...

adb shell am force-stop "%TARGET_PACKAGE%" >nul 2>&1
adb shell am force-stop "%TEST_PACKAGE%" >nul 2>&1

timeout /t 1 /nobreak >nul

adb shell input keyevent 224 >nul 2>&1
adb shell wm dismiss-keyguard >nul 2>&1

rem ------------------------------------------------------------
rem Session B:
rem Run Compose Navigation tests in a fresh instrumentation process.
rem ------------------------------------------------------------

echo.
echo [7B/7] Running Compose UI instrumented tests...
echo Selected class:
echo %COMPOSE_TEST_CLASS%
echo.

adb shell am instrument ^
    -w ^
    -r ^
    -e class "%COMPOSE_TEST_CLASS%" ^
    "%TEST_COMPONENT%" ^
    > "%UI_REPORT_FILE%" 2>&1

set "UI_ADB_EXIT_CODE=%ERRORLEVEL%"

echo.
echo Compose UI instrumentation adb process returned.
echo ADB exit code: %UI_ADB_EXIT_CODE%
echo.

if not exist "%UI_REPORT_FILE%" goto :ui_report_missing

type "%UI_REPORT_FILE%"

powershell.exe ^
    -NoLogo ^
    -NoProfile ^
    -ExecutionPolicy Bypass ^
    -File "%VALIDATOR_SCRIPT%" ^
    -ReportFile "%UI_REPORT_FILE%" ^
    -AdbExitCode "%UI_ADB_EXIT_CODE%"

set "UI_VALIDATION_EXIT_CODE=%ERRORLEVEL%"

if not "%UI_VALIDATION_EXIT_CODE%"=="0" goto :ui_tests_failed

goto :full_suite_passed


:run_selected_test

echo Selected test:
echo %~1
echo.

adb shell am force-stop "%TARGET_PACKAGE%" >nul 2>&1
adb shell am force-stop "%TEST_PACKAGE%" >nul 2>&1

adb shell am instrument ^
    -w ^
    -r ^
    -e class "%~1" ^
    "%TEST_COMPONENT%" ^
    > "%SELECTED_REPORT_FILE%" 2>&1

set "SELECTED_ADB_EXIT_CODE=%ERRORLEVEL%"

echo.
echo Selected instrumentation adb process returned.
echo ADB exit code: %SELECTED_ADB_EXIT_CODE%
echo.

if not exist "%SELECTED_REPORT_FILE%" goto :selected_report_missing

type "%SELECTED_REPORT_FILE%"

powershell.exe ^
    -NoLogo ^
    -NoProfile ^
    -ExecutionPolicy Bypass ^
    -File "%VALIDATOR_SCRIPT%" ^
    -ReportFile "%SELECTED_REPORT_FILE%" ^
    -AdbExitCode "%SELECTED_ADB_EXIT_CODE%"

set "SELECTED_VALIDATION_EXIT_CODE=%ERRORLEVEL%"

if not "%SELECTED_VALIDATION_EXIT_CODE%"=="0" goto :selected_test_failed

goto :selected_test_passed


:non_ui_report_missing
echo.
echo ============================================================
echo NON-UI INSTRUMENTATION REPORT WAS NOT GENERATED
echo ============================================================
echo.
echo Expected report:
echo %NON_UI_REPORT_FILE%
echo.
exit /b 1


:ui_report_missing
echo.
echo ============================================================
echo COMPOSE UI INSTRUMENTATION REPORT WAS NOT GENERATED
echo ============================================================
echo.
echo Expected report:
echo %UI_REPORT_FILE%
echo.
exit /b 1


:selected_report_missing
echo.
echo ============================================================
echo SELECTED INSTRUMENTATION REPORT WAS NOT GENERATED
echo ============================================================
echo.
echo Expected report:
echo %SELECTED_REPORT_FILE%
echo.
exit /b 1


:non_ui_tests_failed
echo.
echo ============================================================
echo NON-UI INSTRUMENTED TESTS FAILED
echo ============================================================
echo.
echo Validator exit code:
echo %NON_UI_VALIDATION_EXIT_CODE%
echo.
echo Report:
echo %NON_UI_REPORT_FILE%
echo.
exit /b 1


:ui_tests_failed
echo.
echo ============================================================
echo COMPOSE UI INSTRUMENTED TESTS FAILED
echo ============================================================
echo.
echo Validator exit code:
echo %UI_VALIDATION_EXIT_CODE%
echo.
echo Report:
echo %UI_REPORT_FILE%
echo.
exit /b 1


:selected_test_failed
echo.
echo ============================================================
echo SELECTED INSTRUMENTED TEST FAILED
echo ============================================================
echo.
echo Validator exit code:
echo %SELECTED_VALIDATION_EXIT_CODE%
echo.
echo Report:
echo %SELECTED_REPORT_FILE%
echo.
exit /b 1


:full_suite_passed
echo.
echo ============================================================
echo ALL INSTRUMENTED TESTS PASSED
echo ============================================================
echo.
echo Non-UI report:
echo %NON_UI_REPORT_FILE%
echo.
echo Compose UI report:
echo %UI_REPORT_FILE%
echo.
echo The full suite was intentionally executed in two isolated
echo instrumentation sessions.
echo.
exit /b 0


:selected_test_passed
echo.
echo ============================================================
echo SELECTED INSTRUMENTED TESTS PASSED
echo ============================================================
echo.
echo Report:
echo %SELECTED_REPORT_FILE%
echo.
exit /b 0