import Foundation

/// USB 接続された Android に `adb reverse tcp:47800 tcp:47800` を自動で張る。
final class AdbReverser {
    private let queue = DispatchQueue(label: "adb")
    private var timer: DispatchSourceTimer?
    private var reversed = Set<String>()
    private var tickCount = 0
    private(set) var adbPath: String?
    var onDevicesChanged: ((Set<String>) -> Void)?

    init() {
        let candidates = [
            "/opt/homebrew/bin/adb",
            "/usr/local/bin/adb",
            NSHomeDirectory() + "/Library/Android/sdk/platform-tools/adb",
            "/opt/homebrew/share/android-commandlinetools/platform-tools/adb",
        ]
        adbPath = candidates.first { FileManager.default.isExecutableFile(atPath: $0) }
    }

    func start() {
        guard adbPath != nil else { NSLog("adb not found; USB mode disabled"); return }
        let t = DispatchSource.makeTimerSource(queue: queue)
        t.schedule(deadline: .now(), repeating: 2.0)
        t.setEventHandler { [weak self] in self?.tick() }
        t.resume()
        timer = t
    }

    private func run(_ args: [String]) -> String? {
        guard let adbPath else { return nil }
        let p = Process()
        p.executableURL = URL(fileURLWithPath: adbPath)
        p.arguments = args
        let pipe = Pipe()
        p.standardOutput = pipe
        p.standardError = FileHandle.nullDevice
        do { try p.run() } catch { return nil }
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        return p.terminationStatus == 0 ? String(decoding: data, as: UTF8.self) : nil
    }

    private func tick() {
        guard let out = run(["devices"]) else { return }
        var serials = Set<String>()
        for line in out.split(separator: "\n").dropFirst() {
            let cols = line.split(whereSeparator: { $0 == "\t" || $0 == " " })
            // Wi-Fi adb（ip:port）は除外。USB 接続のものだけ
            if cols.count >= 2, cols[1] == "device", !cols[0].contains(":"), !cols[0].contains("._adb") { serials.insert(String(cols[0])) }
        }
        // adb サーバーが再起動すると reverse は消えるので、ときどき確認して張り直す
        tickCount += 1
        if tickCount % 5 == 0 {
            for s in reversed where !(run(["-s", s, "reverse", "--list"]) ?? "").contains("tcp:\(Proto.port)") {
                NSLog("adb reverse lost for \(s)")
                reversed.remove(s)
            }
        }
        for s in serials where !reversed.contains(s) {
            if run(["-s", s, "reverse", "tcp:\(Proto.port)", "tcp:\(Proto.port)"]) != nil {
                NSLog("adb reverse set for \(s)")
                reversed.insert(s)
            }
        }
        let changed = reversed != reversed.intersection(serials)
        reversed.formIntersection(serials)
        if changed || !serials.isEmpty {
            let r = reversed
            DispatchQueue.main.async { self.onDevicesChanged?(r) }
        }
    }
}
