#!/bin/bash
# FoldDisplay.app をビルドして署名する
set -euo pipefail
cd "$(dirname "$0")"
swift build -c release
APP=build/FoldDisplay.app
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS"
cp .build/release/FoldDisplay "$APP/Contents/MacOS/FoldDisplay"
cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleIdentifier</key><string>com.classroomadventure.folddisplay</string>
  <key>CFBundleName</key><string>FoldDisplay</string>
  <key>CFBundleExecutable</key><string>FoldDisplay</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>1.0</string>
  <key>CFBundleVersion</key><string>1</string>
  <key>LSMinimumSystemVersion</key><string>14.0</string>
  <key>LSUIElement</key><true/>
  <key>NSLocalNetworkUsageDescription</key><string>Wi-Fi 経由で Android 端末に画面を送るために使います。</string>
  <key>NSBonjourServices</key><array><string>_folddisplay._tcp</string></array>
</dict></plist>
PLIST
# 署名が毎回同じ証明書だと、画面収録/アクセシビリティの許可が再ビルド後も維持される
IDENTITY=$(security find-identity -p codesigning -v | awk -F'"' '/Apple Development|Developer ID/ {print $2; exit}')
codesign --force --sign "${IDENTITY:--}" "$APP"
echo "built: $(pwd)/$APP (signed: ${IDENTITY:-ad-hoc})"
