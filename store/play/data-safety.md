# Google Play「データセーフティ」の回答メモ / Data safety answers

- データの収集: しない / Does your app collect or share user data? → **No**
  - 映像・操作は Mac と端末の間（USB / LAN）で直接やり取りし、開発者やサードパーティには送らない
  - ランダムな端末 ID は端末内と、許可した Mac の中だけに保存（Play の定義では「収集」に当たらない: 端末外の開発者側に送らないため）
- 暗号化: LAN 内の直接通信のみ。インターネット経由の送信は無い
- データ削除の依頼: 対象データが無い（端末の設定から ID のリセット可）
- 広告 ID: 使わない
- 対象年齢: 13歳以上を推奨（一般向けツール）

# 審査担当者向けのメモ / Notes for the reviewer (App access)
The app connects to the free Annex app for Mac. Without a Mac, tap “Try it without a Mac” on the main screen to open demo mode, which shows a simulated Mac display you can control by touch (drag windows by their title bar, tap tiles, scroll).
