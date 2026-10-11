import CoreMedia
import Foundation
import Network

/// 接続中の Android 1台ぶんの状態（受信・映像送信・入力）。
final class ClientSession {
    let conn: NWConnection
    let isUSB: Bool
    let label: String
    private let displays: DisplayManager
    private let videoQueue = DispatchQueue(label: "video", qos: .userInteractive)
    private lazy var capture = DisplayCapture(queue: videoQueue) { [weak self] pb, pts in self?.onFrame(pb, pts) }
    private let input: InputInjector
    private var cursor: CursorTracker!
    var onClose: ((ClientSession) -> Void)?
    var onStatus: (() -> Void)?

    // videoQueue 上でのみ触る
    private var encoder: VideoEncoder?
    private var lastBuffer: CVPixelBuffer?
    private var lastFrameAt: TimeInterval = 0
    private var refreshCount = 2
    private var idleTimer: DispatchSourceTimer?

    // 送信の滞留量（Wi-Fi が詰まったら P フレームを捨ててキーフレームで復帰する）
    private let lock = NSLock()
    private var pendingBytes = 0
    private var maxPending = 1_500_000

    private var configGeneration = 0
    private var udp: UDPSender?
    private var abr: BitrateController?
    private(set) var resolution: (Int, Int)?
    private var closed = false

    init(conn: NWConnection, displays: DisplayManager) {
        self.conn = conn
        self.displays = displays
        var usb = false
        var label = "?"
        if case let .hostPort(host, _) = conn.endpoint {
            label = "\(host)"
            usb = label == "127.0.0.1" || label == "::1" || label.hasPrefix("::ffff:127.")
        }
        self.isUSB = usb
        self.label = label
        input = InputInjector(displayID: { [weak displays] in displays?.current?.displayID })
        cursor = CursorTracker(displayID: { [weak displays] in displays?.current?.displayID }) { [weak self] d, type in
            self?.send(packet(type, d), droppable: false)
        }
    }

    func start() {
        conn.stateUpdateHandler = { [weak self] st in
            switch st {
            case .failed, .cancelled: self?.close()
            default: break
            }
        }
        conn.start(queue: .main)
        readHeader()
    }

    func close() {
        guard !closed else { return }
        closed = true
        conn.cancel()
        cursor.stop()
        input.releaseAll()
        let cap = capture
        Task { await cap.stop() }
        videoQueue.async { [self] in
            idleTimer?.cancel()
            encoder?.invalidate()
            encoder = nil
            lastBuffer = nil
        }
        onClose?(self)
    }

    // MARK: 受信

    private func readHeader() {
        conn.receive(minimumIncompleteLength: 5, maximumLength: 5) { [weak self] data, _, done, err in
            guard let self else { return }
            guard let data, data.count == 5, err == nil else { self.close(); return }
            let type = data[data.startIndex]
            let len = Int(data.readBE(UInt32.self, at: 1))
            if len == 0 {
                self.handle(type, Data())
                if done { self.close() } else { self.readHeader() }
            } else if len > 1 << 20 {
                self.close()
            } else {
                self.conn.receive(minimumIncompleteLength: len, maximumLength: len) { body, _, done2, err2 in
                    guard let body, body.count == len, err2 == nil else { self.close(); return }
                    self.handle(type, body)
                    if done2 { self.close() } else { self.readHeader() }
                }
            }
        }
    }

