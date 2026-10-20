#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
AVD_NAME="${AVD_NAME:-microauto_api35}"
SYSTEM_IMAGE="${SYSTEM_IMAGE:-system-images;android-35;default;arm64-v8a}"
EMULATOR="$SDK_ROOT/emulator/emulator"
ADB="$SDK_ROOT/platform-tools/adb"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/avdmanager"

if [ ! -x "$EMULATOR" ]; then
  echo "Emulator not found at $EMULATOR"
  echo "Install it with: sdkmanager \"emulator\" \"$SYSTEM_IMAGE\""
  exit 1
fi

IMAGE_PATH=$(printf '%s' "$SYSTEM_IMAGE" | tr ';' '/')
if [ ! -d "$SDK_ROOT/$IMAGE_PATH" ]; then
  echo "Installing $SYSTEM_IMAGE"
  yes | "$SDKMANAGER" "$SYSTEM_IMAGE" >/dev/null
fi

if ! "$EMULATOR" -list-avds | grep -qx "$AVD_NAME"; then
  echo "Creating AVD $AVD_NAME"
  echo no | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" -d pixel_6 --force
fi

"$ADB" start-server >/dev/null
"$EMULATOR" -avd "$AVD_NAME" \
  -no-window -no-audio -no-boot-anim \
  -gpu swiftshader_indirect -no-snapshot \
  > /tmp/microauto-emulator.log 2>&1 &
EMULATOR_PID=$!

cleanup() {
  "$ADB" emu kill >/dev/null 2>&1 || true
  kill "$EMULATOR_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT

"$ADB" wait-for-device
for _ in $(seq 1 120); do
  BOOT=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$BOOT" = "1" ] && break
  sleep 2
done

if [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; then
  echo "Emulator failed to boot. See /tmp/microauto-emulator.log"
  exit 1
fi

"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0

./gradlew :app:connectedDebugAndroidTest \
  --no-daemon \
  -Pandroid.builder.sdkDownload=false
