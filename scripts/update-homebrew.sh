#!/bin/bash
# GitHub に出したリリースに合わせて Homebrew tap（ClassroomAdventure/homebrew-tap）の cask を更新する
# 使い方: scripts/update-homebrew.sh 1.2.3   （gh release create のあとに実行）
set -euo pipefail
VER=${1:?version}
TAP=${TAP_DIR:-$HOME/Developer/homebrew-tap}
[ -d "$TAP/.git" ] || gh repo clone ClassroomAdventure/homebrew-tap "$TAP"
git -C "$TAP" pull -q
URL="https://github.com/ClassroomAdventure/ca-annex/releases/download/v$VER/Annex-mac.zip"
SHA=$(curl -fsSL "$URL" | shasum -a 256 | cut -d' ' -f1)
CASK="$TAP/Casks/annex.rb"
sed -i '' -E "s/^  version \"[^\"]+\"/  version \"$VER\"/; s/^  sha256 \"[0-9a-f]+\"/  sha256 \"$SHA\"/" "$CASK"
grep -E '^  (version|sha256) ' "$CASK"
if git -C "$TAP" diff --quiet; then echo "tap はすでに $VER です"; exit 0; fi
git -C "$TAP" commit -qam "annex $VER" && git -C "$TAP" push -q && echo "tap を $VER に更新しました"
