import Foundation

/// 回線状況に合わせてビットレートを上下させる（Wi-Fi 用）。
/// Android から 250ms ごとに届く「受信数・欠落数・通信遅延」を見る。
///
/// Wi-Fi は混んでいなくても遅延が一瞬跳ねる（省電力の受信間隔、他の電波など）。
/// 一回の跳ねで下げると、揺れの多い Wi-Fi では下限に張り付いて画質もフレームも落ちる（実際に起きた）。
/// なので遅延は平滑化し、「欠落が出た」か「遅延の伸びが続いた」ときだけ下げ、落ち着いたら早めに戻す。
final class BitrateController {
    /// ANNEX_DEBUG_ABR=1 で判断の材料をログに出す
    static let debug = ProcessInfo.processInfo.environment["ANNEX_DEBUG_ABR"] != nil
    let minKbps: Int
    let maxKbps: Int
    private(set) var kbps: Int
    private var baselines: [(t: TimeInterval, us: Int)] = []
    private var queuingMs: Double = 0   // 平滑化した「平常時より伸びた分」
    private var highStreak = 0
    private var lastDecrease: TimeInterval = 0
    private var lastIncrease: TimeInterval = 0

    /// pixels: 画面の画素数。下限は画面の大きさに見合った値にする（大きい画面を 3Mbps で送ると見られたものではない）
    init(maxKbps: Int, pixels: Int) {
        minKbps = min(maxKbps, max(3000, pixels / 800))
        self.maxKbps = max(minKbps, maxKbps)
        kbps = max(minKbps, maxKbps * 7 / 10)
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
        let sample = max(0, Double(avgNetUs - baseline) / 1000)
        queuingMs = queuingMs * 0.7 + sample * 0.3
        highStreak = queuingMs > 25 ? highStreak + 1 : 0
        let lossRate = Double(lost) / Double(received + lost)

        var next = kbps
        if lossRate > 0.03, now - lastDecrease > 0.5 {
            next = Int(Double(kbps) * 0.75)
            lastDecrease = now
        } else if highStreak >= 2, now - lastDecrease > 0.5 {
            next = Int(Double(kbps) * 0.85)
            lastDecrease = now
            highStreak = 0
        } else if lost == 0, queuingMs < 10, now - lastDecrease > 1.0, now - lastIncrease > 0.5 {
            next = Int(Double(kbps) * 1.12) + 300
            lastIncrease = now
        }
        next = min(maxKbps, max(minKbps, next))
        if Self.debug {
            NSLog(String(format: "abr recv=%d lost=%d avg=%.1fms base=%.1fms q=%.1fms streak=%d → %d kbps (min %d)",
                         received, lost, Double(avgNetUs) / 1000, Double(baseline) / 1000, queuingMs, highStreak, next, minKbps))
        }
        guard next != kbps else { return nil }
        kbps = next
        return next
    }
}
