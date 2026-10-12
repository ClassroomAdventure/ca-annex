import AppKit
import ApplicationServices
import CoreImage.CIFilterBuiltins
import ServiceManagement
import SwiftUI

/// 初回に開くセットアップ画面。メニューの「セットアップガイド…」からいつでも開き直せる。
final class OnboardingController: NSObject, NSWindowDelegate {
    private var window: NSWindow?
    private let model: OnboardingModel

    init(adbPath: @escaping () -> String?) {
        model = OnboardingModel(adbPath: adbPath)
    }

    var isVisible: Bool { window?.isVisible ?? false }

    static var hasCompleted: Bool {
        get { UserDefaults.standard.bool(forKey: "onboardingCompleted") }
        set { UserDefaults.standard.set(newValue, forKey: "onboardingCompleted") }
    }

    func show(startAt step: OnboardingModel.Step? = nil) {
        if let step { model.step = step }
        if window == nil {
            let w = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 760, height: 580),
                             styleMask: [.titled, .closable, .fullSizeContentView], backing: .buffered, defer: false)
            w.titlebarAppearsTransparent = true
            w.titleVisibility = .hidden
            w.isMovableByWindowBackground = true
            w.isReleasedWhenClosed = false
            w.delegate = self
            w.contentView = NSHostingView(rootView: OnboardingView(model: model, close: { [weak self] in self?.finish() }))
            w.center()
            window = w
        }
        model.refresh()
        model.startPolling()
        NSApp.activate(ignoringOtherApps: true)
        window?.makeKeyAndOrderFront(nil)
    }

    private func finish() {
        Self.hasCompleted = true
        window?.close()
    }

    func windowWillClose(_ notification: Notification) {
        model.stopPolling()
    }
}

final class OnboardingModel: ObservableObject {
    enum Step: Int, CaseIterable { case welcome, screen, accessibility, android, connect, done }

    @Published var step: Step = .welcome
    @Published var screenGranted = false
    @Published var accessibilityGranted = false
    @Published var adbFound = false
    @Published var screenRequested = false
    @Published var launchAtLogin = false
    @Published var copied = false

    let adbPath: () -> String?
    private var timer: Timer?

    init(adbPath: @escaping () -> String?) {
        self.adbPath = adbPath
        refresh()
    }

    func refresh() {
        screenGranted = CGPreflightScreenCaptureAccess()
        accessibilityGranted = AXIsProcessTrusted()
        adbFound = adbPath() != nil
        launchAtLogin = SMAppService.mainApp.status == .enabled
    }

    /// 許可はシステム設定で行われるので、戻ってきたら気づけるようにときどき確認する
    func startPolling() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.refresh() }
    }

    func stopPolling() { timer?.invalidate(); timer = nil }

    func next() { if let n = Step(rawValue: step.rawValue + 1) { step = n } }
    func back() { if let n = Step(rawValue: step.rawValue - 1) { step = n } }

    func requestScreen() {
        screenRequested = true
        if !CGRequestScreenCaptureAccess() { openPrivacy("Privacy_ScreenCapture") }
    }

    func requestAccessibility() {
        let opts = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
        if !AXIsProcessTrustedWithOptions(opts) { openPrivacy("Privacy_Accessibility") }
    }

    func openPrivacy(_ anchor: String) {
        NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.preference.security?\(anchor)")!)
    }

    /// 画面収録の許可は、アプリを開き直すまで反映されない
    func relaunch() {
        let cfg = NSWorkspace.OpenConfiguration()
        cfg.createsNewApplicationInstance = true
        NSWorkspace.shared.openApplication(at: Bundle.main.bundleURL, configuration: cfg) { _, _ in
            DispatchQueue.main.async { NSApp.terminate(nil) }
        }
    }

    func copy(_ s: String) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(s, forType: .string)
        copied = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { self.copied = false }
    }

    func setLaunchAtLogin(_ on: Bool) {
        do {
            if on { try SMAppService.mainApp.register() } else { try SMAppService.mainApp.unregister() }
        } catch {
            NSLog("launch at login: \(error)")
        }
        refresh()
    }
}

enum Links {
    static var site: String {
        let ja = Locale.preferredLanguages.first?.hasPrefix("ja") ?? false
        return "https://classroomadventure.github.io/ca-annex/" + (ja ? "ja/" : "")
    }
    static let apk = "https://github.com/ClassroomAdventure/ca-annex/releases/latest/download/Annex-android.apk"
    static let platformTools = "https://developer.android.com/tools/releases/platform-tools"
    static let brewCommand = "brew install android-platform-tools"
}

// MARK: - Views

private let accent = Color(red: 0.23, green: 0.43, blue: 0.98)

