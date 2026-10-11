# FoldDisplay

Galaxy（Android）を Mac の拡張ディスプレイにする、Sidecar 風のアプリ。USB と Wi-Fi に対応しています。

```
Mac: 仮想ディスプレイ(CGVirtualDisplay) → ScreenCaptureKit → VideoToolbox H.264 ─┐
                                                                                 │ TCP :47800
Android: MediaCodec でデコードして全画面表示 ← ────────────────────────────────────┘
         タッチ → マウス/スクロールイベント → Mac (CGEvent)
```

- **USB**: Mac アプリが `adb reverse tcp:47800 tcp:47800` を自動で張る（Android 側で USB デバッグを ON にしておく）
- **Wi-Fi**: Mac が Bonjour（`_folddisplay._tcp`）で自分を広告し、Android 側で自動的に見つかる
- 折りたたみの開閉・回転で端末の解像度が変わると、Mac 側の仮想ディスプレイの解像度も合わせ直す
- 仮想ディスプレイは接続中だけ存在する（切断から4秒後に消える）

## Mac

```sh
mac/build-app.sh                 # mac/build/FoldDisplay.app ができる
open mac/build/FoldDisplay.app   # メニューバーに常駐
```

初回は「画面収録」と「アクセシビリティ」（タッチ操作用）の許可が必要です。
Apple Development 証明書で署名しているので、再ビルドしても許可は維持されます。
画面の配置は「システム設定 → ディスプレイ」で調整します（"Galaxy Display"）。

## Android

```sh
cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew installDebug
```

## 操作

| 操作 | Mac での動作 |
|---|---|
| タップ / 2回タップ | クリック / ダブルクリック |
| 1本指ドラッグ | 左ドラッグ |
| 長押し / 2本指タップ | 右クリック |
| 2本指スワイプ | スクロール（慣性あり） |
| S Pen | ホバーでカーソル移動、タッチで左ボタン、ペンのボタンで右クリック |

## プロトコル

全メッセージ `[type:u8][length:u32 BE][payload]`。定義は `mac/Sources/FoldDisplay/Protocol.swift` と
`android/app/src/main/java/com/classroomadventure/folddisplay/Protocol.kt`。
