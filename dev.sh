#!/bin/bash
# Mac アプリと Android アプリをビルドして入れ直す（開発用）
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
SERIAL=${SERIAL:-$(adb devices | awk '$2=="device" && $1 !~ /[:.]/ {print $1; exit}')}
(cd android && ./gradlew assembleDebug -q)
mac/build-app.sh >/dev/null
pkill -x FoldDisplay || true
sleep 0.5
LOG=${LOG:-/tmp/folddisplay.log}
nohup mac/build/FoldDisplay.app/Contents/MacOS/FoldDisplay >"$LOG" 2>&1 &
adb -s "$SERIAL" install -r android/app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb -s "$SERIAL" shell am force-stop com.classroomadventure.folddisplay
echo "ok (mac log: $LOG, device: $SERIAL)"
