#!/usr/bin/env python3
"""docs/src/page.html から英語版 docs/index.html と日本語版 docs/ja/index.html を作る。

テンプレートの書き方:
  - 1行内の言語別テキスト: <span class="en">…</span><span class="ja">…</span>
  - ブロック単位:          <!--en--> … <!--/en-->  /  <!--ja--> … <!--/ja-->
生成したページには、その言語の本文だけが入る（検索エンジンが言語ごとに正しく扱えるように）。

使い方: python3 scripts/build-pages.py
"""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
SITE = "https://classroomadventure.github.io/ca-sidescreen/"
REPO = "https://github.com/ClassroomAdventure/ca-sidescreen"
VERSION = re.search(r'versionName = "([^"]+)"', (ROOT / "android/app/build.gradle.kts").read_text()).group(1)

PAGES = {
    "en": {
        "path": "",
        "root": "",
        "title": "SideScreen – Use your Android as a second monitor for Mac (free Sidecar alternative)",
        "description": "Free, open-source app that turns an Android phone, tablet or foldable into an extended display "
                       "for your Mac. Works over USB or Wi-Fi with about 16 ms latency, plus touch and stylus input. "
                       "Like Sidecar, for Android.",
        "og_locale": "en_US",
        "og_image": "og-en.png",
        "keywords": "android second monitor mac, sidecar for android, android tablet as mac display, "
                    "extended display, virtual display, galaxy tab mac monitor",
    },
    "ja": {
        "path": "ja/",
        "root": "../",
        "title": "SideScreen｜Android を Mac のサブディスプレイに（無料・Sidecar の Android 版）",
        "description": "Android のスマホ・タブレット・折りたたみ（Galaxy・Pixel・Xperia など）を Mac の"
                       "サブディスプレイ（拡張ディスプレイ・サブモニター）にする無料のオープンソースアプリ。"
                       "USB でも Wi-Fi でも、遅延約16ms、タッチ・ペン操作に対応。Sidecar の Android 版のように使えます。",
        "og_locale": "ja_JP",
        "og_image": "og-ja.png",
        "keywords": "Android Mac サブディスプレイ, Android Mac 拡張ディスプレイ, Sidecar Android, "
                    "Android タブレット Mac サブモニター, Galaxy Mac モニター, デュアルディスプレイ",
    },
}


def strip_other(html: str, keep: str) -> str:
    drop = "ja" if keep == "en" else "en"
    html = re.sub(rf"<!--{drop}-->.*?<!--/{drop}-->\n?", "", html, flags=re.S)
    html = re.sub(rf"<!--/?{keep}-->\n?", "", html)
    # 言語別 span（中に span は入れない約束）
    html = re.sub(rf'<span class="{drop}">.*?</span>', "", html, flags=re.S)
    html = re.sub(rf'<span class="{keep}">(.*?)</span>', r"\1", html, flags=re.S)
    return html


def faq_jsonld(html: str) -> dict:
    items = re.findall(r"<summary>(.*?)</summary>\s*<p>(.*?)</p>", html, flags=re.S)
    return {
        "@context": "https://schema.org",
        "@type": "FAQPage",
        "mainEntity": [
            {"@type": "Question", "name": q.strip(),
             "acceptedAnswer": {"@type": "Answer", "text": re.sub(r"<[^>]+>", "", a).strip()}}
            for q, a in items
        ],
    }


