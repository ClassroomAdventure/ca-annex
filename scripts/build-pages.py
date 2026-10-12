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
        "alts": {
            "HERO_LABEL": "A Mac and an Android tablet side by side. A window is dragged from the Mac's screen onto the tablet, which works as the Mac's second screen, then scrolled with two fingers on the tablet.",
            "DEMO_LABEL": "A Galaxy Z Fold8 showing a Mac's extra display, with windows being scrolled and dragged by touch",
            "ALT_HOME": "SideScreen on Android: a Mac found over Wi-Fi with a Connect button, next to a setup guide",
            "ALT_SETUP": "SideScreen's setup guide on the Mac, showing a QR code to download the Android app",
            "ALT_PERMISSION": "The setup guide's Screen Recording step, with a button to allow it",
            "ALT_APPROVAL": "A dialog on the Mac asking whether to allow a Galaxy Z Fold8 to use the Mac as a display",
            "ALT_SETTINGS": "SideScreen settings on Android: display size, frame rate and HEVC",
        },
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
        "alts": {
            "HERO_LABEL": "Mac と Android タブレットを並べた図。Mac の画面のウィンドウを、2枚目の画面になったタブレットへドラッグで移し、タブレット上で2本指でスクロールしている。",
            "DEMO_LABEL": "Galaxy Z Fold8 に Mac の拡張ディスプレイを映し、タッチでスクロールやウィンドウのドラッグをしている様子",
            "ALT_HOME": "Android の SideScreen。Wi-Fi で見つかった Mac と接続ボタン、その横に準備の案内",
            "ALT_SETUP": "Mac の SideScreen のセットアップガイド。Android アプリをダウンロードする QR コード",
            "ALT_PERMISSION": "セットアップガイドの画面収録のステップと、許可するボタン",
            "ALT_APPROVAL": "Galaxy Z Fold8 にこの Mac のディスプレイを使わせるかを確認する Mac のダイアログ",
            "ALT_SETTINGS": "Android の SideScreen の設定。画面の大きさ、フレームレート、HEVC",
        },
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


