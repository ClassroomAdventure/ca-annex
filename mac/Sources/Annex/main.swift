import AppKit
import CoreGraphics
import ServiceManagement

/// en.lproj / ja.lproj の Localizable.strings から引く
func L(_ key: String) -> String { NSLocalizedString(key, comment: "") }

final class AppDelegate: NSObject, NSApplicationDelegate, NSMenuDelegate {
    private var statusItem: NSStatusItem!
    private let server = Server()
    private let adb = AdbReverser()
    private var usbDevices = Set<String>()
    private let menu = NSMenu()
    private lazy var onboarding = OnboardingController(adbPath: { [weak self] in self?.adb.adbPath })
    private var lastPermissionNudge: Date?

    func applicationDidFinishLaunching(_ n: Notification) {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        statusItem.menu = menu
        menu.delegate = self

        server.onStatus = { [weak self] in self?.refresh() }
        // 許可が無い状態で端末がつなぎに来たら、セットアップガイドの「画面収録」を開く（つなぎ直しのたびに出さない）
        server.onNeedsScreenRecording = { [weak self] in
            guard let self else { return }
            let now = Date()
            if let last = self.lastPermissionNudge, now.timeIntervalSince(last) < 60 { return }
            self.lastPermissionNudge = now
            if !self.onboarding.isVisible { self.onboarding.show(startAt: .screen) }
        }
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

        // 初回、または許可が外れているときはセットアップ画面を出す
        if !OnboardingController.hasCompleted {
            onboarding.show(startAt: .welcome)
        } else if !CGPreflightScreenCaptureAccess() {
            onboarding.show(startAt: .screen)
        } else if !InputInjector.ensureAccessibility(prompt: false) {
            onboarding.show(startAt: .accessibility)
        }
    }

    /// Dock に出ないアプリなので、Finder などから開き直されたらセットアップ画面を出す
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
        if !flag { onboarding.show() }
        return true
    }

    private func refresh() {
        let connected = server.session?.resolution != nil
        if let b = statusItem.button {
            b.image = NSImage(systemSymbolName: connected ? "rectangle.on.rectangle.fill" : "rectangle.on.rectangle",
                              accessibilityDescription: "Annex")
        }
        menu.removeAllItems()
        func info(_ s: String) {
            let i = NSMenuItem(title: s, action: nil, keyEquivalent: "")
            i.isEnabled = false
            menu.addItem(i)
        }
        func item(_ title: String, _ action: Selector, key: String = "") -> NSMenuItem {
            let i = NSMenuItem(title: title, action: action, keyEquivalent: key)
            i.target = self
            menu.addItem(i)
            return i
        }

        if let s = server.session, let pending = s.pendingName {
            info(String(format: L("menu.pending"), pending))
        } else if let s = server.session, connected {
            info(String(format: L("menu.connected"), s.isUSB ? "USB" : "Wi-Fi (\(s.label))"))
            if let (w, h) = s.resolution { info(String(format: L("menu.resolution"), w, h)) }
            _ = item(L("menu.disconnect"), #selector(disconnect))
        } else {
            info(L("menu.waiting"))
        }
        if !VirtualDisplay.isSupported {
            item(L("menu.unsupported"), #selector(openReleases)).toolTip = L("menu.unsupported.tip")
        }
        // Wi-Fi で使っているときだけ、AWDL（カクつきの主な原因）の止め方を出す
        if let s = server.session, !s.isUSB, connected {
            if AWDL.isActive { item(L("menu.awdl.off"), #selector(awdlOff)).toolTip = L("menu.awdl.tip") }
        }
        if awdlStoppedByUs, !AWDL.isActive { _ = item(L("menu.awdl.on"), #selector(awdlOn)) }
        menu.addItem(.separator())
        if let ip = LocalAddress.primaryIPv4() {
            item(String(format: L("menu.address"), ip), #selector(copyAddress)).toolTip = L("menu.address.tip")
        }
        info(adb.adbPath == nil ? L("menu.usb.noAdb") :
             usbDevices.isEmpty ? L("menu.usb.none") : String(format: L("menu.usb.ready"), usbDevices.count))
        if !CGPreflightScreenCaptureAccess() { item(L("menu.needScreenRecording"), #selector(fixScreen)) }
        if !InputInjector.ensureAccessibility(prompt: false) { item(L("menu.needAccessibility"), #selector(fixAccessibility)) }
        menu.addItem(.separator())
        _ = item(L("menu.setup"), #selector(openSetup))
        _ = item(L("menu.openDisplays"), #selector(openDisplays))
        let login = item(L("menu.launchAtLogin"), #selector(toggleLogin))
        login.state = SMAppService.mainApp.status == .enabled ? .on : .off
        if !TrustStore.all.isEmpty {
            _ = item(String(format: L("menu.forget"), TrustStore.all.count), #selector(forgetDevices))
        }
        menu.addItem(.separator())
        _ = item(L("menu.about"), #selector(about))
        _ = item(L("menu.quit"), #selector(NSApplication.terminate(_:)), key: "q").target = NSApp
    }

    /// 開くたびに作り直す（AWDL や許可の状態はメニューの外で変わる）
    func menuNeedsUpdate(_ menu: NSMenu) { refresh() }

    @objc private func disconnect() { server.session?.close() }

    private var awdlStoppedByUs = false

    @objc private func awdlOff() {
        if AWDL.set(up: false) { awdlStoppedByUs = true }
        refresh()
    }

    @objc private func awdlOn() {
        if AWDL.set(up: true) { awdlStoppedByUs = false }
        refresh()
    }

    @objc private func openReleases() {
        NSWorkspace.shared.open(URL(string: "https://github.com/ClassroomAdventure/ca-annex/releases/latest")!)
    }

    @objc private func copyAddress() {
        guard let ip = LocalAddress.primaryIPv4() else { return }
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(ip, forType: .string)
    }

    @objc private func openSetup() { onboarding.show(startAt: .welcome) }
    @objc private func fixScreen() { onboarding.show(startAt: .screen) }
    @objc private func fixAccessibility() { onboarding.show(startAt: .accessibility) }

    @objc private func openDisplays() {
        NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.Displays-Settings.extension")!)
    }

    @objc private func toggleLogin() {
        do {
            if SMAppService.mainApp.status == .enabled { try SMAppService.mainApp.unregister() } else { try SMAppService.mainApp.register() }
        } catch { NSLog("launch at login: \(error)") }
        refresh()
    }

    @objc private func forgetDevices() {
        TrustStore.reset()
        refresh()
    }

    @objc private func about() {
        NSApp.activate(ignoringOtherApps: true)
        NSApp.orderFrontStandardAboutPanel(options: [
            .credits: NSAttributedString(string: L("about.credits"), attributes: [.font: NSFont.systemFont(ofSize: 11)]),
        ])
    }
}

if let i = CommandLine.arguments.firstIndex(of: "--render-onboarding"), CommandLine.arguments.count > i + 1 {
    _ = NSApplication.shared
    MainActor.assumeIsolated { OnboardingRenderer.run(to: CommandLine.arguments[i + 1]) }
    exit(0)
}

if let i = CommandLine.arguments.firstIndex(of: "--bench-encoder") {
    let rest = CommandLine.arguments.dropFirst(i + 1).compactMap { Int($0) }
    EncoderBench.run(fps: rest.first ?? 120, seconds: rest.dropFirst().first ?? 4)
    exit(0)
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
