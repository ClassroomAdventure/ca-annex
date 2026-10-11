import CoreMedia
import CoreVideo
import Foundation

/// `SideScreen --bench-encoder [fps] [秒]` — 合成した画面を一定間隔でエンコーダに流し、
/// 「処理中の上限あり / なし」で遅延が伸び続けないかを比べる開発用の計測。
/// 遅い Mac の再現は SIDESCREEN_SLOW_ENCODE_MS=12 などと組み合わせる。
enum EncoderBench {
    static func run(fps: Int, seconds: Int) {
        let w = 2448, h = 1848
        for limit in [Int.max, 2] {
            let label = limit == .max ? "上限なし" : "上限 \(limit) 枚"
            var latencies: [[Double]] = Array(repeating: [], count: seconds)
            let lock = NSLock()
            let start = Proto.nowUs()
            guard let enc = VideoEncoder.make(codec: .hevc, width: w, height: h, fps: fps, bitrateKbps: 30000, output: { _, flags, pts in
                guard flags != Proto.flagCodecConfig else { return }
                let now = Proto.nowUs()
                let sec = Int((now - start) / 1_000_000)
                lock.withLock { if sec < seconds { latencies[sec].append(Double(now - pts) / 1000) } }
            }) else { print("encoder unavailable"); return }
            enc.maxInFlight = limit

            var pool: CVPixelBufferPool?
            CVPixelBufferPoolCreate(nil, nil, [
                kCVPixelBufferPixelFormatTypeKey: kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,
                kCVPixelBufferWidthKey: w, kCVPixelBufferHeightKey: h,
                kCVPixelBufferIOSurfacePropertiesKey: [:],
            ] as CFDictionary, &pool)

            let interval = 1.0 / Double(fps)
            var next = ProcessInfo.processInfo.systemUptime
            for i in 0..<(fps * seconds) {
                var pb: CVPixelBuffer?
                CVPixelBufferPoolCreatePixelBuffer(nil, pool!, &pb)
                guard let pb else { continue }
                // 毎フレーム違う模様（動いている画面のつもり）
                CVPixelBufferLockBaseAddress(pb, [])
                let y = CVPixelBufferGetBaseAddressOfPlane(pb, 0)!.assumingMemoryBound(to: UInt8.self)
                let stride = CVPixelBufferGetBytesPerRowOfPlane(pb, 0)
                for row in 0..<h { memset(y + row * stride, Int32((row + i * 7) & 0xff), w) }
                memset(CVPixelBufferGetBaseAddressOfPlane(pb, 1)!, 128, CVPixelBufferGetBytesPerRowOfPlane(pb, 1) * h / 2)
                CVPixelBufferUnlockBaseAddress(pb, [])
                enc.encode(pb, pts: CMTime(value: CMTimeValue(Proto.nowUs()), timescale: 1_000_000))
                next += interval
                let wait = next - ProcessInfo.processInfo.systemUptime
                if wait > 0 { Thread.sleep(forTimeInterval: wait) }
            }
            Thread.sleep(forTimeInterval: 1.0)
            enc.invalidate()
            print("\(label)（\(fps)fps）: 捨てた \(enc.droppedFrames) 枚")
            for (s, l) in latencies.enumerated() where !l.isEmpty {
                print(String(format: "  %d秒目  平均 %5.1f ms  最大 %5.1f ms  (%d 枚)", s + 1, l.reduce(0, +) / Double(l.count), l.max()!, l.count))
            }
        }
    }
}
