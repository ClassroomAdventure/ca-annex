import CoreGraphics
import CVirtualDisplay
import Foundation

/// Sidecar と同じ「拡張ディスプレイ」として macOS に見える仮想ディスプレイ。
final class VirtualDisplay {
    private let display: CGVirtualDisplay
    var displayID: CGDirectDisplayID { display.displayID }

    init(name: String) {
        let d = CGVirtualDisplayDescriptor()
        d.queue = DispatchQueue.main
        d.name = name
        // モードは後から変えられるので上限は大きめに取る
        d.maxPixelsWide = 4096
        d.maxPixelsHigh = 4096
        d.sizeInMillimeters = CGSize(width: 180, height: 160)
        d.vendorID = 0x5353   // "SS"
        d.productID = 0x0001
        d.serialNum = 0x0001
        display = CGVirtualDisplay(descriptor: d)
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