    private func handle(_ type: UInt8, _ p: Data) {
        switch type {
        case Proto.hello where p.count >= 17:
            let h = Hello(width: Int(p.readBE(UInt32.self, at: 0)), height: Int(p.readBE(UInt32.self, at: 4)),
                          scale: Double(p.readFloat(at: 8)), bitrateKbps: Int(p.readBE(UInt32.self, at: 12)),
                          fps: Int(p[p.startIndex + 16]),
                          codec: p.count >= 18 ? VideoCodec(rawValue: p[p.startIndex + 17]) ?? .h264 : .h264,
                          udpPort: p.count >= 20 ? p.readBE(UInt16.self, at: 18) : 0,
                          deviceName: Self.readName(p))
            helloSeq += 1
            configure(h, seq: helloSeq)
        case Proto.mouse where p.count >= 9:
            if let kind = Proto.MouseKind(rawValue: p[p.startIndex]) {
                input.mouse(kind: kind, x: p.readFloat(at: 1), y: p.readFloat(at: 5))
            }
        case Proto.scroll where p.count >= 16:
            input.scroll(x: p.readFloat(at: 0), y: p.readFloat(at: 4), dx: p.readFloat(at: 8), dy: p.readFloat(at: 12))
        case Proto.ping where p.count >= 8:
            var d = Data(p.prefix(8))
            d.appendBE(Proto.nowUs())
            send(packet(Proto.pong, d), droppable: false)
        case Proto.stats where p.count >= 16:
            guard let abr else { break }
            if let kbps = abr.update(received: Int(p.readBE(UInt32.self, at: 0)), lost: Int(p.readBE(UInt32.self, at: 4)),
                                     avgNetUs: Int(p.readBE(UInt32.self, at: 8)), minNetUs: Int(p.readBE(UInt32.self, at: 12))) {
                videoQueue.async { [self] in encoder?.setBitrate(kbps: kbps) }
                sendBitrate(kbps)
            }
        case Proto.keyframe:
            videoQueue.async { [self] in requestKey() }
        default:
            break
        }
    }

    private static func readName(_ p: Data) -> String {
        guard p.count >= 21 else { return "" }
        let n = Int(p[p.startIndex + 20])
        guard p.count >= 21 + n else { return "" }
        return String(decoding: p[(p.startIndex + 21)..<(p.startIndex + 21 + n)], as: UTF8.self)
    }

    // MARK: 設定（解像度が変わるたびに呼ばれる：折りたたみの開閉など）

    private var helloSeq = 0

    private func configure(_ h: Hello, seq: Int, attempt: Int = 0) {
        guard seq == helloSeq else { return } // 待っている間に新しい hello が来た
        let name = h.deviceName.isEmpty ? "Android Display" : h.deviceName
        // 直前にディスプレイを消した直後は作成に失敗することがあるので、少し待ってやり直す
        guard let display = displays.acquire(name: name) else {
            if attempt < 10, !closed {
                NSLog("virtual display not ready, retrying (\(attempt + 1))")
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in self?.configure(h, seq: seq, attempt: attempt + 1) }
            } else {
                NSLog("could not create virtual display")
                close()
            }
            return
        }
        let w = max(320, min(4096, h.width)) & ~1
        let ht = max(320, min(4096, h.height)) & ~1
        let scale = max(1.0, min(3.0, h.scale.isFinite ? h.scale : 2.0))
        let fps = max(15, min(120, h.fps))
        let kbps = max(2000, min(200_000, h.bitrateKbps))
        NSLog("hello \(label) \(w)x\(ht) scale=\(scale) \(kbps)kbps \(fps)fps usb=\(isUSB)")
        configGeneration += 1
        let gen = configGeneration
        resolution = (w, ht)
        lock.withLock { maxPending = max(400_000, kbps * 1000 / 8 / 4) } // 約250ms分

        // Wi-Fi は映像を UDP で、ビットレートは自動調整
        if !isUSB, h.udpPort != 0 {
            if udp == nil { udp = UDPSender(host: label, port: h.udpPort) }
            NSLog("video over UDP to \(label):\(h.udpPort) -> \(udp != nil)")
        }
        let abrCtl: BitrateController? = isUSB ? nil : BitrateController(maxKbps: kbps)
        abr = abrCtl
        let startKbps = abrCtl?.kbps ?? kbps

        display.setMode(pointsWide: Int((Double(w) / scale).rounded()), pointsHigh: Int((Double(ht) / scale).rounded()),
                        refreshRate: Double(fps))
        onStatus?()