def head(lang: str, page: dict) -> str:
    url = SITE + page["path"]
    app = {
        "@context": "https://schema.org",
        "@type": "SoftwareApplication",
        "name": "SideScreen",
        "url": url,
        "description": page["description"],
        "applicationCategory": "UtilitiesApplication",
        "operatingSystem": "macOS 14+, Android 8.0+",
        "softwareVersion": VERSION,
        "license": "https://opensource.org/licenses/MIT",
        "downloadUrl": REPO + "/releases/latest",
        "image": SITE + "icon.png",
        "inLanguage": ["en", "ja"],
        "offers": {"@type": "Offer", "price": "0", "priceCurrency": "USD"},
        "author": {"@type": "Organization", "name": "Classroom Adventure"},
    }
    alt = "\n".join(
        f'<link rel="alternate" hreflang="{l}" href="{SITE + p["path"]}">' for l, p in PAGES.items()
    )
    e = lambda s: s.replace("&", "&amp;").replace('"', "&quot;")
    return f"""<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{page["title"]}</title>
<meta name="description" content="{e(page["description"])}">
<meta name="keywords" content="{e(page["keywords"])}">
<link rel="canonical" href="{url}">
{alt}
<link rel="alternate" hreflang="x-default" href="{SITE}">
<link rel="icon" type="image/png" href="{page["root"]}favicon.png">
<link rel="apple-touch-icon" href="{page["root"]}icon.png">
<meta name="theme-color" content="#3A6EFA">
<meta property="og:type" content="website">
<meta property="og:site_name" content="SideScreen">
<meta property="og:title" content="{e(page["title"])}">
<meta property="og:description" content="{e(page["description"])}">
<meta property="og:url" content="{url}">
<meta property="og:image" content="{SITE + page["og_image"]}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:locale" content="{page["og_locale"]}">
<meta property="og:locale:alternate" content="{PAGES["ja" if lang == "en" else "en"]["og_locale"]}">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="{e(page["title"])}">
<meta name="twitter:description" content="{e(page["description"])}">
<meta name="twitter:image" content="{SITE + page["og_image"]}">
<script type="application/ld+json">{json.dumps(app, ensure_ascii=False)}</script>
<script type="application/ld+json">@@FAQ_JSONLD@@</script>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans+JP:wght@400;500;600&family=IBM+Plex+Sans:wght@400;500;600&display=swap" rel="stylesheet">"""


def lang_switch(lang: str) -> str:
    en = PAGES["en"]["root"] if lang == "en" else "../"
    ja = "ja/" if lang == "en" else "./"
    cur = lambda l: ' aria-current="page"' if l == lang else ""
    return (f'<div class="lang" role="group" aria-label="Language">'
            f'<a href="{en}" hreflang="en" lang="en" data-lang-link="en"{cur("en")}>EN</a>'
            f'<a href="{ja}" hreflang="ja" lang="ja" data-lang-link="ja"{cur("ja")}>日本語</a></div>')


def script(lang: str) -> str:
    # 英語ページを日本語の端末で開いた人にだけ、日本語版への案内を出す（自動リダイレクトはしない）
    if lang != "en":
        return '<script>document.querySelectorAll("[data-lang-link]").forEach(function(a){a.addEventListener("click",function(){try{localStorage.setItem("sidescreen-lang",a.getAttribute("data-lang-link"))}catch(e){}})});</script>'
    return """<script>
  (function () {
    var links = document.querySelectorAll("[data-lang-link]");
    links.forEach(function (a) {
      a.addEventListener("click", function () { try { localStorage.setItem("sidescreen-lang", a.getAttribute("data-lang-link")); } catch (e) {} });
    });
    var chosen = null;
    try { chosen = localStorage.getItem("sidescreen-lang"); } catch (e) {}
    if (!chosen && (navigator.language || "").toLowerCase().indexOf("ja") === 0) {
      var bar = document.getElementById("suggest");
      if (bar) bar.hidden = false;
    }
  })();
</script>"""


def build(lang: str) -> str:
    page = PAGES[lang]
    html = (DOCS / "src/page.html").read_text()
    html = html.replace("{{HEAD}}", head(lang, page))
    html = html.replace("{{LANG_SWITCH}}", lang_switch(lang))
    html = html.replace("{{SCRIPT}}", script(lang))
    html = html.replace("{{LANG}}", lang).replace("{{ROOT}}", page["root"])
    if lang == "en":
        html = html.replace("<header>", '<div class="suggest" id="suggest" hidden lang="ja">'
                                         '<a href="ja/">日本語のページはこちら</a></div>\n<header>', 1)
    html = strip_other(html, lang)
    html = html.replace("@@FAQ_JSONLD@@", json.dumps(faq_jsonld(html), ensure_ascii=False))
    return html


def sitemap() -> str:
    urls = []
    for lang, p in PAGES.items():
        alts = "".join(f'\n    <xhtml:link rel="alternate" hreflang="{l}" href="{SITE + q["path"]}"/>'
                       for l, q in PAGES.items())
        urls.append(f"  <url>\n    <loc>{SITE + p['path']}</loc>{alts}\n  </url>")
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n'
            + "\n".join(urls) + "\n</urlset>\n")


if __name__ == "__main__":
    (DOCS / "index.html").write_text(build("en"))
    (DOCS / "ja").mkdir(exist_ok=True)
    (DOCS / "ja/index.html").write_text(build("ja"))
    (DOCS / "sitemap.xml").write_text(sitemap())
    print("wrote docs/index.html, docs/ja/index.html, docs/sitemap.xml")
