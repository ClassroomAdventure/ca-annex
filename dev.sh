#!/bin/bash
# Mac アプリと Android アプリをビルドして入れ直す（開発用）
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
SERIAL=${SERIAL:-$(adb devices | awk '$2=="device" && $1 !~ /[:.]/ {print $1; exit}')}
if [ -f android/keystore/annex-release.jks ] && [ -z "${ANNEX_KEYSTORE_PASSWORD:-}" ]; then
  ANNEX_KEYSTORE_PASSWORD=$(op read op://Dev/env-ca-annex/ANNEX_KEYSTORE_PASSWORD 2>/dev/null || true)
  export ANNEX_KEYSTORE_PASSWORD
fi
(cd android && ./gradlew assembleDebug -q)
mac/build-app.sh >/dev/null
pkill -x Annex || true
sleep 0.5
# いつも /Applications から動かす（画面収録などの許可がこの場所のアプリに付くため）
rm -rf /Applications/Annex.app
ditto mac/build/Annex.app /Applications/Annex.app
LOG=${LOG:-/tmp/annex.log}
ANNEX_DROP=${ANNEX_DROP:-} nohup /Applications/Annex.app/Contents/MacOS/Annex >"$LOG" 2>&1 &
adb -s "$SERIAL" install -r android/app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb -s "$SERIAL" shell am force-stop com.classroomadventure.annex
echo "ok (mac log: $LOG, device: $SERIAL)"