        Task { @MainActor in
            await capture.stop()
            guard gen == configGeneration, !closed else { return }
            videoQueue.sync {
                encoder?.invalidate()
                encoder = VideoEncoder.make(codec: h.codec, width: w, height: ht, fps: fps, bitrateKbps: startKbps) { [weak self] data, flags, pts in
                    self?.sendVideo(data, flags: flags, pts: pts)
                }
                lastBuffer = nil
                startIdleTimer()
            }
            var cfg = Data()
            cfg.appendBE(UInt32(w))
            cfg.appendBE(UInt32(ht))
            cfg.append(videoQueue.sync { encoder?.codec.rawValue ?? 0 })
            send(packet(Proto.config, cfg), droppable: false)
            sendBitrate(startKbps)
            cursor.resend()
            cursor.start()
            do {
                try await capture.start(displayID: display.displayID, width: w, height: ht, fps: fps)
            } catch {
                NSLog("capture start failed: \(error)")
                close()
            }
        }
    }

    // MARK: 映像

    private func onFrame(_ pb: CVPixelBuffer, _ pts: CMTime) {
        guard let enc = encoder, CVPixelBufferGetWidth(pb) == enc.width, CVPixelBufferGetHeight(pb) == enc.height else { return }
        lastBuffer = pb
        lastFrameAt = ProcessInfo.processInfo.systemUptime
        refreshCount = 0
        if !enc.encode(pb, pts: pts), enc.droppedFrames % 120 == 1 {
            NSLog("encoder busy: dropped \(enc.droppedFrames) frames so far")
        }
    }

    private func requestKey() {
        encoder?.requestKeyFrame()
        // 画面が止まっているとフレームが来ないので、最後のフレームを再エンコードする
        if let pb = lastBuffer, ProcessInfo.processInfo.systemUptime - lastFrameAt > 0.05 {
            encoder?.encode(pb, pts: CMClockGetTime(CMClockGetHostTimeClock()))
        }
    }

    private func startIdleTimer() {
        idleTimer?.cancel()
        let t = DispatchSource.makeTimerSource(queue: videoQueue)
        t.schedule(deadline: .now() + 0.25, repeating: 0.25)
        t.setEventHandler { [weak self] in
            guard let self, self.refreshCount < 2, let pb = self.lastBuffer,
                  ProcessInfo.processInfo.systemUptime - self.lastFrameAt > 0.3 else { return }
            // 静止したら同じ画を差分フレームとして追加で送り、文字をくっきりさせる
            // （キーフレームで送り直すと通信量が跳ねるので使わない）
            self.refreshCount += 1
            self.encoder?.encode(pb, pts: CMClockGetTime(CMClockGetHostTimeClock()))
        }
        t.resume()
        idleTimer = t
    }

    private var dropping = false

    private func sendVideo(_ data: Data, flags: UInt8, pts: UInt64) {
        var payload = Data(capacity: data.count + 17)
        payload.append(flags)
        payload.appendBE(pts)
        payload.appendBE(Proto.nowUs())
        payload.append(data)
        if let udp {
            udp.send(payload)
            return
        }
        let isRef = flags != 0
        if !isRef {
            let congested = lock.withLock { pendingBytes > maxPending }
            if congested || dropping {
                if !dropping {
                    dropping = true
                    videoQueue.async { [self] in encoder?.requestKeyFrame() }
                }
                return
            }
        } else if flags & Proto.flagKeyFrame != 0 {
            dropping = false
        }
        send(packet(Proto.video, payload), droppable: !isRef)
    }

    private func sendBitrate(_ kbps: Int) {
        var d = Data()
        d.appendBE(UInt32(kbps))
        send(packet(Proto.bitrate, d), droppable: false)
    }

    private func send(_ d: Data, droppable: Bool) {
        lock.withLock { pendingBytes += d.count }
        conn.send(content: d, completion: .contentProcessed { [weak self] err in
            guard let self else { return }
            self.lock.withLock { self.pendingBytes -= d.count }
            if err != nil { DispatchQueue.main.async { self.close() } }
        })
    }
}

/// 仮想ディスプレイは接続中だけ存在させる（切断後しばらくで消す＝Sidecar と同じ挙動）。
final class DisplayManager {
    private(set) var current: VirtualDisplay?
    private var releaseWork: DispatchWorkItem?

    func acquire(name: String) -> VirtualDisplay? {
        releaseWork?.cancel()
        releaseWork = nil
        if let c = current {
            if c.name == name { return c }
            current = nil // 別の端末に替わった。配置はそれぞれの端末ごとに覚えている
        }
        guard let d = VirtualDisplay(name: name) else { return nil }
        current = d
        return d
    }

    /// 折りたたみ時の再接続などで一瞬切れても窓配置が崩れないよう猶予を置く
    func scheduleRelease(after seconds: Double = 4) {
        releaseWork?.cancel()
        let w = DispatchWorkItem { [weak self] in self?.current = nil }
        releaseWork = w
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: w)
    }
}
