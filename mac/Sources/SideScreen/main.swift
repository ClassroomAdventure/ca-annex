import AppKit
import CoreGraphics

/// en.lproj / ja.lproj の Localizable.strings から引く
func L(_ key: String) -> String { NSLocalizedString(key, comment: "") }

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
                              accessibilityDescription: "SideScreen")
        }
        menu.removeAllItems()
        func info(_ s: String) {
            let i = NSMenuItem(title: s, action: nil, keyEquivalent: "")
            i.isEnabled = false
            menu.addItem(i)
        }
        if let s = server.session {
            info(String(format: L("menu.connected"), s.isUSB ? "USB" : "Wi-Fi (\(s.label))"))
            if let (w, h) = s.resolution { info(String(format: L("menu.resolution"), w, h)) }
            menu.addItem(NSMenuItem(title: L("menu.disconnect"), action: #selector(disconnect), keyEquivalent: ""))
        } else {
            info(L("menu.waiting"))
        }
        info(adb.adbPath == nil ? L("menu.usb.noAdb") :
             usbDevices.isEmpty ? L("menu.usb.none") : String(format: L("menu.usb.ready"), usbDevices.count))
        if !CGPreflightScreenCaptureAccess() { info(L("menu.needScreenRecording")) }
        if !InputInjector.ensureAccessibility(prompt: false) { info(L("menu.needAccessibility")) }
        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: L("menu.openDisplays"), action: #selector(openDisplays), keyEquivalent: ""))
        menu.addItem(NSMenuItem(title: L("menu.openPrivacy"), action: #selector(openPrivacy), keyEquivalent: ""))
        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: L("menu.quit"), action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
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
