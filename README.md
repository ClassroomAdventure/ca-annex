# SideScreen

Galaxy（Android）を Mac の拡張ディスプレイにする、Sidecar 風のアプリ。USB と Wi-Fi に対応しています。

```
Mac: 仮想ディスプレイ(CGVirtualDisplay) → ScreenCaptureKit → VideoToolbox H.264 ─┐
                                                                                 │ TCP :47800
Android: MediaCodec でデコードして全画面表示 ← ────────────────────────────────────┘
         タッチ → マウス/スクロールイベント → Mac (CGEvent)
```

- **USB**: TCP。Mac アプリが `adb reverse tcp:47800 tcp:47800` を自動で張る（Android 側で USB デバッグを ON にしておく）
- **Wi-Fi**: Mac が Bonjour（`_sidescreen._tcp`）で自分を広告し、Android 側で自動的に見つかる。
  映像は UDP（1200B シャード＋8個ごとに XOR パリティ、DSCP CS5）、操作・カーソル・制御は TCP。
  Android が 250ms ごとに受信数・欠落・通信遅延を返し、Mac がビットレートを 3〜30Mbps で自動調整する
- コーデックは HEVC（端末が対応していれば。設定で H.264 に戻せる）。定期キーフレームは出さず、静止時は差分フレームで精細化
- カーソルは映像に焼き込まず、Android 側で描く（タッチ位置へ即座に動かす）
- 折りたたみの開閉・回転で端末の解像度が変わると、Mac 側の仮想ディスプレイの解像度も合わせ直す
- 仮想ディスプレイは接続中だけ存在する（切断から4秒後に消える）

## Mac

```sh
mac/build-app.sh                 # mac/build/SideScreen.app ができる
open mac/build/SideScreen.app   # メニューバーに常駐
```

初回は「画面収録」と「アクセシビリティ」（タッチ操作用）の許可が必要です。
Apple Development 証明書で署名しているので、再ビルドしても許可は維持されます。
画面の配置は「システム設定 → ディスプレイ」で調整します（"Galaxy Display"）。

## Android

```sh
cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew installDebug
```

## 遅延の計測

表示中に **3本指タップ** で統計を表示（logcat の `FDStats` タグにも毎秒出る）。
「Mac内」＝キャプチャ→エンコード完了、「通信」＝エンコード完了→受信、「デコード」＝受信→デコード完了。

実測（Galaxy Z Fold 8 / M4 Pro、画面が動いている状態）:

| | Mac内 | 通信 | デコード | 合計 |
|---|---|---|---|---|
| USB | 約7ms | 約10ms | 約3ms | 約20ms |
| Wi-Fi（UDP） | 約7ms | 約5ms | 約3ms | 約16ms |

USB の「通信」は adb reverse の転送で溜まっている分（ケーブル・libusb では変わらない）。

開発用: `./dev.sh` で両方ビルドして入れ直す。`SIDESCREEN_DROP=0.03 ./dev.sh` で Wi-Fi の擬似パケットロス試験。

## 操作

| 操作 | Mac での動作 |
|---|---|
| タップ / 2回タップ | クリック / ダブルクリック |
| 1本指ドラッグ | 左ドラッグ |
| 長押し / 2本指タップ | 右クリック |
| 2本指スワイプ | スクロール（慣性あり） |
| S Pen | ホバーでカーソル移動、タッチで左ボタン、ペンのボタンで右クリック |

## プロトコル

全メッセージ `[type:u8][length:u32 BE][payload]`。定義は `mac/Sources/SideScreen/Protocol.swift` と
`android/app/src/main/java/com/classroomadventure/sidescreen/Protocol.kt`。