struct OnboardingView: View {
    @ObservedObject var model: OnboardingModel
    let close: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Sidebar(model: model)
                .frame(width: 220)
                .background(.thinMaterial)
            Divider()
            VStack(alignment: .leading, spacing: 0) {
                ScrollView {
                    content
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 40)
                        .padding(.top, 44)
                        .padding(.bottom, 20)
                }
                Divider()
                footer
                    .padding(.horizontal, 24)
                    .padding(.vertical, 14)
            }
        }
        .frame(width: 760, height: 580)
        .tint(accent)
    }

    @ViewBuilder private var content: some View {
        switch model.step {
        case .welcome: WelcomeStep()
        case .screen: ScreenStep(model: model)
        case .accessibility: AccessibilityStep(model: model)
        case .android: AndroidStep(model: model)
        case .connect: ConnectStep(model: model)
        case .done: DoneStep(model: model)
        }
    }

    private var footer: some View {
        HStack {
            if model.step != .welcome {
                Button(L("onb.back")) { model.back() }
            }
            Spacer()
            if model.step == .done {
                Button(L("onb.finish")) { close() }
                    .keyboardShortcut(.defaultAction)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
            } else {
                Button(model.step == .welcome ? L("onb.start") : L("onb.continue")) { model.next() }
                    .keyboardShortcut(.defaultAction)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
            }
        }
    }
}

private struct Sidebar: View {
    @ObservedObject var model: OnboardingModel

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 10) {
                Image(nsImage: NSApp.applicationIconImage).resizable().frame(width: 32, height: 32)
                Text("Annex").font(.system(size: 15, weight: .semibold))
            }
            .padding(.bottom, 20)
            .padding(.leading, 6)
            ForEach(OnboardingModel.Step.allCases, id: \.self) { s in
                row(s)
            }
            Spacer()
        }
        .padding(.top, 48)
        .padding(.horizontal, 14)
    }

    private func row(_ s: OnboardingModel.Step) -> some View {
        let current = s == model.step
        let done = isDone(s)
        return Button { model.step = s } label: {
            HStack(spacing: 10) {
                ZStack {
                    Circle().fill(current ? accent : Color.secondary.opacity(0.15)).frame(width: 22, height: 22)
                    if done && !current {
                        Image(systemName: "checkmark").font(.system(size: 10, weight: .bold)).foregroundStyle(accent)
                    } else {
                        Text("\(s.rawValue + 1)").font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(current ? .white : .secondary)
                    }
                }
                Text(title(s)).font(.system(size: 13, weight: current ? .semibold : .regular))
                    .foregroundStyle(current ? .primary : .secondary)
                Spacer()
            }
            .padding(.vertical, 7)
            .padding(.horizontal, 8)
            .background(current ? Color.secondary.opacity(0.12) : .clear, in: RoundedRectangle(cornerRadius: 8))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func isDone(_ s: OnboardingModel.Step) -> Bool {
        switch s {
        case .screen: return model.screenGranted
        case .accessibility: return model.accessibilityGranted
        default: return s.rawValue < model.step.rawValue
        }
    }

    private func title(_ s: OnboardingModel.Step) -> String {
        switch s {
        case .welcome: return L("onb.side.welcome")
        case .screen: return L("onb.side.screen")
        case .accessibility: return L("onb.side.accessibility")
        case .android: return L("onb.side.android")
        case .connect: return L("onb.side.connect")
        case .done: return L("onb.side.done")
        }
    }
}

private struct StepHeader: View {
    let symbol: String
    let title: String
    let body_: String

    init(symbol: String, title: String, body: String) {
        self.symbol = symbol; self.title = title; self.body_ = body
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 34, weight: .regular))
                .foregroundStyle(accent)
                .frame(height: 40)
            Text(title).font(.system(size: 24, weight: .semibold))
            Text(body_).font(.system(size: 14)).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: 440, alignment: .leading)
        }
    }
}

/// 許可の状態を1行で（済 / まだ）
private struct StatusLine: View {
    let ok: Bool
    let okText: String
    let ngText: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: ok ? "checkmark.circle.fill" : "circle.dashed")
                .foregroundStyle(ok ? Color.green : Color.secondary)
            Text(ok ? okText : ngText).font(.system(size: 13, weight: .medium))
                .foregroundStyle(ok ? .primary : .secondary)
        }
        .padding(.vertical, 8).padding(.horizontal, 12)
        .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
    }
}

private struct WelcomeStep: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Image(nsImage: NSApp.applicationIconImage).resizable().frame(width: 96, height: 96)
            Text(L("onb.welcome.title")).font(.system(size: 28, weight: .semibold))
            Text(L("onb.welcome.body")).font(.system(size: 14)).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true).frame(maxWidth: 460, alignment: .leading)
            VStack(alignment: .leading, spacing: 10) {
                Bullet(symbol: "lock.shield", text: L("onb.welcome.b1"))
                Bullet(symbol: "iphone.gen3", text: L("onb.welcome.b2"))
                Bullet(symbol: "cable.connector", text: L("onb.welcome.b3"))
            }
            .padding(.top, 6)
        }
    }
}

