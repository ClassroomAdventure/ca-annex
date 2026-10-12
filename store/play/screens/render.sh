#!/bin/bash
# Play 用スクリーンショットを書き出す: store/play/screens/out/<form>-<lang>-<n>.png
# 使い方: store/play/screens/render.sh [form] [lang]   （省略時は全部）
set -euo pipefail
cd "$(dirname "$0")"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
mkdir -p out
TMP=$(mktemp -d)
for form in ${1:-phone tablet}; do
  [ $form = phone ] && SIZE=1080,1920 || SIZE=1920,1080
  for lang in ${2:-en ja}; do
    for n in 1 2 3 4 5 6; do
      timeout 40 "$CHROME" --headless=new --user-data-dir="$TMP/$form$lang$n" --no-first-run --hide-scrollbars \
        --allow-file-access-from-files --force-device-scale-factor=1 --window-size=$SIZE --virtual-time-budget=4000 \
        --screenshot="$PWD/out/$form-$lang-$n.png" "file://$PWD/shot.html?n=$n&lang=$lang&form=$form" >/dev/null 2>&1 || true &
    done
    wait
  done
done
rm -rf "$TMP"
ls out
