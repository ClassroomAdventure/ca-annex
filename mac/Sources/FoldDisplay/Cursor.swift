import AppKit
import CoreGraphics

/// Mac のカーソル位置と形を監視して、変化したら送る。
/// 映像にカーソルを焼き込まず Android 側で描くことで、指の動きにカーソルが遅れずに付いてくる。
final class CursorTracker {
    private let displayID: () -> CGDirectDisplayID?
    private let send: (Data, UInt8) -> Void
    private var timer: DispatchSourceTimer?
    private var lastPos: (Bool, Float, Float) = (false, -1, -1)
    private var lastShapeKey = 0
    private var tick = 0

    init(displayID: @escaping () -> CGDirectDisplayID?, send: @escaping (Data, UInt8) -> Void) {
        self.displayID = displayID
        self.send = send
    }

    func start() {
        stop()
        let t = DispatchSource.makeTimerSource(queue: .main)
        t.schedule(deadline: .now(), repeating: 1.0 / 120)
        t.setEventHandler { [weak self] in self?.poll() }
        t.resume()
        timer = t
    }

    func stop() { timer?.cancel(); timer = nil }

    /// 新しいクライアント・解像度変更時に全部送り直す
    func resend() {
        lastPos = (false, -1, -1)
        lastShapeKey = 0
    }

    private func poll() {
        guard let id = displayID(), let loc = CGEvent(source: nil)?.location else { return }
        let b = CGDisplayBounds(id)
        let inside = b.contains(loc)
        let x = Float((loc.x - b.minX) / b.width)
        let y = Float((loc.y - b.minY) / b.height)
        if inside != lastPos.0 || (inside && (x != lastPos.1 || y != lastPos.2)) {
            lastPos = (inside, x, y)
            var d = Data()
            d.append(inside ? 1 : 0)
            d.appendBE(x.bitPattern)
            d.appendBE(y.bitPattern)
            send(d, Proto.cursorPos)
        }
        tick += 1
        if inside && tick % 4 == 0 { pollShape() } // 形は 30Hz で十分
    }

    private func pollShape() {
        guard let c = NSCursor.currentSystem else { return }
        let img = c.image
        // 一番大きい表現を PNG にして送る（Android 側で拡大縮小）
        guard let rep = img.representations.max(by: { $0.pixelsWide < $1.pixelsWide }) else { return }
        let cg: CGImage?
        if let bmp = rep as? NSBitmapImageRep {
            cg = bmp.cgImage
        } else {
            var rect = CGRect(origin: .zero, size: img.size)
            cg = img.cgImage(forProposedRect: &rect, context: nil, hints: nil)
        }
        guard let cg else { return }
        var hasher = Hasher()
        hasher.combine(cg.width); hasher.combine(cg.height)
        hasher.combine(c.hotSpot.x); hasher.combine(c.hotSpot.y)
        if let data = cg.dataProvider?.data as Data? { hasher.combine(data) }
        let key = hasher.finalize()
        guard key != lastShapeKey else { return }
        lastShapeKey = key
        guard let png = NSBitmapImageRep(cgImage: cg).representation(using: .png, properties: [:]) else { return }
        var d = Data()
        d.appendBE(Float(c.hotSpot.x).bitPattern)
        d.appendBE(Float(c.hotSpot.y).bitPattern)
        d.appendBE(Float(img.size.width).bitPattern)
        d.appendBE(Float(img.size.height).bitPattern)
        d.append(png)
        send(d, Proto.cursorImage)
    }
}
