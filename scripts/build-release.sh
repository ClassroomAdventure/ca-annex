#!/bin/bash
# 配布物を dist/ に作る: Annex-mac.zip と Annex-android.apk
# （ファイル名を固定にして、ページから releases/latest/download/<名前> で常に最新を落とせるようにする）
# 使い方: scripts/build-release.sh 1.0.0
set -euo pipefail
cd "$(dirname "$0")/.."
VER=${1:?version}
grep -q "versionName = \"$VER\"" android/app/build.gradle.kts || { echo "android versionName が $VER ではありません"; exit 1; }
grep -q "<string>$VER</string>" mac/build-app.sh || { echo "mac の CFBundleShortVersionString が $VER ではありません"; exit 1; }
rm -rf dist
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
mkdir -p dist

# Android（リリース鍵は 1Password から）
if [ ! -f android/keystore/annex-release.jks ]; then
  mkdir -p android/keystore
  op document get ca-annex-release-keystore --vault Dev --out-file android/keystore/annex-release.jks
fi
ANNEX_KEYSTORE_PASSWORD=$(op read op://Dev/env-ca-annex/ANNEX_KEYSTORE_PASSWORD) \
  bash -c 'cd android && ./gradlew assembleRelease bundleRelease -q'
cp android/app/build/outputs/apk/release/app-release.apk dist/Annex-android.apk
# Google Play に上げる用（アップロード鍵 = 上と同じリリース鍵）
cp android/app/build/outputs/bundle/release/app-release.aab dist/Annex-android.aab

# Mac（Developer ID が無いので ad-hoc 署名。初回起動の手順は README）
SIGN_IDENTITY=- mac/build-app.sh
(cd mac/build && ditto -c -k --keepParent Annex.app ../../dist/Annex-mac.zip)
ls -la dist
