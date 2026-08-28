#!/usr/bin/env bash
set -euo pipefail

PIN="246810"
TARGET_PACKAGE="ir.carepack.debug"
EVIDENCE_FILE="carepack-performance-evidence.txt"

cleanup() {
  adb shell locksettings clear --old "$PIN" >/dev/null 2>&1 || true
}

trap cleanup EXIT

# The target package must exist before runtime permissions/app-ops are applied.
./gradlew --no-daemon --no-configuration-cache --dependency-verification strict :app:installDebug

adb shell locksettings set-pin "$PIN"
adb shell pm grant "$TARGET_PACKAGE" android.permission.POST_NOTIFICATIONS
adb shell appops set "$TARGET_PACKAGE" SCHEDULE_EXACT_ALARM allow

# Keep the emulator alive long enough to collect evidence even when tests fail.
test_exit=0
./gradlew --no-daemon --no-configuration-cache --dependency-verification strict connectedDebugAndroidTest || test_exit=$?

# Preserve the instrumentation failure as the primary result.
if [ "$test_exit" -ne 0 ]; then
  exit "$test_exit"
fi

# UTP copies additionalTestOutputDir to this host output tree before
# uninstalling the app/test APKs.
HOST_EVIDENCE_ROOT="app/build/outputs/connected_android_test_additional_output"

HOST_EVIDENCE_FILE="$(
  find "$HOST_EVIDENCE_ROOT" \
    -type f \
    -name "$EVIDENCE_FILE" \
    -print \
    -quit \
    2>/dev/null || true
)"

if [ -z "$HOST_EVIDENCE_FILE" ] || [ ! -s "$HOST_EVIDENCE_FILE" ]; then
  echo "Performance evidence was not copied by UTP or is empty." >&2
  exit 1
fi

echo "Performance evidence: $HOST_EVIDENCE_FILE"