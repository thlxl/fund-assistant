#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
packages=()
command -v javac >/dev/null || packages+=(openjdk-17-jdk-headless)
command -v curl >/dev/null || packages+=(curl)
command -v unzip >/dev/null || packages+=(unzip)
if [ "${#packages[@]}" -gt 0 ]; then
  if [ "$(id -u)" -eq 0 ]; then
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y "${packages[@]}"
  else
    sudo -n apt-get update -qq
    sudo -n env DEBIAN_FRONTEND=noninteractive apt-get install -y "${packages[@]}"
  fi
fi
bash tools/setup-wsl.sh
bash gradlew testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest
test -s app/build/outputs/apk/debug/app-debug.apk
