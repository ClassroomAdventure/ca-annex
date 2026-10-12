#!/bin/bash
# Annex.app をビルドして署名する
set -euo pipefail
cd "$(dirname "$0")"
# Apple シリコンと Intel の両方で動くユニバーサルバイナリ
ARCHS="--arch arm64 --arch x86_64"
swift build -c release $ARCHS 2>&1 | grep -v "x86_64 architecture is deprecated" || true
BIN="$(swift build -c release $ARCHS --show-bin-path)/Annex"
APP=build/Annex.app
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN" "$APP/Contents/MacOS/Annex"
cp -R Resources/en.lproj Resources/ja.lproj "$APP/Contents/Resources/"
# Liquid Glass アイコン（.icon）→ Assets.car、macOS 14/15 用に AppIcon.icns も出る
xcrun actool "$PWD/Resources/AppIcon.icon" --compile "$PWD/$APP/Contents/Resources" --platform macosx \
  --minimum-deployment-target 14.0 --app-icon AppIcon --include-all-app-icons \
  --output-partial-info-plist "$PWD/build/icon-partial.plist" >/dev/null
cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleDevelopmentRegion</key><string>en</string>
  <key>CFBundleLocalizations</key><array><string>en</string><string>ja</string></array>
  <key>CFBundleIdentifier</key><string>com.classroomadventure.annex</string>
  <key>CFBundleName</key><string>Annex</string>
  <key>CFBundleExecutable</key><string>Annex</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleIconFile</key><string>AppIcon</string>
  <key>CFBundleIconName</key><string>AppIcon</string>
  <key>CFBundleShortVersionString</key><string>1.2.0</string>
  <key>CFBundleVersion</key><string>4</string>
  <key>LSMinimumSystemVersion</key><string>14.0</string>
  <key>LSUIElement</key><true/>
  <key>NSLocalNetworkUsageDescription</key><string>Wi-Fi 経由で Android 端末に画面を送るために使います。</string>
  <key>NSBonjourServices</key><array><string>_annex._tcp</string></array>
</dict></plist>
PLIST
# 署名が毎回同じ証明書だと、画面収録/アクセシビリティの許可が再ビルド後も維持される
# SIGN_IDENTITY=- で ad-hoc（配布用）。未指定なら手元の Developer ID / Apple Development 証明書
IDENTITY=${SIGN_IDENTITY:-$(security find-identity -p codesigning -v | awk -F'"' '/Developer ID Application/ {print $2; exit}')}
IDENTITY=${IDENTITY:-$(security find-identity -p codesigning -v | awk -F'"' '/Apple Development/ {print $2; exit}')}
codesign --force --options runtime --sign "${IDENTITY:--}" "$APP"
echo "built: $(pwd)/$APP (signed: ${IDENTITY:-ad-hoc})"
