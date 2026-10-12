#!/usr/bin/env python3
"""Google Play へ Annex をまとめて上げる（Play Developer API v3）。

認証は gcloud のなりすまし（鍵ファイルは作らない）:
  サービスアカウント play-annex@classroom-adventure-admin.iam.gserviceaccount.com
  ※ Play Console の「ユーザーと権限」でこのアカウントを招待しておくこと

使い方:
  python3 store/play/publish.py --listing                 掲載文と画像だけ
  python3 store/play/publish.py --bundle dist/Annex-android.aab --track internal
  python3 store/play/publish.py --listing --bundle dist/Annex-android.aab --track alpha   （alpha = クローズドテスト）
  --dry-run を付けると、変更を確定（commit）せずに検証だけする
"""
import argparse
import json
import pathlib
import subprocess
import sys
import urllib.error
import urllib.request

PKG = "com.classroomadventure.annex"
SA = "play-annex@classroom-adventure-admin.iam.gserviceaccount.com"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG}"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PKG}"
HERE = pathlib.Path(__file__).resolve().parent
SITE = "https://classroomadventure.github.io/ca-annex/"
EMAIL = "info@classroom-adventure.com"  # ストアに公開される連絡先

# Play の言語コード → このフォルダのファイルの言語
LANGS = {"en-US": "en", "ja-JP": "ja"}


def token() -> str:
    out = subprocess.run(
        ["gcloud", "auth", "print-access-token", f"--impersonate-service-account={SA}",
         "--scopes=https://www.googleapis.com/auth/androidpublisher"],
        capture_output=True, text=True)
    if out.returncode != 0 or not out.stdout.strip():
        sys.exit("認証に失敗しました: " + out.stderr.strip().splitlines()[-1])
    return out.stdout.strip()


TOKEN = None


def call(method: str, url: str, body=None, data: bytes = None, ctype: str = "application/json"):
    headers = {"Authorization": f"Bearer {TOKEN}"}
    if body is not None:
        data = json.dumps(body).encode()
    if data is not None:
        headers["Content-Type"] = ctype
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        msg = e.read().decode(errors="replace")
        try:
            msg = json.loads(msg)["error"]["message"]
        except Exception:
            pass
        sys.exit(f"{method} {url.split(PKG)[-1]} → {e.code}: {msg}")


def listing_text(lang: str) -> dict:
    """listing-<lang>.md の最初の3節（アプリ名 / 簡単な説明 / 詳しい説明）を読む"""
    md = (HERE / f"listing-{lang}.md").read_text()
    sections = [s.split("\n", 1)[1].strip() for s in md.split("\n## ")[1:]]
    title, short, full = sections[0], sections[1], sections[2]
    assert len(title) <= 30 and len(short) <= 80 and len(full) <= 4000, f"{lang}: 文字数オーバー"
    return {"title": title, "shortDescription": short, "fullDescription": full}


def replace_images(edit: str, play_lang: str, kind: str, files):
    call("DELETE", f"{API}/edits/{edit}/listings/{play_lang}/{kind}")
    for f in files:
        call("POST", f"{UPLOAD}/edits/{edit}/listings/{play_lang}/{kind}?uploadType=media",
             data=f.read_bytes(), ctype="image/png")
    print(f"  {play_lang} {kind}: {len(files)} 枚")


def upload_listing(edit: str):
    call("PATCH", f"{API}/edits/{edit}/details", {"contactWebsite": SITE, "contactEmail": EMAIL, "defaultLanguage": "en-US"})
    shots = HERE / "screens" / "out"
    for play_lang, lang in LANGS.items():
        t = listing_text(lang)
        call("PUT", f"{API}/edits/{edit}/listings/{play_lang}", {"language": play_lang, **t})
        print(f"  {play_lang} 掲載文: {t['title']}")
        replace_images(edit, play_lang, "icon", [HERE / "icon-512.png"])
        replace_images(edit, play_lang, "featureGraphic", [HERE / f"feature-{lang}.png"])
        phone = sorted(shots.glob(f"phone-{lang}-*.png"))
        tablet = sorted(shots.glob(f"tablet-{lang}-*.png"))
        replace_images(edit, play_lang, "phoneScreenshots", phone)
        replace_images(edit, play_lang, "sevenInchScreenshots", tablet)
        replace_images(edit, play_lang, "tenInchScreenshots", tablet)


def release_notes(version: str):
    return [
        {"language": "en-US", "text": f"Annex {version}. Use your Android device as an extra display for your Mac."},
        {"language": "ja-JP", "text": f"Annex {version}。Android 端末を Mac のサブディスプレイにします。"},
    ]


def upload_bundle(edit: str, aab: pathlib.Path, track: str, status: str):
    print(f"  AAB をアップロード中: {aab} ({aab.stat().st_size // 1024} KB)")
    b = call("POST", f"{UPLOAD}/edits/{edit}/bundles?uploadType=media", data=aab.read_bytes(),
             ctype="application/octet-stream")
    vc = b["versionCode"]
    version = subprocess.run(["grep", "-o", 'versionName = "[^"]*"', str(HERE.parent.parent / "android/app/build.gradle.kts")],
                             capture_output=True, text=True).stdout.split('"')[1]
    call("PUT", f"{API}/edits/{edit}/tracks/{track}", {
        "track": track,
        "releases": [{"name": version, "versionCodes": [str(vc)], "status": status, "releaseNotes": release_notes(version)}],
    })
    print(f"  トラック {track} に versionCode {vc}（{version}）を {status} で登録")


def main():
    global TOKEN
    ap = argparse.ArgumentParser()
    ap.add_argument("--listing", action="store_true", help="掲載文と画像を上げる")
    ap.add_argument("--bundle", type=pathlib.Path, help="上げる AAB")
    ap.add_argument("--track", default="internal", help="internal / alpha（クローズド）/ beta / production")
    ap.add_argument("--status", default="draft",
                    help="draft（まだ一度も公開していないアプリはこれしか使えない）/ completed")
    ap.add_argument("--dry-run", action="store_true", help="commit せずに検証だけ")
    a = ap.parse_args()
    if not a.listing and not a.bundle:
        ap.error("--listing か --bundle のどちらかを指定してください")

    TOKEN = token()
    edit = call("POST", f"{API}/edits", {})["id"]
    print(f"編集を開始: {edit}")
    if a.listing:
        upload_listing(edit)
    if a.bundle:
        upload_bundle(edit, a.bundle, a.track, a.status)
    if a.dry_run:
        call("POST", f"{API}/edits/{edit}:validate")
        call("DELETE", f"{API}/edits/{edit}")
        print("検証 OK（--dry-run なので確定していません）")
    else:
        call("POST", f"{API}/edits/{edit}:commit")
        print("確定しました")


if __name__ == "__main__":
    main()
