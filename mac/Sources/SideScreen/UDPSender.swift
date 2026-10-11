import Darwin
import Foundation

/// Wi-Fi 用の映像送信（UDP）。TCP だと1パケット落ちただけで後続が全部待たされるため。
///
/// 1フレームを SHARD バイトずつに分け、GROUP 個ごとに XOR パリティを1つ付ける（1個までの欠落を復元）。
/// データグラム: frameId u32, index u16, dataCount u16, parityCount u16, frameLen u32, shard
final class UDPSender {
    static let shard = 1200
    static let group = 8
    static let headerSize = 14

    private let fd: Int32
    private var addr: sockaddr_storage
    private let addrLen: socklen_t
    private let queue = DispatchQueue(label: "udp-send", qos: .userInteractive)
    private var frameId: UInt32 = 0
    /// 試験用: SIDESCREEN_DROP=0.03 で 3% のパケットをわざと捨てる
    private let dropRate = Double(ProcessInfo.processInfo.environment["SIDESCREEN_DROP"] ?? "") ?? 0

    init?(host: String, port: UInt16) {
        var hints = addrinfo(ai_flags: AI_NUMERICHOST, ai_family: AF_UNSPEC, ai_socktype: SOCK_DGRAM, ai_protocol: 0,
                             ai_addrlen: 0, ai_canonname: nil, ai_addr: nil, ai_next: nil)
        var res: UnsafeMutablePointer<addrinfo>?
        // "::ffff:192.168.1.33" や "fe80::1%en0" の形でも来る
        var h = host
        if h.hasPrefix("::ffff:") { h = String(h.dropFirst(7)) }
        guard getaddrinfo(h, String(port), &hints, &res) == 0, let ai = res else { return nil }
        defer { freeaddrinfo(res) }
        fd = socket(ai.pointee.ai_family, SOCK_DGRAM, IPPROTO_UDP)
        guard fd >= 0 else { return nil }
        addr = sockaddr_storage()
        addrLen = ai.pointee.ai_addrlen
        withUnsafeMutableBytes(of: &addr) { dst in
            dst.copyMemory(from: UnsafeRawBufferPointer(start: ai.pointee.ai_addr, count: Int(ai.pointee.ai_addrlen)))
        }
        var buf: Int32 = 4 << 20
        setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &buf, socklen_t(MemoryLayout<Int32>.size))
        // DSCP CS5 → Wi-Fi の WMM で「映像」優先キューに載る
        var tos: Int32 = 0xA0
        if ai.pointee.ai_family == AF_INET {
            setsockopt(fd, IPPROTO_IP, IP_TOS, &tos, socklen_t(MemoryLayout<Int32>.size))
        } else {
            setsockopt(fd, IPPROTO_IPV6, IPV6_TCLASS, &tos, socklen_t(MemoryLayout<Int32>.size))
        }
        var noSigpipe: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &noSigpipe, socklen_t(MemoryLayout<Int32>.size))
    }

    deinit { close(fd) }

    /// payload = TCP の video メッセージと同じ中身（flags, captureUs, encodedUs, データ）
    func send(_ payload: Data) {
        queue.async { [self] in sendNow(payload) }
    }

    private func sendNow(_ payload: Data) {
        frameId &+= 1
        let n = payload.count
        let dataCount = (n + Self.shard - 1) / Self.shard
        let parityCount = (dataCount + Self.group - 1) / Self.group
        var parity = [[UInt8]](repeating: [UInt8](repeating: 0, count: Self.shard), count: parityCount)
        var pkt = [UInt8](repeating: 0, count: Self.headerSize + Self.shard)

        func header(_ index: Int) {
            pkt[0] = UInt8(frameId >> 24); pkt[1] = UInt8(frameId >> 16 & 0xff)
            pkt[2] = UInt8(frameId >> 8 & 0xff); pkt[3] = UInt8(frameId & 0xff)
            pkt[4] = UInt8(index >> 8); pkt[5] = UInt8(index & 0xff)
            pkt[6] = UInt8(dataCount >> 8); pkt[7] = UInt8(dataCount & 0xff)
            pkt[8] = UInt8(parityCount >> 8); pkt[9] = UInt8(parityCount & 0xff)
            pkt[10] = UInt8(n >> 24 & 0xff); pkt[11] = UInt8(n >> 16 & 0xff)
            pkt[12] = UInt8(n >> 8 & 0xff); pkt[13] = UInt8(n & 0xff)
        }

        var sentInBurst = 0
        func emit(_ len: Int) {
            if dropRate > 0, Double.random(in: 0..<1) < dropRate { return }
            _ = pkt.withUnsafeBytes { p in
                withUnsafePointer(to: &addr) {
                    $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { sa in
                        sendto(fd, p.baseAddress, Self.headerSize + len, 0, sa, addrLen)
                    }
                }
            }
            // 一気に撃つと Wi-Fi の送信キューで落ちるので、16個ごとに少し間を空ける（≒ 120Mbps）
            sentInBurst += 1
            if sentInBurst % 16 == 0 { usleep(1200) }
        }

        payload.withUnsafeBytes { (src: UnsafeRawBufferPointer) in
            for i in 0..<dataCount {
                let off = i * Self.shard
                let len = min(Self.shard, n - off)
                header(i)
                pkt.withUnsafeMutableBytes { dst in
                    dst.baseAddress!.advanced(by: Self.headerSize).copyMemory(from: src.baseAddress!.advanced(by: off), byteCount: len)
                    if len < Self.shard { memset(dst.baseAddress!.advanced(by: Self.headerSize + len), 0, Self.shard - len) }
                }
                let g = i / Self.group
                for j in 0..<Self.shard { parity[g][j] ^= pkt[Self.headerSize + j] }
                emit(Self.shard)
            }
            for g in 0..<parityCount {
                header(dataCount + g)
                pkt.replaceSubrange(Self.headerSize..<(Self.headerSize + Self.shard), with: parity[g])
                emit(Self.shard)
            }
        }
    }
}