private struct Bullet: View {
    let symbol: String
    let text: String
    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Image(systemName: symbol).foregroundStyle(accent).frame(width: 20)
            Text(text).font(.system(size: 13)).fixedSize(horizontal: false, vertical: true)
        }
    }
}

private struct ScreenStep: View {
    @ObservedObject var model: OnboardingModel
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            StepHeader(symbol: "rectangle.dashed.badge.record", title: L("onb.screen.title"), body: L("onb.screen.body"))
            StatusLine(ok: model.screenGranted, okText: L("onb.granted"), ngText: L("onb.notGranted"))
            if !model.screenGranted {
                HStack(spacing: 10) {
                    Button(L("onb.screen.allow")) { model.requestScreen() }.controlSize(.large).buttonStyle(.borderedProminent)
                    Button(L("onb.openSettings")) { model.openPrivacy("Privacy_ScreenCapture") }.controlSize(.large)
                }
                if model.screenRequested {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(L("onb.screen.relaunchNote")).font(.system(size: 12)).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true).frame(maxWidth: 440, alignment: .leading)
                        Button(L("onb.relaunch")) { model.relaunch() }
                    }
                }
            }
        }
    }
}

private struct AccessibilityStep: View {
    @ObservedObject var model: OnboardingModel
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            StepHeader(symbol: "hand.tap", title: L("onb.ax.title"), body: L("onb.ax.body"))
            StatusLine(ok: model.accessibilityGranted, okText: L("onb.granted"), ngText: L("onb.notGranted"))
            if !model.accessibilityGranted {
                HStack(spacing: 10) {
                    Button(L("onb.ax.allow")) { model.requestAccessibility() }.controlSize(.large).buttonStyle(.borderedProminent)
                    Button(L("onb.openSettings")) { model.openPrivacy("Privacy_Accessibility") }.controlSize(.large)
                }
                Text(L("onb.ax.note")).font(.system(size: 12)).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true).frame(maxWidth: 440, alignment: .leading)
            }
        }
    }
}

private struct AndroidStep: View {
    @ObservedObject var model: OnboardingModel
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            StepHeader(symbol: "apps.iphone.badge.plus", title: L("onb.android.title"), body: L("onb.android.body"))
            HStack(alignment: .top, spacing: 22) {
                QRCode(text: Links.apk)
                    .frame(width: 148, height: 148)
                    .padding(10)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.secondary.opacity(0.2)))
                VStack(alignment: .leading, spacing: 10) {
                    Text(L("onb.android.scan")).font(.system(size: 13, weight: .medium))
                    Text(L("onb.android.unknownSources")).font(.system(size: 12)).foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(L("onb.android.req")).font(.system(size: 12)).foregroundStyle(.secondary)
                    Button(model.copied ? L("onb.copied") : L("onb.android.copyLink")) { model.copy(Links.apk) }
                }
                .frame(maxWidth: 280, alignment: .leading)
            }
        }
    }
}

private struct ConnectStep: View {
    @ObservedObject var model: OnboardingModel
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            StepHeader(symbol: "point.3.connected.trianglepath.dotted", title: L("onb.connect.title"), body: L("onb.connect.body"))
            VStack(alignment: .leading, spacing: 12) {
                Way(symbol: "wifi", title: L("onb.connect.wifi"), detail: L("onb.connect.wifiBody")) {
                    if let ip = LocalAddress.primaryIPv4() {
                        Text(String(format: L("onb.connect.address"), ip)).font(.system(size: 12)).foregroundStyle(.secondary)
                            .textSelection(.enabled)
                    }
                }
                Way(symbol: "cable.connector", title: L("onb.connect.usb"), detail: L("onb.connect.usbBody")) {
                    StatusLine(ok: model.adbFound, okText: L("onb.adb.found"), ngText: L("onb.adb.missing"))
                    if !model.adbFound {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(L("onb.adb.how")).font(.system(size: 12)).foregroundStyle(.secondary)
                            HStack(spacing: 6) {
                                Text(Links.brewCommand).font(.system(size: 12, design: .monospaced)).textSelection(.enabled)
                                    .fixedSize()
                                    .padding(.horizontal, 8).padding(.vertical, 5)
                                    .background(Color.secondary.opacity(0.1), in: RoundedRectangle(cornerRadius: 6))
                                Button(model.copied ? L("onb.copied") : L("onb.copy")) { model.copy(Links.brewCommand) }
                            }
                            Link(L("onb.adb.noBrew"), destination: URL(string: Links.platformTools)!).font(.system(size: 12))
                        }
                    }
                }
            }
        }
    }
}

