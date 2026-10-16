#!/usr/bin/env bash
set -euo pipefail
TOOLS="$HOME/.local/share/fund-assistant"
mkdir -p "$TOOLS/android/cmdline-tools"
if [ ! -x "$TOOLS/android/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -fL --retry 3 https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$TOOLS/cmdline.zip"
  unzip -q -o "$TOOLS/cmdline.zip" -d "$TOOLS/android/cmdline-tools"
  mv "$TOOLS/android/cmdline-tools/cmdline-tools" "$TOOLS/android/cmdline-tools/latest"
fi
if [ ! -x "$TOOLS/gradle-8.9/bin/gradle" ]; then
  curl -fL --retry 3 https://services.gradle.org/distributions/gradle-8.9-bin.zip -o "$TOOLS/gradle.zip"
  unzip -q -o "$TOOLS/gradle.zip" -d "$TOOLS"
fi
export ANDROID_HOME="$TOOLS/android"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$(dirname "$0")/../local.properties"
"$TOOLS/gradle-8.9/bin/gradle" --version
