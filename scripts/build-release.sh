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

# Mac: Developer ID で署名 → Apple の公証 → 結果をアプリに添付（staple）→ Gatekeeper で検査
#   公証キー: 1Password の Document "ca-annex-notary-key" と env-ca-annex の NOTARY_KEY_ID / NOTARY_ISSUER_ID
#   ANNEX_SKIP_NOTARIZE=1 なら ad-hoc 署名のまま（試験用。配布しないこと）
if [ "${ANNEX_SKIP_NOTARIZE:-}" = 1 ]; then
  SIGN_IDENTITY=- mac/build-app.sh
else
  DEVID=$(security find-identity -p codesigning -v | awk -F'"' '/Developer ID Application/ {print $2; exit}')
  [ -n "$DEVID" ] || { echo "Developer ID Application 証明書がキーチェーンにありません"; exit 1; }
  KEY_ID=$(op read op://Dev/env-ca-annex/NOTARY_KEY_ID)
  ISSUER=$(op read op://Dev/env-ca-annex/NOTARY_ISSUER_ID 2>/dev/null || true)
  KEYFILE=$(mktemp -t annex-notary); trap 'rm -f "$KEYFILE"' EXIT
  op document get ca-annex-notary-key --vault Dev --out-file "$KEYFILE" --force >/dev/null
  SIGN_IDENTITY="$DEVID" mac/build-app.sh
  (cd mac/build && rm -f notarize.zip && ditto -c -k --keepParent Annex.app notarize.zip)
  echo "公証に提出中（数分かかります）…"
  OUT=$(xcrun notarytool submit mac/build/notarize.zip --key "$KEYFILE" --key-id "$KEY_ID" ${ISSUER:+--issuer "$ISSUER"} --wait --timeout 30m 2>&1) || true
  echo "$OUT" | grep -E "id:|status:" | tail -2
  # grep -q をパイプの途中に置くと、早く終わった grep のせいで pipefail が失敗扱いになるので、一度変数に入れて判定する
  case "$OUT" in *"status: Accepted"*) ;; *) echo "公証が通りませんでした（xcrun notarytool log <id> で理由を確認）"; exit 1;; esac
  xcrun stapler staple mac/build/Annex.app
  spctl --assess --type execute -vv mac/build/Annex.app
  rm -f mac/build/notarize.zip
fi
(cd mac/build && ditto -c -k --keepParent Annex.app ../../dist/Annex-mac.zip)
ls -la dist
