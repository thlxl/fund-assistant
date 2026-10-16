#!/usr/bin/env bash
set -euo pipefail
export ANDROID_HOME="$HOME/.local/share/fund-assistant/android"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
cd "$(dirname "$0")/.."
"$HOME/.local/share/fund-assistant/gradle-8.9/bin/gradle" testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest
deadline=$((SECONDS + 600))
until [ "$(adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = '1' ]; do
  if [ "$SECONDS" -ge "$deadline" ]; then printf 'Emulator boot timed out\n'; exit 1; fi
  sleep 5
done
adb -s emulator-5554 shell settings put system system_locales zh-CN
adb -s emulator-5554 shell wm size 720x1520
adb -s emulator-5554 shell wm density 300
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w cn.local.fund.test/android.test.InstrumentationTestRunner | tee /tmp/fund-instrumentation.log
grep -q 'OK (' /tmp/fund-instrumentation.log
adb -s emulator-5554 shell am start -n cn.local.fund/.MainActivity
mkdir -p dist
adb -s emulator-5554 exec-out screencap -p > dist/app-screenshot.png
