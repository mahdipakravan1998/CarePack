@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem ------------------------------------------------------------
rem CarePack Xiaomi physical-device instrumented test runner
rem
rem Full suite:
rem   tools\run-xiaomi-android-tests.cmd
rem
rem One non-Compose class:
rem   tools\run-xiaomi-android-tests.cmd "ir.carepack.reporting.ReportingIntegrationTest"
rem
rem One Compose class:
rem   tools\run-xiaomi-android-tests.cmd "ir.carepack.ui.ReportingPrivacyDeletionComposeTest"
rem
rem Another Compose class:
rem   tools\run-xiaomi-android-tests.cmd "ir.carepack.ui.ReportingComposeTest"
rem
rem One method:
rem   tools\run-xiaomi-android-tests.cmd "ir.carepack.ui.ReportingComposeTest#detail_exposesAllThreeReportActions"
rem ------------------------------------------------------------

cd /d "%~dp0.."

set "GRADLEW=%CD%\gradlew.bat"

set "TARGET_PACKAGE=ir.carepack.debug"
set "TEST_PACKAGE=ir.carepack.debug.test"
set "TEST_RUNNER=androidx.test.runner.AndroidJUnitRunner"
set "TEST_COMPONENT=%TEST_PACKAGE%/%TEST_RUNNER%"

set "REPORTING_PRIVACY_DELETION_COMPOSE_CLASS=ir.carepack.ui.ReportingPrivacyDeletionComposeTest"
set "REPORTING_COMPOSE_CLASS=ir.carepack.ui.ReportingComposeTest"
set "CAREPACK_COMPOSE_CLASS=ir.carepack.ui.CarePackComposeTest"

set "COMPOSE_EXCLUDED_CLASSES=%REPORTING_PRIVACY_DELETION_COMPOSE_CLASS%,%REPORTING_COMPOSE_CLASS%,%CAREPACK_COMPOSE_CLASS%"

set "TARGET_APK=%CD%\app\build\outputs\apk\debug\app-debug.apk"
set "TEST_APK=%CD%\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"

set "REPORT_DIRECTORY=%CD%\app\build\reports\xiaomiAndroidTest"

set "NON_UI_REPORT_FILE=%REPORT_DIRECTORY%\instrumentation-non-ui-result.txt"
set "UI_REPORT_FILE=%REPORT_DIRECTORY%\instrumentation-ui-result.txt"
set "UI_SESSION_REPORT_FILE=%REPORT_DIRECTORY%\instrumentation-ui-session-result.txt"
set "SELECTED_REPORT_FILE=%REPORT_DIRECTORY%\instrumentation-selected-result.txt"

set "PERMISSION_SCRIPT=%CD%\tools\grant-xiaomi-ui-permissions.ps1"
set "INSTRUMENTATION_SCRIPT=%CD%\tools\run-adb-instrumentation.ps1"
set "VALIDATOR_SCRIPT=%CD%\tools\validate-instrumentation-report.ps1"

set "INSTRUMENTATION_TIMEOUT_SECONDS=180"
set "PACKAGE_VISIBILITY_TIMEOUT_SECONDS=20"
set "XIAOMI_PERMISSION_ATTEMPTS=2"

set "REPORTING_PRIVACY_DELETION_COMPOSE_METHODS=reportPreview_displaysExactTextAndExplicitActionsAtTwoHundredPercentFont privacyScreen_isLocalOnlyAndDoesNotExposeExternalPolicyAction deleteEverything_confirmationProgressAndFailureStatesAreExplicit"

set "REPORTING_COMPOSE_METHODS=noReportAndUnknown_haveDifferentVisibleText detail_exposesAllThreeReportActions cancelledOccurrence_disablesNewReportActions undoSnackbar_callsCurrentUndoToken todayShowsSeparateNoMedicationEmptyState historySectionShowsGroupedRecentHistory"

set "CAREPACK_COMPOSE_METHODS=completedCarePlan_startsAtToday_andRecordsGiven settingsPrivacy_opensLocalPrivacyScreen"

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

