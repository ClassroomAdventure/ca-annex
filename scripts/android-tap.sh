#!/bin/bash
# 画面に出ている文字（または content-desc）で要素を探してタップする。座標を推測しないための補助。
# 使い方: scripts/android-tap.sh <adb serial> "<文字>"        → 見つからなければ exit 1
#         scripts/android-tap.sh <adb serial> "<文字>" --print → タップせず「x y」を表示
S=$1; T=$2
# 無限アニメーション中は dump が失敗することがあるので数回やり直す
for i in 1 2 3; do adb -s "$S" shell uiautomator dump /sdcard/ui.xml 2>/dev/null | grep -q dumped && break; sleep 1; done
XY=$(adb -s "$S" exec-out cat /sdcard/ui.xml | python3 -c '
import re, sys
t = sys.argv[1]; x = sys.stdin.read()
for n in re.findall(r"<node [^>]*>", x):
    txt = re.search(r" text=\"([^\"]*)\"", n).group(1)
    d = re.search(r" content-desc=\"([^\"]*)\"", n)
    if t in txt or (d and t in d.group(1)):
        x1, y1, x2, y2 = map(int, re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n).groups())
        print((x1 + x2) // 2, (y1 + y2) // 2); break
' "$T")
[ -n "$XY" ] || { echo "not found: $T" >&2; exit 1; }
if [ "$3" = "--print" ]; then echo "$XY"; else adb -s "$S" shell input tap $XY; fi
