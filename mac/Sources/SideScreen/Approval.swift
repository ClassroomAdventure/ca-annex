import AppKit
import SwiftUI

/// Wi-Fi で接続を許可した端末を覚えておく（USB はケーブルを挿した時点で本人とみなす）。
enum TrustStore {
    private static let key = "trustedDevices"

    static var all: [String: String] {
        UserDefaults.standard.dictionary(forKey: key) as? [String: String] ?? [:]
    }

    static func isTrusted(_ id: String) -> Bool { !id.isEmpty && all[id] != nil }

    static func trust(_ id: String, name: String) {
        var d = all
        d[id] = name
        UserDefaults.standard.set(d, forKey: key)
    }

    static func reset() { UserDefaults.standard.removeObject(forKey: key) }
}

/// 初めての端末が Wi-Fi でつないできたときに出す確認。メニューバー常駐アプリなので独立した小さなパネルにする。
final class ApprovalPanel: NSObject, NSWindowDelegate {
    private var panel: NSPanel?
    private var decided = false
    private let completion: (Bool) -> Void

    init(deviceName: String, address: String, completion: @escaping (Bool) -> Void) {
        self.completion = completion
        super.init()
        let p = NSPanel(contentRect: NSRect(x: 0, y: 0, width: 380, height: 200),
                        styleMask: [.titled, .closable, .fullSizeContentView], backing: .buffered, defer: false)
        p.titlebarAppearsTransparent = true
        p.titleVisibility = .hidden
        p.level = .floating
        p.isReleasedWhenClosed = false
        p.hidesOnDeactivate = false
        p.delegate = self
        p.contentView = NSHostingView(rootView: ApprovalView(
            deviceName: deviceName, address: address,
            allow: { [weak self] in self?.decide(true) },
            deny: { [weak self] in self?.decide(false) }))
        p.center()
        panel = p
    }

    func show() {
        NSApp.activate(ignoringOtherApps: true)
        panel?.makeKeyAndOrderFront(nil)
        NSSound.beep()
    }

    func dismiss() {
        decided = true
        panel?.close()
    }

    private func decide(_ ok: Bool) {
        guard !decided else { return }
        decided = true
        panel?.close()
        completion(ok)
    }

    func windowWillClose(_ notification: Notification) {
        if !decided { decided = true; completion(false) }
    }
}

private struct ApprovalView: View {
    let deviceName: String
    let address: String
    let allow: () -> Void
    let deny: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Image(nsImage: NSApp.applicationIconImage).resizable().frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text(String(format: L("approve.title"), deviceName)).font(.system(size: 14, weight: .semibold))
                        .fixedSize(horizontal: false, vertical: true)
                    Text(String(format: L("approve.address"), address)).font(.system(size: 12)).foregroundStyle(.secondary)
                }
            }
            Text(L("approve.body")).font(.system(size: 12)).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                Spacer()
                Button(L("approve.deny"), action: deny).keyboardShortcut(.cancelAction).controlSize(.large)
                Button(L("approve.allow"), action: allow).keyboardShortcut(.defaultAction).controlSize(.large)
            }
        }
        .padding(.horizontal, 22)
        .padding(.top, 30)
        .padding(.bottom, 18)
        .frame(width: 380)
    }
}

/// 開発用プレビュー（OnboardingRenderer から使う）
struct ApprovalPreview: View {
    var body: some View {
        ApprovalView(deviceName: "Galaxy Z Fold8", address: "192.168.1.33", allow: {}, deny: {})
    }
}
