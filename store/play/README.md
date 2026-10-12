# Google Play に出すもの

Play Console（アプリ: `com.classroomadventure.annex`）に、このフォルダのものを上げる。

| Play Console の項目 | ファイル |
|---|---|
| アプリ本体（AAB） | `scripts/build-release.sh <ver>` → `dist/Annex-android.aab` |
| アプリ名・簡単な説明・詳しい説明 | `listing-en.md` / `listing-ja.md` |
| アプリのアイコン（512×512） | `icon-512.png` |
| フィーチャーグラフィック（1024×500） | `feature-en.png` / `feature-ja.png` |
| スマートフォンのスクリーンショット（1080×1920） | `screens/out/phone-<lang>-1〜6.png` |
| 7 / 10 インチタブレットのスクリーンショット（1920×1080） | `screens/out/tablet-<lang>-1〜6.png` |
| プライバシーポリシー | https://classroomadventure.github.io/ca-annex/privacy/ |
| データセーフティ・アプリのアクセス権（審査用の説明） | `data-safety.md` |

- スクリーンショットは `screens/shot.html` から書き出す（`screens/render.sh`）。素材は `screens/raw/`（端末のステータスバーは切り落とし済み）
- アイコンは `../../mac/Resources/AppIcon.icon` の絵から、フィーチャーグラフィックは `feature.html?lang=..` から作った
- 個人の開発者アカウントなので、本番公開の前にクローズドテスト（テスター 12 人以上 × 14 日間）が必要
- アップロード鍵 = `android/keystore/annex-release.jks`（1Password: `ca-annex-release-keystore`）。初回に Play App Signing を有効にする

## CLI で上げる（store/play/publish.py）

認証は gcloud のなりすまし（鍵ファイルなし）。サービスアカウント
`play-annex@classroom-adventure-admin.iam.gserviceaccount.com` を Play Console の「ユーザーと権限」に招待しておく
（アプリの権限: テストトラックへのリリース、ストアでの表示の管理）。

```sh
python3 store/play/publish.py --listing --dry-run                      # 掲載文・画像を検証だけ
python3 store/play/publish.py --listing                                # 掲載文・画像を確定
python3 store/play/publish.py --bundle dist/Annex-android.aab --track internal   # 内部テストへ（下書き）
python3 store/play/publish.py --bundle dist/Annex-android.aab --track alpha      # クローズドテストへ（下書き）
```

API でできないもの（Play Console の画面で行う）: アプリの作成、コンテンツのレーティング、ターゲット層、
データセーフティ（`data-safety.md` を写す）、アプリのアクセス権（審査用の説明）、広告の有無、テスターのリスト、国・地域。
