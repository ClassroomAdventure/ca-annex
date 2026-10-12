import CoreGraphics
import CVirtualDisplay
import Foundation

/// Sidecar と同じ「拡張ディスプレイ」として macOS に見える仮想ディスプレイ。
final class VirtualDisplay {
    private let display: CGVirtualDisplay
    let name: String
    var displayID: CGDirectDisplayID { display.displayID }

    /// 作れなかったら nil（直前に消したディスプレイの後片付けが終わっていないときなど）
    init?(name: String) {
        self.name = name
        let d = CGVirtualDisplayDescriptor()
        d.queue = DispatchQueue.main
        d.name = name
        // モードは後から変えられるので上限は大きめに取る
        d.maxPixelsWide = 4096
        d.maxPixelsHigh = 4096
        d.sizeInMillimeters = CGSize(width: 180, height: 160)
        d.vendorID = 0x414E   // "AN"
        d.productID = 0x0001
        // macOS はディスプレイの配置（左右上下）を UUID ごとに覚える。UUID は vendor/product/serialNumber から
        // 作られるので、端末ごとに固定のシリアルを付けると、次に接続したとき前の配置に戻る。
        // （serialNum は古い名前。macOS 27 では serialNumber を設定しないと毎回ランダムな UUID になる）
        let serial = Self.serial(for: name)
        d.serialNum = serial
        if d.responds(to: NSSelectorFromString("setSerialNumber:")) {
            d.setValue(serial, forKey: "serialNumber")
        }
        display = CGVirtualDisplay(descriptor: d)
        if display.displayID == 0 { return nil }
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
        let s = CGVirtualDisplaySettings()
        s.hiDPI = 1
        s.modes = [CGVirtualDisplayMode(width: UInt32(pointsWide), height: UInt32(pointsHigh), refreshRate: refreshRate)]
        return display.apply(s)
    }
}
