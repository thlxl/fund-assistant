#!/usr/bin/env bash
set -euo pipefail
export ANDROID_HOME="$HOME/.local/share/fund-assistant/android"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
if [ ! -f "$HOME/.android/avd/fund-test.avd/config.ini" ]; then
  printf 'no\n' | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n fund-test -k 'system-images;android-35;default;x86_64' --device 'pixel_4'
fi
exec "$ANDROID_HOME/emulator/emulator" -avd fund-test -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader -accel off -memory 2048 -cores 2
