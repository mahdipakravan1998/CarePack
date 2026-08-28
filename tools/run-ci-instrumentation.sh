#!/usr/bin/env bash
set -euo pipefail

PIN="246810"
TARGET_PACKAGE="ir.carepack.debug"
EVIDENCE_DIR="build/performance-evidence"
EVIDENCE_FILE="${EVIDENCE_DIR}/carepack-performance-evidence.txt"

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

mkdir -p "$EVIDENCE_DIR"

perf_exit=0
adb shell run-as "$TARGET_PACKAGE" cat files/carepack-performance-evidence.txt > "$EVIDENCE_FILE" || perf_exit=$?

# Preserve the instrumentation failure as the primary result.
if [ "$test_exit" -ne 0 ]; then
  exit "$test_exit"
fi

exit "$perf_exit"