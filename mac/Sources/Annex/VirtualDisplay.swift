import CoreGraphics
import CVirtualDisplay
import AppKit
import Foundation

/// Sidecar と同じ「拡張ディスプレイ」として macOS に見える仮想ディスプレイ。
final class VirtualDisplay {
    private let display: CGVirtualDisplay
    let name: String
    var displayID: CGDirectDisplayID { display.displayID }

    /// この macOS で仮想ディスプレイ（非公開 API）が使えるか
    /// 試験用: ANNEX_SIMULATE_UNSUPPORTED=1 で「使えない macOS」を再現する
    static var isSupported: Bool {
        ProcessInfo.processInfo.environment["ANNEX_SIMULATE_UNSUPPORTED"] == nil && CVDIsAvailable()
    }

    /// 作れなかったら nil（直前に消したディスプレイの後片付けが終わっていないとき、
    /// または将来の macOS で非公開 API が変わって使えなくなったとき）
    init?(name: String) {
        self.name = name
        guard Self.isSupported, let d = CVDMakeDescriptor() else { return nil }
        // 項目が将来消えても落ちないよう、あるものだけ設定する
        func set(_ key: String, _ value: Any) {
            let setter = "set" + key.prefix(1).uppercased() + key.dropFirst() + ":"
            if d.responds(to: NSSelectorFromString(setter)) { d.setValue(value, forKey: key) }
        }
        set("queue", DispatchQueue.main)
        set("name", name)
        // モードは後から変えられるので上限は大きめに取る
        set("maxPixelsWide", 4096)
        set("maxPixelsHigh", 4096)
        set("sizeInMillimeters", NSValue(size: NSSize(width: 180, height: 160)))
        set("vendorID", 0x414E)   // "AN"
        set("productID", 0x0001)
        // macOS はディスプレイの配置（左右上下）を UUID ごとに覚える。UUID は vendor/product/serialNumber から
        // 作られるので、端末ごとに固定のシリアルを付けると、次に接続したとき前の配置に戻る。
        // （serialNum は古い名前。macOS 27 では serialNumber を設定しないと毎回ランダムな UUID になる）
        let serial = Self.serial(for: name)
        set("serialNum", serial)
        set("serialNumber", serial)
        guard let v = CVDMakeDisplay(d), v.displayID != 0 else { return nil }
        display = v
    }

    /// 端末名から作る固定のシリアル（FNV-1a）。0 は避ける
    static func serial(for name: String) -> UInt32 {
        var h: UInt32 = 0x811C9DC5
        for b in name.utf8 { h = (h ^ UInt32(b)) &* 0x0100_0193 }
        return h == 0 ? 1 : h
    }

    /// points × 2 ピクセルの HiDPI モードを設定する。
    @discardableResult
    func setMode(pointsWide: Int, pointsHigh: Int, refreshRate: Double) -> Bool {
        guard let s = CVDMakeSettings(),
              let mode = CVDMakeMode(UInt32(pointsWide), UInt32(pointsHigh), refreshRate) else { return false }
        if s.responds(to: NSSelectorFromString("setHiDPI:")) { s.hiDPI = 1 }
        s.modes = [mode]
        return display.apply(s)
    }
}
