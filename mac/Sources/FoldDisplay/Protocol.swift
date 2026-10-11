import Foundation

/// Mac ⇄ Android の通信プロトコル。全メッセージは [type:u8][length:u32 BE][payload]。
enum Proto {
    static let port: UInt16 = 47800
    static let bonjourType = "_folddisplay._tcp"

    // Android → Mac
    static let hello: UInt8 = 0x01   // width u32, height u32, scale f32, bitrateKbps u32, fps u8
    static let mouse: UInt8 = 0x20   // kind u8, x f32, y f32 (0..1 正規化)
    static let scroll: UInt8 = 0x21  // x f32, y f32, dx f32, dy f32 (dx/dy は端末ピクセル)
    static let keyframe: UInt8 = 0x30 // キーフレーム要求（payload なし）

    // Mac → Android
    static let config: UInt8 = 0x81  // width u32, height u32
    static let video: UInt8 = 0x82   // flags u8, pts u64(us), Annex-B H.264

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
