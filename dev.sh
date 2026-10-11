#!/bin/bash
# Mac アプリと Android アプリをビルドして入れ直す（開発用）
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
SERIAL=${SERIAL:-$(adb devices | awk '$2=="device" && $1 !~ /[:.]/ {print $1; exit}')}
(cd android && ./gradlew assembleDebug -q)
mac/build-app.sh >/dev/null
pkill -x SideScreen || true
sleep 0.5
# いつも /Applications から動かす（画面収録などの許可がこの場所のアプリに付くため）
rm -rf /Applications/SideScreen.app
ditto mac/build/SideScreen.app /Applications/SideScreen.app
LOG=${LOG:-/tmp/sidescreen.log}
SIDESCREEN_DROP=${SIDESCREEN_DROP:-} nohup /Applications/SideScreen.app/Contents/MacOS/SideScreen >"$LOG" 2>&1 &
adb -s "$SERIAL" install -r android/app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb -s "$SERIAL" shell am force-stop com.classroomadventure.sidescreen
echo "ok (mac log: $LOG, device: $SERIAL)"
