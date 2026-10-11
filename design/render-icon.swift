// アイコンのレイヤー画像（1024×1024 PNG）を描き出す。
// 使い方: swift design/render-icon.swift <出力ディレクトリ>
import AppKit
import CoreGraphics

let out = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "."
let S: CGFloat = 1024

/// 形の定義（Android のベクターもこの数値から作る）。座標は左上原点。
struct Shapes {
    // メインの画面（Mac）
    static let main = CGRect(x: 168, y: 300, width: 540, height: 360)
    static let mainRadius: CGFloat = 52
    // 横の画面（Android）
    static let side = CGRect(x: 596, y: 404, width: 260, height: 360)
    static let sideRadius: CGFloat = 48
}

func png(_ name: String, draw: (CGContext) -> Void) {
    let cs = CGColorSpace(name: CGColorSpace.displayP3)!
    let ctx = CGContext(data: nil, width: Int(S), height: Int(S), bitsPerComponent: 8, bytesPerRow: 0, space: cs,
                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.translateBy(x: 0, y: S); ctx.scaleBy(x: 1, y: -1) // 左上原点
    draw(ctx)
    let rep = NSBitmapImageRep(cgImage: ctx.makeImage()!)
    try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: "\(out)/\(name)"))
}

func rounded(_ r: CGRect, _ radius: CGFloat) -> CGPath {
    CGPath(roundedRect: r, cornerWidth: radius, cornerHeight: radius, transform: nil)
}

// レイヤー: メインの画面（白、下に細いスタンド）
png("main.png") { c in
    c.setFillColor(CGColor(gray: 1, alpha: 1))
    c.addPath(rounded(Shapes.main, Shapes.mainRadius)); c.fillPath()
    // スタンド
    let standW: CGFloat = 150, standH: CGFloat = 26
    c.addPath(rounded(CGRect(x: Shapes.main.midX - standW / 2 - 60, y: Shapes.main.maxY + 34, width: standW, height: standH), 13))
    c.fillPath()
}

// レイヤー: 横の画面（少し青みのある白）。重なり部分はガラスで奥行きが出る
png("side.png") { c in
    c.setFillColor(CGColor(srgbRed: 0.86, green: 0.92, blue: 1.0, alpha: 1))
    c.addPath(rounded(Shapes.side, Shapes.sideRadius)); c.fillPath()
}

// プレビュー（確認用。実際の見た目は Liquid Glass で描画される）
png("preview.png") { c in
    let inset: CGFloat = 100
    let body = CGRect(x: inset, y: inset, width: S - inset * 2, height: S - inset * 2)
    c.saveGState()
    c.addPath(rounded(body, 185)); c.clip()
    let g = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: [
        CGColor(srgbRed: 0.29, green: 0.56, blue: 1.0, alpha: 1), CGColor(srgbRed: 0.16, green: 0.30, blue: 0.92, alpha: 1)] as CFArray,
        locations: [0, 1])!
    c.drawLinearGradient(g, start: CGPoint(x: 0, y: inset), end: CGPoint(x: 0, y: S - inset), options: [])
    // 824/1024 に縮めてグリフを置く
    c.translateBy(x: S / 2, y: S / 2); c.scaleBy(x: 824 / S, y: 824 / S); c.translateBy(x: -S / 2, y: -S / 2)
    c.setShadow(offset: CGSize(width: 0, height: 10), blur: 30, color: CGColor(gray: 0, alpha: 0.25))
    c.setFillColor(CGColor(gray: 1, alpha: 1))
    c.addPath(rounded(Shapes.main, Shapes.mainRadius)); c.fillPath()
    c.addPath(rounded(CGRect(x: Shapes.main.midX - 135, y: Shapes.main.maxY + 34, width: 150, height: 26), 13)); c.fillPath()
    c.setFillColor(CGColor(srgbRed: 0.86, green: 0.92, blue: 1.0, alpha: 1))
    c.addPath(rounded(Shapes.side, Shapes.sideRadius)); c.fillPath()
    c.restoreGState()
}
print("rendered to \(out)")
