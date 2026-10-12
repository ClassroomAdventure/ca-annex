import Foundation

/// AWDL（AirDrop・ユニバーサルコントロール・Sidecar などが使う Mac の無線）。
/// 動いていると Wi-Fi のチャンネルを定期的に切り替えるため、映像の届く間隔が数十〜100ms 跳ねる。
/// 実測（Wi-Fi 7 / 6 の良好な回線）: 通信の遅延 中央値 16ms・上位10% 66ms・最大 106ms → 停止後 4.0 / 4.6 / 5.7ms。
/// 止めるには管理者権限が要るので、ユーザーがメニューから選んだときだけ macOS の認証ダイアログを出して止める。
/// Mac を再起動すると元に戻る（AirDrop などを使うと macOS が再び有効にすることもある）。
enum AWDL {
    static var isActive: Bool {
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/sbin/ifconfig")
        p.arguments = ["awdl0"]
        let pipe = Pipe()
        p.standardOutput = pipe
        p.standardError = FileHandle.nullDevice
        guard (try? p.run()) != nil else { return false }
        let out = String(decoding: pipe.fileHandleForReading.readDataToEndOfFile(), as: UTF8.self)
        p.waitUntilExit()
        return out.contains("status: active")
    }

    /// up = false で止める、true で再開。ユーザーがキャンセルしたら false
    @discardableResult
    static func set(up: Bool) -> Bool {
        let cmd = "/sbin/ifconfig awdl0 " + (up ? "up" : "down")
        let prompt = (up ? L("awdl.prompt.up") : L("awdl.prompt.down")).replacingOccurrences(of: "\"", with: "'")
        let src = "do shell script \"\(cmd)\" with prompt \"\(prompt)\" with administrator privileges"
        var err: NSDictionary?
        NSAppleScript(source: src)?.executeAndReturnError(&err)
        if let err { NSLog("awdl \(up ? "up" : "down") failed: \(err)") }
        return err == nil
    }
}
