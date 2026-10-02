#!/usr/bin/env bash
set -euo pipefail

AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
PACKAGE="com.learnova.app"

# android-emulator-runner changes the working directory for each script command.
# Resolve the release APK from the checkout explicitly instead of relying on
# GITHUB_WORKSPACE or per-line shell state.
APK=""
for candidate in "./Learnova.apk" "$(pwd)/Learnova.apk" "${GITHUB_WORKSPACE:-}/Learnova.apk"; do
  if [ -f "$candidate" ]; then
    APK="$candidate"
    break
  fi
done

if [ -z "$APK" ]; then
  echo "::error::Release APK was not found."
  echo "GITHUB_WORKSPACE=${GITHUB_WORKSPACE:-<unset>}"
  echo "PWD=$(pwd)"
  find "$GITHUB_WORKSPACE" -maxdepth 3 -type f -name '*.apk' -print 2>/dev/null || true
  find . -maxdepth 3 -type f -name '*.apk' -print 2>/dev/null || true
  exit 1
fi

test -r "$APK" || { echo "::error::Release APK is not readable at $APK"; exit 1; }
test -x "$AAPT2" || { echo "::error::aapt2 is not executable at $AAPT2"; exit 1; }

BADGING="$("$AAPT2" dump badging "$APK")"
printf '%s\n' "$BADGING" | grep -F "package: name='$PACKAGE'" >/dev/null

VERSION_CODE="$(printf '%s\n' "$BADGING" | sed -n "s/.*versionCode='\\([^']*\\)'.*/\\1/p" | head -n 1)"
VERSION_NAME="$(printf '%s\n' "$BADGING" | sed -n "s/.*versionName='\\([^']*\\)'.*/\\1/p" | head -n 1)"
test -n "$VERSION_CODE"
test -n "$VERSION_NAME"

echo "Current release: $PACKAGE versionCode=$VERSION_CODE versionName=$VERSION_NAME"
echo "Smoke-test APK: $APK"

adb wait-for-device

BOOT_COMPLETED=""
i=0
while [ "$i" -lt 90 ]; do
  BOOT_COMPLETED="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [ "$BOOT_COMPLETED" = "1" ]; then
    break
  fi
  i=$((i + 1))
  sleep 1
done
test "$BOOT_COMPLETED" = "1"

echo "== Clean install =="
adb uninstall "$PACKAGE" >/dev/null 2>&1 || true
if adb shell pm path "$PACKAGE" >/dev/null 2>&1; then
  echo "::error::Package still exists after uninstall: $PACKAGE"
  exit 1
fi

adb install --no-streaming "$APK"
adb shell pm path "$PACKAGE" | grep -F "$PACKAGE" >/dev/null
adb shell dumpsys package "$PACKAGE" | grep -F "versionCode=$VERSION_CODE" >/dev/null

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.MainActivity" > /tmp/learnova-launch-install.txt
grep -F "Status: ok" /tmp/learnova-launch-install.txt >/dev/null
adb shell dumpsys activity top | grep -F "$PACKAGE/.MainActivity" >/dev/null

echo "Clean install + explicit launcher activity smoke test passed."

echo "== Signed in-place update/replace =="
adb install --no-streaming -r "$APK"
adb shell pm path "$PACKAGE" | grep -F "$PACKAGE" >/dev/null
adb shell dumpsys package "$PACKAGE" | grep -F "versionCode=$VERSION_CODE" >/dev/null

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.MainActivity" > /tmp/learnova-launch-update.txt
grep -F "Status: ok" /tmp/learnova-launch-update.txt >/dev/null
adb shell dumpsys activity top | grep -F "$PACKAGE/.MainActivity" >/dev/null

echo "Signed update/replace + explicit launcher activity smoke test passed."

echo "== Runtime Macrobenchmark performance validation =="
# The release APK is already installed on the live API-35 emulator. Run the
# production-like benchmark APK against that exact installed build so startup,
# frame timing, and Learnova trace sections are measured on a real device.
gradle --no-daemon :macrobenchmark:connectedBenchmarkAndroidTest --stacktrace

echo "Runtime Macrobenchmark performance validation passed."

rm -f /tmp/learnova-launch-install.txt /tmp/learnova-launch-update.txt
echo "Install/update safety + runtime performance gates passed."
