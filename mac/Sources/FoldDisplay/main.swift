import AppKit
import CoreGraphics

final class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private let server = Server()
    private let adb = AdbReverser()
    private var usbDevices = Set<String>()
    private let menu = NSMenu()

    func applicationDidFinishLaunching(_ n: Notification) {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        statusItem.menu = menu

        if !CGPreflightScreenCaptureAccess() { CGRequestScreenCaptureAccess() }
        _ = InputInjector.ensureAccessibility(prompt: true)

        server.onStatus = { [weak self] in self?.refresh() }
        adb.onDevicesChanged = { [weak self] d in
            guard let self, self.usbDevices != d else { return }
            self.usbDevices = d
            self.refresh()
        }
        do { try server.start() } catch {
            NSLog("listen failed: \(error)")
        }
        adb.start()
        refresh()
    }

    private func refresh() {
        let connected = server.session != nil
        if let b = statusItem.button {
            b.image = NSImage(systemSymbolName: connected ? "rectangle.on.rectangle.fill" : "rectangle.on.rectangle",
                              accessibilityDescription: "FoldDisplay")
        }
        menu.removeAllItems()
        func info(_ s: String) {
            let i = NSMenuItem(title: s, action: nil, keyEquivalent: "")
            i.isEnabled = false
            menu.addItem(i)
        }
        if let s = server.session {
            info("接続中: \(s.isUSB ? "USB" : "Wi-Fi (\(s.label))")")
            if let (w, h) = s.resolution { info("解像度: \(w)×\(h)") }
            menu.addItem(NSMenuItem(title: "切断", action: #selector(disconnect), keyEquivalent: ""))
        } else {
            info("待機中（Android アプリから接続してください）")
        }
        info(adb.adbPath == nil ? "USB: adb が見つかりません" :
             usbDevices.isEmpty ? "USB: 端末なし" : "USB: \(usbDevices.count)台 準備OK")
        if !CGPreflightScreenCaptureAccess() { info("⚠︎ 画面収録の許可が必要です") }
        if !InputInjector.ensureAccessibility(prompt: false) { info("⚠︎ タッチ操作にはアクセシビリティの許可が必要です") }
        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: "ディスプレイ設定を開く…", action: #selector(openDisplays), keyEquivalent: ""))
        menu.addItem(NSMenuItem(title: "プライバシー設定を開く…", action: #selector(openPrivacy), keyEquivalent: ""))
        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: "終了", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
        for i in menu.items where i.action != nil && i.action != #selector(NSApplication.terminate(_:)) { i.target = self }
    }

    @objc private func disconnect() { server.session?.close() }

    @objc private func openDisplays() {
        NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.Displays-Settings.extension")!)
    }

    @objc private func openPrivacy() {
        NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture")!)
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