private struct Way<Extra: View>: View {
    let symbol: String
    let title: String
    let detail: String
    @ViewBuilder let extra: () -> Extra

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(title, systemImage: symbol).font(.system(size: 14, weight: .semibold))
            Text(detail).font(.system(size: 12)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            extra()
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .frame(maxWidth: 520, alignment: .topLeading)
        .background(Color.secondary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct DoneStep: View {
    @ObservedObject var model: OnboardingModel
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            StepHeader(symbol: "checkmark.seal", title: L("onb.done.title"), body: L("onb.done.body"))
            if !model.screenGranted || !model.accessibilityGranted {
                StatusLine(ok: false, okText: "", ngText: L("onb.done.missing"))
            }
            HStack(spacing: 8) {
                Image(systemName: "menubar.rectangle").foregroundStyle(accent)
                Text(L("onb.done.menubar")).font(.system(size: 13))
            }
            Toggle(L("onb.done.login"), isOn: Binding(get: { model.launchAtLogin }, set: { model.setLaunchAtLogin($0) }))
                .toggleStyle(.switch)
        }
    }
}

/// CoreImage で作る QR コード（拡大してもにじまないよう補間なしで描く）
private struct QRCode: View {
    let text: String
    var body: some View {
        if let img = Self.make(text) {
            Image(nsImage: img).interpolation(.none).resizable()
        } else {
            Color.clear
        }
    }

    static func make(_ text: String) -> NSImage? {
        let f = CIFilter.qrCodeGenerator()
        f.message = Data(text.utf8)
        f.correctionLevel = "M"
        guard let out = f.outputImage else { return nil }
        let rep = NSCIImageRep(ciImage: out)
        let img = NSImage(size: rep.size)
        img.addRepresentation(rep)
        return img
    }
}

/// この Mac の LAN 側 IPv4 アドレス（手動接続の案内用）
enum LocalAddress {
    static func primaryIPv4() -> String? {
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return nil }
        defer { freeifaddrs(ifaddr) }
        var candidates: [(String, String)] = []
        for p in sequence(first: first, next: { $0.pointee.ifa_next }) {
            let a = p.pointee
            guard let sa = a.ifa_addr, sa.pointee.sa_family == UInt8(AF_INET),
                  (a.ifa_flags & UInt32(IFF_UP)) != 0, (a.ifa_flags & UInt32(IFF_LOOPBACK)) == 0 else { continue }
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            getnameinfo(sa, socklen_t(sa.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST)
            let name = String(cString: a.ifa_name)
            let ip = String(cString: host)
            if ip.hasPrefix("169.254.") { continue }
            candidates.append((name, ip))
        }
        // Wi-Fi / 有線（en*）を優先
        return (candidates.first { $0.0.hasPrefix("en") } ?? candidates.first)?.1
    }
}

/// 開発用: `Annex --render-onboarding <dir>` で各ステップを PNG に書き出す（見た目の確認用）
enum OnboardingRenderer {
    @MainActor static func run(to dir: String) {
        let model = OnboardingModel(adbPath: { nil })
        // 初めての人が見る状態（どれもまだ許可されていない）で描く
        model.screenGranted = false
        model.accessibilityGranted = false
        for s in OnboardingModel.Step.allCases {
            model.step = s
            snapshot(OnboardingView(model: model, close: {}), size: NSSize(width: 760, height: 580), to: "\(dir)/step\(s.rawValue).png")
        }
        snapshot(ApprovalPreview(), size: NSSize(width: 380, height: 200), to: "\(dir)/approval.png")
    }

    /// 画面外に置いた本物のウィンドウを描いて PNG にする（ボタンやスクロールも描かれる）
    @MainActor private static func snapshot<V: View>(_ view: V, size: NSSize, to path: String) {
        let w = NSWindow(contentRect: NSRect(origin: NSPoint(x: -10000, y: -10000), size: size),
                         styleMask: [.titled, .fullSizeContentView], backing: .buffered, defer: false)
        w.titlebarAppearsTransparent = true
        w.titleVisibility = .hidden
        let host = NSHostingView(rootView: view)
        w.contentView = host
        // アクティブなウィンドウとして描く（既定のボタンが実物と同じく青になる）
        NSApp.setActivationPolicy(.accessory)
        NSApp.activate(ignoringOtherApps: true)
        w.makeKeyAndOrderFront(nil)
        RunLoop.current.run(until: Date().addingTimeInterval(0.6))
        guard let content = w.contentView?.superview ?? w.contentView,
              let rep = content.bitmapImageRepForCachingDisplay(in: content.bounds) else { return }
        content.cacheDisplay(in: content.bounds, to: rep)
        try? rep.representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: path))
        w.orderOut(nil)
    }
}