DEMO_SCRIPT = """<script>
  (function () {
    // --- 実機の録画（ギャラリー）: ボタンを押したら再生 ---
    var v = document.getElementById("demo"), b = document.getElementById("play");
    if (v && b) b.addEventListener("click", function () { v.play(); v.controls = true; b.hidden = true; });

    // --- 最初の画面: Mac と Android が1つのデスクトップとしてつながる様子 ---
    var wrap = document.getElementById("rigWrap"), rig = document.getElementById("rig");
    if (!wrap || !rig) return;
    function fit() {
      var k = wrap.clientWidth / 1080;
      rig.style.transform = "scale(" + k + ")";
      rig.classList.toggle("small", k < 0.6);
    }
    fit();
    new ResizeObserver(fit).observe(wrap);

    // 画面ごとの左上（1080×600 の座標系）。ウィンドウやカーソルは全体の座標で動かし、各画面で切り抜いて見せる
    var screens = [].slice.call(rig.querySelectorAll(".scr")).map(function (el) {
      var mac = el.dataset.screen === "mac";
      return { el: el, x: mac ? 54 : 772, y: mac ? 44 : 132,
        win: el.querySelector(".win"), tiles: el.querySelector(".tiles-in"), ptr: el.querySelector(".ptr"),
        f1: el.querySelector(".f1"), f2: el.querySelector(".f2"), cells: el.querySelectorAll(".tiles-in i") };
    });
    var steps = [].slice.call(document.querySelectorAll("#liveSteps li"));
    var ease = function (t) { return t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; };
    var seg = function (t, a, b) { return Math.min(1, Math.max(0, (t - a) / (b - a))); };
    var lerp = function (a, b, k) { return a + (b - a) * k; };
    var LOOP = 10;

    function frame(t, loop) {
      rig.dataset.mode = loop % 2 ? "wifi" : "usb";
      // ウィンドウ: Mac の上 → 境目をまたいで Android へ
      var d = ease(seg(t, 1.3, 3.7));
      var wx = lerp(300, 795, d), wy = lerp(120, 150, d);
      var fade = t > 9.2 ? 1 - seg(t, 9.2, 9.7) : t < 0.3 ? seg(t, 0, 0.3) : 1;
      // カーソル: ウィンドウのタイトルへ移動 → つかんで一緒に動く → 離れる
      var px, py;
      if (t < 1.3) { var k = ease(seg(t, 0.2, 1.1)); px = lerp(520, 345, k); py = lerp(330, 130, k); }
      else if (t < 3.9) { px = wx + 45; py = wy + 10; }
      else { var k2 = ease(seg(t, 3.9, 4.4)); px = lerp(wx + 45, wx + 150, k2); py = lerp(wy + 10, wy - 40, k2); }
      var ptrOn = t < 4.4 ? 1 : 0;
      // 指: タップ → 2本指で上へスクロール
      var tap = seg(t, 4.6, 5.0), tapOn = t > 4.6 && t < 5.3;
      var sc = ease(seg(t, 5.6, 7.2)), twoOn = t > 5.5 && t < 7.4;
      var scroll = -40 * sc * (t < 9.2 ? 1 : 0);
      screens.forEach(function (s) {
        s.win.style.transform = "translate(" + (wx - s.x) + "px," + (wy - s.y) + "px)";
        s.win.style.opacity = fade;
        s.win.classList.toggle("grab", t > 1.2 && t < 3.8);
        s.tiles.style.transform = "translateY(" + scroll + "px)";
        s.ptr.style.transform = "translate(" + (px - s.x) + "px," + (py - s.y) + "px)";
        s.ptr.style.opacity = ptrOn;
        if (s.cells[4]) s.cells[4].classList.toggle("picked", t > 4.85 && t < 9.2);
        if (s.f1) {
          var fx = 795 + 115, fy = 150 + 80;
          if (tapOn) {
            s.f1.style.opacity = 1 - tap * 0.6; s.f2.style.opacity = 0;
            s.f1.style.transform = "translate(" + (fx - s.x) + "px," + (fy - s.y) + "px) scale(" + (1 + tap * 0.4) + ")";
          } else if (twoOn) {
            var oy = -60 * sc;
            s.f1.style.opacity = s.f2.style.opacity = 1;
            s.f1.style.transform = "translate(" + (fx - 22 - s.x) + "px," + (fy + 30 + oy - s.y) + "px)";
            s.f2.style.transform = "translate(" + (fx + 22 - s.x) + "px," + (fy + 30 + oy - s.y) + "px)";
          } else { s.f1.style.opacity = s.f2.style.opacity = 0; }
        }
      });
      var step = t < 1.3 || t > 9.2 ? 1 : t < 4.4 ? 2 : 3;
      steps.forEach(function (li) { li.classList.toggle("on", +li.dataset.step === step); });
    }

    // 確認用: ?t=5 でその時刻のコマに止める（&mode=wifi で Wi-Fi 版）
    var q = new URLSearchParams(location.search);
    if (q.has("t")) { frame(+q.get("t"), q.get("mode") === "wifi" ? 1 : 0); return; }

    // 動きを減らす設定の人には、ウィンドウが Android に移ったあとの1コマを見せる
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      frame(8, 0);
      steps.forEach(function (li) { li.classList.add("on"); });
      return;
    }
    var start = performance.now(), visible = true;
    new IntersectionObserver(function (e) { visible = e[0].isIntersecting; }).observe(wrap);
    (function tick(now) {
      if (visible) {
        var sec = (now - start) / 1000;
        frame(sec % LOOP, Math.floor(sec / LOOP));
      }
      requestAnimationFrame(tick);
    })(start);
  })();
</script>"""


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
    html = html.replace("{{SCRIPT}}", script(lang) + "\n" + DEMO_SCRIPT)
    html = html.replace("{{LANG}}", lang).replace("{{ROOT}}", page["root"])
    for k, v in page["alts"].items():
        html = html.replace("{{" + k + "}}", v.replace('"', "&quot;"))
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
