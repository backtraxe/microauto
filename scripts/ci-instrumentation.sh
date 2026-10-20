#!/usr/bin/env bash
set -uo pipefail

./gradlew :app:connectedDebugAndroidTest \
  --no-daemon \
  -Pandroid.builder.sdkDownload=false
status=$?

adb logcat -d -v threadtime > logcat.txt 2>&1 || true
adb logcat -b crash -d -v threadtime > logcat-crash.txt 2>&1 || true
adb shell dumpsys dropbox --print data_app_crash > dropbox-crash.txt 2>&1 || true
adb shell pm list packages | grep microauto > installed-packages.txt 2>&1 || true

exit "$status"
