import Foundation

/// 回線状況に合わせてビットレートを上下させる（Wi-Fi 用）。
/// Android から 250ms ごとに届く「受信数・欠落数・通信遅延」を見て、
/// 詰まり始め（遅延が平常時より伸びる / 欠落が出る）ですばやく下げ、安定していればゆっくり上げる。
final class BitrateController {
    let minKbps = 3000
    let maxKbps: Int
    private(set) var kbps: Int
    private var baselines: [(t: TimeInterval, us: Int)] = []
    private var lastDecrease: TimeInterval = 0
    private var lastIncrease: TimeInterval = 0

    init(maxKbps: Int) {
        self.maxKbps = max(minKbps, maxKbps)
        kbps = max(minKbps, maxKbps * 6 / 10)
    }

    /// 変更があれば新しい値を返す
    func update(received: Int, lost: Int, avgNetUs: Int, minNetUs: Int) -> Int? {
        let now = ProcessInfo.processInfo.systemUptime
        if received > 0 {
            baselines.append((now, minNetUs))
            baselines.removeAll { now - $0.t > 10 }
        }
        guard received + lost > 0 else { return nil } // 画面が止まっているときは判断しない
        let baseline = baselines.map(\.us).min() ?? minNetUs
        let queuingMs = Double(avgNetUs - baseline) / 1000
        let lossRate = Double(lost) / Double(received + lost)

        var next = kbps
        if lossRate > 0.02 || queuingMs > 15 {
            if now - lastDecrease > 0.3 {
                next = Int(Double(kbps) * (lossRate > 0.1 || queuingMs > 40 ? 0.6 : 0.8))
                lastDecrease = now
            }
        } else if queuingMs < 5 && now - lastDecrease > 1.5 && now - lastIncrease > 0.5 {
            next = Int(Double(kbps) * 1.08) + 200
            lastIncrease = now
        }
        next = min(maxKbps, max(minKbps, next))
        guard next != kbps else { return nil }
        kbps = next
        return next
    }
}
