<p align="center"><img src="docs/icon.png" width="128" alt="SideScreen のアイコン"></p>

<h1 align="center">SideScreen</h1>

<p align="center">Android のスマホやタブレットを、Mac の拡張ディスプレイにします。USB でも Wi-Fi でも。</p>

<p align="center"><a href="https://classroomadventure.github.io/ca-sidescreen/">Web サイト</a> · <a href="https://github.com/ClassroomAdventure/ca-sidescreen/releases/latest">ダウンロード</a> · <a href="README.md">English</a></p>

---

Sidecar の Android 版のように使えます。Mac からは本物の2枚目のディスプレイとして見えるので、ウィンドウを移したり、ディスプレイ設定で配置を変えたりできます。タッチやペンでクリック・ドラッグ・スクロールもできます。

- **USB でも Wi-Fi でも。** ケーブルを挿せば自動で始まり、同じ Wi-Fi なら一覧から Mac を選ぶだけ。
- **遅延が小さい。** 実測で、Wi-Fi 経由でキャプチャから表示まで約 16ms。カーソルは端末側で描くので、指に遅れず付いてきます。
- **くっきり。** 端末の解像度そのままの Retina 表示。文字を大きくする設定もあります。
- **折りたたみ対応。** 開く・閉じる・回すと、Mac 側の解像度も自動で合わせ直します。
- **Android 8.0 以降ならどの端末でも。** スマホ・タブレット・折りたたみに対応。端末が対応していれば HEVC を使います。

## 必要なもの

| | |
|---|---|
| Mac | macOS 14 Sonoma 以降（Apple シリコン / Intel） |
| Android | Android 8.0 以降 |
| USB で使う場合 | [Android platform tools](https://developer.android.com/tools/releases/platform-tools)（`brew install android-platform-tools`）と、端末の USB デバッグ |
| Wi-Fi で使う場合 | Mac と Android が同じネットワーク |

## インストール

### Mac

1. [Releases](https://github.com/ClassroomAdventure/ca-sidescreen/releases/latest) から `SideScreen-mac.zip` をダウンロードして展開し、`SideScreen.app` をアプリケーションに移します。
2. まだ Apple の公証を受けていないので、初回は起動がブロックされます。一度開いてから **システム設定 → プライバシーとセキュリティ** で **このまま開く** を押してください。（`xattr -dr com.apple.quarantine /Applications/SideScreen.app` でも可）
3. メニューバーに常駐します。聞かれたら **画面収録**（画面を送るため）と **アクセシビリティ**（タッチ操作のため）を許可してください。

### Android

1. 端末で [Releases](https://github.com/ClassroomAdventure/ca-sidescreen/releases/latest) から `SideScreen-android.apk` をダウンロードしてインストールします。
2. USB で使う場合は **開発者向けオプション → USB デバッグ** を ON にし、ケーブルをつないで Mac を許可します。
3. SideScreen を開きます。USB なら自動でつながり、Wi-Fi なら一覧の Mac をタップします。

## 操作

| 操作 | Mac での動作 |
|---|---|
| タップ / 2回タップ | クリック / ダブルクリック |
| 1本指ドラッグ | ドラッグ |
| 長押し / 2本指タップ | 右クリック |
| 2本指スワイプ | スクロール（慣性あり） |
| ペン | ホバーでカーソル移動、タッチでクリック、サイドボタンで右クリック |
| 3本指タップ | 遅延の内訳を表示 |

画面の配置は **システム設定 → ディスプレイ** で変えられます。端末の名前で表示されます。

## 仕組み

```
Mac   仮想ディスプレイ(CGVirtualDisplay) → ScreenCaptureKit → VideoToolbox HEVC/H.264 ──┐
                                                                                        │ USB: adb reverse 経由の TCP
                                                                                        │ Wi-Fi: 映像は UDP、操作・制御は TCP
Android   MediaCodec でハードウェアデコード → 全画面 SurfaceView ◀─────────────────────────┘
          タッチ / ペン → マウス・スクロールイベント → Mac (CGEvent)
```

- 仮想ディスプレイは接続中だけ存在します（Sidecar と同じ）。
- Wi-Fi では映像を 1200 バイトずつに分け、8個ごとに XOR パリティを付けて送るので、パケットが1つ落ちても再送なしで直せます。端末が 250ms ごとに欠落と通信遅延を返し、Mac がビットレートを 3〜30Mbps で調整します。
- 定期的なキーフレームは出しません。画面が止まったら同じ画を差分フレームで送り直し、通信量を跳ねさせずに文字をくっきりさせます。
- カーソルは映像に焼き込まず、Mac から位置と形を送って端末側で描きます。触った位置へすぐ動きます。

Galaxy Z Fold と M4 Pro の MacBook Pro で、画面が動いているときの実測:

| | Mac（キャプチャ＋エンコード） | 通信 | デコード | 合計 |
|---|---|---|---|---|
| USB | 約 7ms | 約 10ms | 約 3ms | 約 20ms |
| Wi-Fi | 約 7ms | 約 5ms | 約 3ms | 約 16ms |

USB は `adb reverse` を通るため、連続して送ると adb で溜まります。今のところ Wi-Fi のほうが速い経路です。

> **注意:** 仮想ディスプレイの作成には macOS の非公開 API `CGVirtualDisplay` を使っています（BetterDisplay、DeskPad、Chromium のテストも同じ）。将来の macOS アップデートで動かなくなる可能性があります。

## ソースからビルド

```sh
# Mac アプリ → mac/build/SideScreen.app
mac/build-app.sh

# Android アプリ
cd android && ./gradlew installDebug   # JDK 17 と Android SDK（platform 36）が必要
```

`./dev.sh` で両方をビルドし、Mac アプリを再起動して、つないだ端末に入れます。`SIDESCREEN_DROP=0.03 ./dev.sh` で Wi-Fi のパケットを 3% 捨てて欠落からの回復を試せます。

リリース版: `scripts/build-release.sh <バージョン>` で `dist/SideScreen-mac.zip` と `dist/SideScreen-android.apk` ができます。Android のリリース鍵はリポジトリに入っていません（`.env.tpl` 参照）。無い場合は debug 鍵で署名します。

Web サイトは `docs/` にあります。`docs/src/page.html`（英日を1ファイルに書く）を編集して `python3 scripts/build-pages.py` を実行すると、`docs/index.html` と `docs/ja/index.html` が作り直されます。SNS 用の画像は `scripts/render-og.sh` で書き出します。

### プロトコル

TCP のメッセージはすべて `[type: u8][length: u32 BE][payload]`。定義は `mac/Sources/SideScreen/Protocol.swift` と `android/app/src/main/java/com/classroomadventure/sidescreen/Protocol.kt` にあります。

## ライセンス

[MIT](LICENSE) © Classroom Adventure