if not exist "%INSTRUMENTATION_SCRIPT%" (
    echo ERROR: Instrumentation PowerShell runner was not found:
    echo %INSTRUMENTATION_SCRIPT%
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

for /f "usebackq delims=" %%U in (`adb shell am get-current-user`) do (
    set "DEVICE_USER_ID=%%U"
)

if not defined DEVICE_USER_ID (
    set "DEVICE_USER_ID=0"
)

echo Device manufacturer: %DEVICE_MANUFACTURER%
echo Device model:        %DEVICE_MODEL%
echo Android version:     %ANDROID_VERSION%
echo Android user:        %DEVICE_USER_ID%
echo.

echo [1/8] Building application and test APKs...

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
echo [2/8] Stopping previous processes...

adb shell am force-stop "%TARGET_PACKAGE%" >nul 2>&1
adb shell am force-stop "%TEST_PACKAGE%" >nul 2>&1
adb shell am force-stop com.miui.securitycenter >nul 2>&1

echo.
echo [3/8] Updating and verifying application APK...

call :install_and_verify_target_apk

if errorlevel 1 (
    echo.
    echo ERROR: Application APK installation or verification failed.
    exit /b 1
)

echo.
echo [4/8] Installing and verifying a fresh Android test APK...

call :install_and_verify_test_apk

if errorlevel 1 (
    echo.
    echo ERROR: Android test APK installation or verification failed.
    exit /b 1
)

echo.
echo [5/8] Waiting for instrumentation registration...

call :wait_for_instrumentation

if errorlevel 1 (
    echo.
    echo Instrumentation was not registered after the first install.
    echo Reinstalling the Android test APK once...
    echo.

    call :install_and_verify_test_apk

    if errorlevel 1 (
        echo.
        echo ERROR: Android test APK reinstallation failed.
        exit /b 1
    )

    call :wait_for_instrumentation

    if errorlevel 1 (
        echo.
        echo ERROR: Instrumentation component was not registered:
        echo %TEST_COMPONENT%
        echo.
        echo Registered instrumentation components:
        adb shell pm list instrumentation
        exit /b 1
    )
)

echo Instrumentation registered:
echo %TEST_COMPONENT%

echo.
echo [6/8] Granting Xiaomi background-window permission...

call :grant_xiaomi_permission

if errorlevel 1 (
    echo.
    echo WARNING: Xiaomi UI permission automation did not complete.
    echo The APKs are installed and verified, so the test run will continue.
    echo If MIUI blocks a test window, set this permission manually:
    echo Open new windows while running in the background = Always allow
    echo.
)

echo.
echo [7/8] Preparing physical device...

adb shell input keyevent 224 >nul 2>&1
adb shell wm dismiss-keyguard >nul 2>&1
adb shell input keyevent 3 >nul 2>&1

adb shell settings put global window_animation_scale 0 >nul 2>&1
adb shell settings put global transition_animation_scale 0 >nul 2>&1
adb shell settings put global animator_duration_scale 0 >nul 2>&1

adb shell svc power stayon true >nul 2>&1

adb shell am set-standby-bucket "%TARGET_PACKAGE%" active >nul 2>&1
adb shell am set-standby-bucket "%TEST_PACKAGE%" active >nul 2>&1

adb shell dumpsys deviceidle whitelist +"%TARGET_PACKAGE%" >nul 2>&1
adb shell dumpsys deviceidle whitelist +"%TEST_PACKAGE%" >nul 2>&1

adb shell cmd appops set "%TARGET_PACKAGE%" RUN_IN_BACKGROUND allow >nul 2>&1
adb shell cmd appops set "%TARGET_PACKAGE%" RUN_ANY_IN_BACKGROUND allow >nul 2>&1

adb shell cmd appops set "%TEST_PACKAGE%" RUN_IN_BACKGROUND allow >nul 2>&1
adb shell cmd appops set "%TEST_PACKAGE%" RUN_ANY_IN_BACKGROUND allow >nul 2>&1

adb shell am force-stop com.miui.securitycenter >nul 2>&1
adb shell input keyevent 3 >nul 2>&1

timeout /t 1 /nobreak >nul

if not exist "%REPORT_DIRECTORY%" (
    mkdir "%REPORT_DIRECTORY%"
)

if exist "%NON_UI_REPORT_FILE%" (
    del /f /q "%NON_UI_REPORT_FILE%" >nul 2>&1
)

if exist "%UI_REPORT_FILE%" (
    del /f /q "%UI_REPORT_FILE%" >nul 2>&1
)

if exist "%UI_SESSION_REPORT_FILE%" (
    del /f /q "%UI_SESSION_REPORT_FILE%" >nul 2>&1
)

if exist "%SELECTED_REPORT_FILE%" (
    del /f /q "%SELECTED_REPORT_FILE%" >nul 2>&1
)

echo.
echo [8/8] Running instrumented tests...
echo.

if not "%~1"=="" goto :dispatch_selected_test

goto :run_full_suite


:dispatch_selected_test

if /I "%~1"=="%REPORTING_PRIVACY_DELETION_COMPOSE_CLASS%" (
    goto :run_selected_reporting_privacy_deletion_compose_class
)

if /I "%~1"=="%REPORTING_COMPOSE_CLASS%" (
    goto :run_selected_reporting_compose_class
)

if /I "%~1"=="%CAREPACK_COMPOSE_CLASS%" (
    goto :run_selected_carepack_compose_class
)

goto :run_selected_single


:run_selected_single

call :run_and_validate ^
    "class" ^
    "%~1" ^
    "%SELECTED_REPORT_FILE%" ^
    "Selected instrumented test"

if errorlevel 1 goto :selected_test_failed

goto :selected_test_passed


:run_selected_reporting_privacy_deletion_compose_class

type nul > "%SELECTED_REPORT_FILE%"

for %%M in (%REPORTING_PRIVACY_DELETION_COMPOSE_METHODS%) do (
    call :run_and_validate ^
        "class" ^
        "%REPORTING_PRIVACY_DELETION_COMPOSE_CLASS%#%%M" ^
        "%UI_SESSION_REPORT_FILE%" ^
        "ReportingPrivacyDeletionComposeTest#%%M"

    if errorlevel 1 goto :selected_test_failed

    call :append_report ^
        "%REPORTING_PRIVACY_DELETION_COMPOSE_CLASS%#%%M" ^
        "%SELECTED_REPORT_FILE%" ^
        "%UI_SESSION_REPORT_FILE%"
)

goto :selected_test_passed


:run_selected_reporting_compose_class

type nul > "%SELECTED_REPORT_FILE%"

for %%M in (%REPORTING_COMPOSE_METHODS%) do (
    call :run_and_validate ^
        "class" ^
        "%REPORTING_COMPOSE_CLASS%#%%M" ^
        "%UI_SESSION_REPORT_FILE%" ^
        "ReportingComposeTest#%%M"

    if errorlevel 1 goto :selected_test_failed

    call :append_report ^
        "%REPORTING_COMPOSE_CLASS%#%%M" ^
        "%SELECTED_REPORT_FILE%" ^
        "%UI_SESSION_REPORT_FILE%"
)

goto :selected_test_passed


:run_selected_carepack_compose_class

type nul > "%SELECTED_REPORT_FILE%"

for %%M in (%CAREPACK_COMPOSE_METHODS%) do (
    call :run_and_validate ^
        "class" ^
        "%CAREPACK_COMPOSE_CLASS%#%%M" ^
        "%UI_SESSION_REPORT_FILE%" ^
        "CarePackComposeTest#%%M"

    if errorlevel 1 goto :selected_test_failed

    call :append_report ^
        "%CAREPACK_COMPOSE_CLASS%#%%M" ^
        "%SELECTED_REPORT_FILE%" ^
        "%UI_SESSION_REPORT_FILE%"
)

goto :selected_test_passed


:run_full_suite

echo [8A/8] Running non-Compose instrumented tests...
echo Excluded classes:
echo %COMPOSE_EXCLUDED_CLASSES%
echo.

call :run_and_validate ^
    "notClass" ^
    "%COMPOSE_EXCLUDED_CLASSES%" ^
    "%NON_UI_REPORT_FILE%" ^
    "Non-Compose instrumented tests"

if errorlevel 1 goto :non_ui_tests_failed

type nul > "%UI_REPORT_FILE%"

echo.
echo [8B/8] Running ReportingPrivacyDeletionComposeTest methods in isolated sessions...
echo.

for %%M in (%REPORTING_PRIVACY_DELETION_COMPOSE_METHODS%) do (
    call :run_and_validate ^
        "class" ^
        "%REPORTING_PRIVACY_DELETION_COMPOSE_CLASS%#%%M" ^
        "%UI_SESSION_REPORT_FILE%" ^
        "ReportingPrivacyDeletionComposeTest#%%M"

    if errorlevel 1 goto :ui_tests_failed

    call :append_report ^
        "%REPORTING_PRIVACY_DELETION_COMPOSE_CLASS%#%%M" ^
        "%UI_REPORT_FILE%" ^
        "%UI_SESSION_REPORT_FILE%"
)

echo.
echo [8C/8] Running ReportingComposeTest methods in isolated sessions...
echo.

for %%M in (%REPORTING_COMPOSE_METHODS%) do (
    call :run_and_validate ^
        "class" ^
        "%REPORTING_COMPOSE_CLASS%#%%M" ^
        "%UI_SESSION_REPORT_FILE%" ^
        "ReportingComposeTest#%%M"

    if errorlevel 1 goto :ui_tests_failed

    call :append_report ^
        "%REPORTING_COMPOSE_CLASS%#%%M" ^
        "%UI_REPORT_FILE%" ^
        "%UI_SESSION_REPORT_FILE%"
)

echo.
echo [8D/8] Running CarePackComposeTest methods in isolated sessions...
echo.

for %%M in (%CAREPACK_COMPOSE_METHODS%) do (
    call :run_and_validate ^
        "class" ^
        "%CAREPACK_COMPOSE_CLASS%#%%M" ^
        "%UI_SESSION_REPORT_FILE%" ^
        "CarePackComposeTest#%%M"

    if errorlevel 1 goto :ui_tests_failed

    call :append_report ^
        "%CAREPACK_COMPOSE_CLASS%#%%M" ^
        "%UI_REPORT_FILE%" ^
        "%UI_SESSION_REPORT_FILE%"
)

goto :full_suite_passed


:run_and_validate

set "RUN_FILTER_ARGUMENT=%~1"
set "RUN_FILTER_VALUE=%~2"
set "RUN_REPORT_FILE=%~3"
set "RUN_DISPLAY_NAME=%~4"

set "LAST_FILTER_VALUE=%RUN_FILTER_VALUE%"
set "LAST_REPORT_FILE=%RUN_REPORT_FILE%"

echo.
echo ------------------------------------------------------------
echo Running:
echo %RUN_DISPLAY_NAME%
echo ------------------------------------------------------------
echo.

call :prepare_test_session

if errorlevel 1 (
    exit /b 1
)

if exist "%RUN_REPORT_FILE%" (
    del /f /q "%RUN_REPORT_FILE%" >nul 2>&1
)

powershell.exe ^
    -NoLogo ^
    -NoProfile ^
    -ExecutionPolicy Bypass ^
    -File "%INSTRUMENTATION_SCRIPT%" ^
    -Component "%TEST_COMPONENT%" ^
    -ReportFile "%RUN_REPORT_FILE%" ^
    -TargetPackage "%TARGET_PACKAGE%" ^
    -TestPackage "%TEST_PACKAGE%" ^
    -FilterArgumentName "%RUN_FILTER_ARGUMENT%" ^
    -FilterValue "%RUN_FILTER_VALUE%" ^
    -TimeoutSeconds "%INSTRUMENTATION_TIMEOUT_SECONDS%"

set "RUN_ADB_EXIT_CODE=!ERRORLEVEL!"

echo.
echo Instrumentation process returned.
echo ADB exit code: !RUN_ADB_EXIT_CODE!
echo.

if not exist "%RUN_REPORT_FILE%" (
    echo ERROR: Instrumentation report was not generated:
    echo %RUN_REPORT_FILE%
    exit /b 1
)

type "%RUN_REPORT_FILE%"

powershell.exe ^
    -NoLogo ^
    -NoProfile ^
    -ExecutionPolicy Bypass ^
    -File "%VALIDATOR_SCRIPT%" ^
    -ReportFile "%RUN_REPORT_FILE%" ^
    -AdbExitCode "!RUN_ADB_EXIT_CODE!"

set "RUN_VALIDATION_EXIT_CODE=!ERRORLEVEL!"

if not "!RUN_VALIDATION_EXIT_CODE!"=="0" (
    exit /b !RUN_VALIDATION_EXIT_CODE!
)

exit /b 0


:install_and_verify_target_apk

adb install ^
    --user "%DEVICE_USER_ID%" ^
    -r ^
    -t ^
    -g ^
    "%TARGET_APK%"

if errorlevel 1 (
    exit /b 1
)

call :wait_for_package "%TARGET_PACKAGE%"

if not errorlevel 1 (
    exit /b 0
)

echo.
echo Target package was not visible after the first install.
echo Performing one clean reinstall...

adb uninstall "%TARGET_PACKAGE%" >nul 2>&1

adb install ^
    --user "%DEVICE_USER_ID%" ^
    -t ^
    -g ^
    "%TARGET_APK%"

if errorlevel 1 (
    exit /b 1
)

call :wait_for_package "%TARGET_PACKAGE%"

exit /b %ERRORLEVEL%


:install_and_verify_test_apk

rem A fresh test-package install prevents stale or missing
rem instrumentation registration after interrupted Gradle runs.
adb uninstall "%TEST_PACKAGE%" >nul 2>&1

adb install ^
    --user "%DEVICE_USER_ID%" ^
    -t ^
    -g ^
    "%TEST_APK%"

if errorlevel 1 (
    exit /b 1
)

call :wait_for_package "%TEST_PACKAGE%"

if not errorlevel 1 (
    exit /b 0
)

echo.
echo Test package was not visible after the first install.
echo Performing one clean reinstall...

adb uninstall "%TEST_PACKAGE%" >nul 2>&1

adb install ^
    --user "%DEVICE_USER_ID%" ^
    -t ^
    -g ^
    "%TEST_APK%"

if errorlevel 1 (
    exit /b 1
)

call :wait_for_package "%TEST_PACKAGE%"

exit /b %ERRORLEVEL%


:grant_xiaomi_permission

for /L %%I in (1,1,%XIAOMI_PERMISSION_ATTEMPTS%) do (
    call :ensure_target_package

    if errorlevel 1 (
        exit /b 1
    )

    powershell.exe ^
        -NoLogo ^
        -NoProfile ^
        -ExecutionPolicy Bypass ^
        -File "%PERMISSION_SCRIPT%" ^
        -PackageName "%TARGET_PACKAGE%"

    if not errorlevel 1 (
        exit /b 0
    )

    echo.
    echo Xiaomi permission attempt %%I of %XIAOMI_PERMISSION_ATTEMPTS% failed.

    adb shell am force-stop com.miui.securitycenter >nul 2>&1
    adb shell input keyevent 3 >nul 2>&1

    timeout /t 2 /nobreak >nul
)

exit /b 1


:ensure_target_package

call :wait_for_package "%TARGET_PACKAGE%"

if not errorlevel 1 (
    exit /b 0
)

echo.
echo Target package disappeared before permission setup.
echo Reinstalling and verifying the target APK...

call :install_and_verify_target_apk

exit /b %ERRORLEVEL%


:wait_for_package

set "PACKAGE_TO_WAIT_FOR=%~1"

for /L %%I in (1,1,%PACKAGE_VISIBILITY_TIMEOUT_SECONDS%) do (
    adb shell pm path --user "%DEVICE_USER_ID%" "%PACKAGE_TO_WAIT_FOR%" 2>nul ^
        | findstr /B /C:"package:" >nul

    if not errorlevel 1 (
        exit /b 0
    )

    timeout /t 1 /nobreak >nul
)

echo.
echo Package was not visible to Android user %DEVICE_USER_ID%:
echo %PACKAGE_TO_WAIT_FOR%
echo.

adb shell pm list packages --user "%DEVICE_USER_ID%" ^
    | findstr /I /C:"carepack"

exit /b 1


:prepare_test_session

adb get-state >nul 2>&1

if errorlevel 1 (
    echo ERROR: Device disconnected before instrumentation.
    exit /b 1
)

call :ensure_target_package

if errorlevel 1 (
    echo ERROR: Target package is unavailable before instrumentation.
    exit /b 1
)

call :wait_for_instrumentation

if errorlevel 1 (
    echo ERROR: Instrumentation registration disappeared:
    echo %TEST_COMPONENT%
    exit /b 1
)

adb shell am force-stop "%TARGET_PACKAGE%" >nul 2>&1
adb shell am force-stop "%TEST_PACKAGE%" >nul 2>&1
adb shell am force-stop com.miui.securitycenter >nul 2>&1

adb shell input keyevent 224 >nul 2>&1
adb shell wm dismiss-keyguard >nul 2>&1
adb shell input keyevent 3 >nul 2>&1

timeout /t 1 /nobreak >nul

exit /b 0


:wait_for_instrumentation

for /L %%I in (1,1,15) do (
    adb shell pm list instrumentation 2>nul ^
        | findstr /C:"instrumentation:%TEST_COMPONENT% (target=%TARGET_PACKAGE%)" >nul

    if not errorlevel 1 (
        exit /b 0
    )

    timeout /t 1 /nobreak >nul
)

exit /b 1


:append_report

set "APPEND_TEST_NAME=%~1"
set "APPEND_TARGET_FILE=%~2"
set "APPEND_SOURCE_FILE=%~3"

>> "%APPEND_TARGET_FILE%" echo.
>> "%APPEND_TARGET_FILE%" echo ============================================================
>> "%APPEND_TARGET_FILE%" echo TEST: %APPEND_TEST_NAME%
>> "%APPEND_TARGET_FILE%" echo ============================================================
>> "%APPEND_TARGET_FILE%" echo.

type "%APPEND_SOURCE_FILE%" >> "%APPEND_TARGET_FILE%"

exit /b 0


:non_ui_tests_failed

echo.
echo ============================================================
echo NON-COMPOSE INSTRUMENTED TESTS FAILED
echo ============================================================
echo.
echo Failed filter:
echo %LAST_FILTER_VALUE%
echo.
echo Report:
echo %LAST_REPORT_FILE%
echo.

exit /b 1


:ui_tests_failed

echo.
echo ============================================================
echo COMPOSE UI INSTRUMENTED TEST FAILED
echo ============================================================
echo.
echo Failed filter:
echo %LAST_FILTER_VALUE%
echo.
echo Individual report:
echo %LAST_REPORT_FILE%
echo.
echo Aggregate report:
echo %UI_REPORT_FILE%
echo.

exit /b 1


:selected_test_failed

echo.
echo ============================================================
echo SELECTED INSTRUMENTED TEST FAILED
echo ============================================================
echo.
echo Failed filter:
echo %LAST_FILTER_VALUE%
echo.
echo Report:
echo %LAST_REPORT_FILE%
echo.

exit /b 1


:full_suite_passed

echo.
echo ============================================================
echo ALL INSTRUMENTED TESTS PASSED
echo ============================================================
echo.
echo Non-Compose report:
echo %NON_UI_REPORT_FILE%
echo.
echo Isolated Compose report:
echo %UI_REPORT_FILE%
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
