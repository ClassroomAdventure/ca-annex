#!/bin/bash
# docs/src/og.html から OG 画像（docs/og-en.png, docs/og-ja.png）を書き出す。Google Chrome が必要
set -euo pipefail
cd "$(dirname "$0")/.."
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
TMP=$(mktemp -d)
for lang in en ja; do
  # ヘッドレス Chrome は撮り終わっても終了しないことがあるので timeout で止める
  timeout 30 "$CHROME" --headless=new --user-data-dir="$TMP/$lang" --no-first-run --hide-scrollbars \
    --force-device-scale-factor=1 --window-size=1200,630 --virtual-time-budget=3000 \
    --screenshot="$PWD/docs/og-$lang.png" "file://$PWD/docs/src/og.html?lang=$lang" >/dev/null 2>&1 || true
done
rm -rf "$TMP"
ls -la docs/og-*.png
