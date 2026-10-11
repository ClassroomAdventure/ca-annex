#!/bin/bash
# 配布物を dist/ に作る: SideScreen-mac-<ver>.zip と SideScreen-android-<ver>.apk
# 使い方: scripts/build-release.sh 1.0.0
set -euo pipefail
cd "$(dirname "$0")/.."
VER=${1:?version}
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
mkdir -p dist

# Android（リリース鍵は 1Password から）
if [ ! -f android/keystore/sidescreen-release.jks ]; then
  mkdir -p android/keystore
  op document get ca-sidescreen-release-keystore --vault Dev --out-file android/keystore/sidescreen-release.jks
fi
SIDESCREEN_KEYSTORE_PASSWORD=$(op read op://Dev/env-ca-sidescreen/SIDESCREEN_KEYSTORE_PASSWORD) \
  bash -c 'cd android && ./gradlew assembleRelease -q'
cp android/app/build/outputs/apk/release/app-release.apk "dist/SideScreen-android-$VER.apk"

# Mac（Developer ID が無いので ad-hoc 署名。初回起動の手順は README）
SIGN_IDENTITY=- mac/build-app.sh
(cd mac/build && ditto -c -k --keepParent SideScreen.app "../../dist/SideScreen-mac-$VER.zip")
ls -la dist
