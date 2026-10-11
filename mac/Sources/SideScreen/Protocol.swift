import CoreMedia
import Foundation

/// Mac ⇄ Android の通信プロトコル。全メッセージは [type:u8][length:u32 BE][payload]。
enum Proto {
    static let port: UInt16 = 47800
    static let bonjourType = "_sidescreen._tcp"

    // Android → Mac
    static let hello: UInt8 = 0x01   // width u32, height u32, scale f32, bitrateKbps u32, fps u8, codec u8, udpPort u16（0=TCPで受ける）, nameLen u8, name UTF-8, idLen u8, deviceId UTF-8
    static let mouse: UInt8 = 0x20   // kind u8, x f32, y f32 (0..1 正規化)
    static let scroll: UInt8 = 0x21  // x f32, y f32, dx f32, dy f32 (dx/dy は端末ピクセル)
    static let keyframe: UInt8 = 0x30 // キーフレーム要求（payload なし）
    static let ping: UInt8 = 0x40     // clientTimeNs i64
    static let stats: UInt8 = 0x41    // received u32, lost u32, avgNetUs u32, minNetUs u32（250ms ごと）

    // Mac → Android
    static let config: UInt8 = 0x81  // width u32, height u32, codec u8
    static let video: UInt8 = 0x82   // flags u8, captureUs u64, encodedUs u64, Annex-B
    static let cursorPos: UInt8 = 0x83   // visible u8, x f32, y f32（0..1）
    static let cursorImage: UInt8 = 0x84 // hotX f32, hotY f32, widthPt f32, heightPt f32, PNG
    static let bitrate: UInt8 = 0x85     // kbps u32（自動調整で変わったとき）
    static let approval: UInt8 = 0x86    // state u8: 0=確認待ち, 1=許可, 2=拒否
    static let pong: UInt8 = 0xC0    // clientTimeNs i64, serverUs u64

    /// Mac のホストクロック（ScreenCaptureKit の PTS と同じ時計）をマイクロ秒で
    static func nowUs() -> UInt64 {
        UInt64(CMClockGetTime(CMClockGetHostTimeClock()).seconds * 1_000_000)
    }

    static let flagCodecConfig: UInt8 = 1
    static let flagKeyFrame: UInt8 = 2

    enum MouseKind: UInt8 {
        case move = 0, leftDown = 1, leftUp = 2, rightClick = 3, leftDrag = 4
    }
}

struct Hello {
    var width: Int
    var height: Int
    var scale: Double
    var bitrateKbps: Int
    var fps: Int
    var codec: VideoCodec
    var udpPort: UInt16
    var deviceName: String
    var deviceId: String
}

extension Data {
    mutating func appendBE<T: FixedWidthInteger>(_ v: T) {
        var be = v.bigEndian
        Swift.withUnsafeBytes(of: &be) { append(contentsOf: $0) }
    }

    func readBE<T: FixedWidthInteger>(_ type: T.Type, at offset: Int) -> T {
        var v: T = 0
        for i in 0..<MemoryLayout<T>.size {
            v = (v << 8) | T(self[startIndex + offset + i])
        }
        return v
    }

    func readFloat(at offset: Int) -> Float {
        Float(bitPattern: readBE(UInt32.self, at: offset))
    }
}

func packet(_ type: UInt8, _ payload: Data) -> Data {
    var d = Data(capacity: payload.count + 5)
    d.append(type)
    d.appendBE(UInt32(payload.count))
    d.append(payload)
    return d
}
