import ApplicationServices
import CoreGraphics
import Foundation

/// Android のタッチ操作を Mac のマウス・スクロールイベントとして注入する。
final class InputInjector {
    private let displayID: () -> CGDirectDisplayID?
    private let source = CGEventSource(stateID: .hidSystemState)
    private var leftDown = false
    private var lastClickTime: TimeInterval = 0
    private var lastClickPoint = CGPoint.zero
    private var clickCount = 1

    init(displayID: @escaping () -> CGDirectDisplayID?) {
        self.displayID = displayID
    }

    static func ensureAccessibility(prompt: Bool) -> Bool {
        let opts = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: prompt] as CFDictionary
        return AXIsProcessTrustedWithOptions(opts)
    }

    private func point(_ x: Float, _ y: Float) -> CGPoint? {
        guard let id = displayID() else { return nil }
        let b = CGDisplayBounds(id)
        let cx = min(max(CGFloat(x), 0), 0.9999)
        let cy = min(max(CGFloat(y), 0), 0.9999)
        return CGPoint(x: b.minX + cx * b.width, y: b.minY + cy * b.height)
    }

    private func post(_ type: CGEventType, _ p: CGPoint, button: CGMouseButton = .left, clicks: Int = 1) {
        guard let e = CGEvent(mouseEventSource: source, mouseType: type, mouseCursorPosition: p, mouseButton: button) else { return }
        e.setIntegerValueField(.mouseEventClickState, value: Int64(clicks))
        e.post(tap: .cghidEventTap)
    }

    func mouse(kind: Proto.MouseKind, x: Float, y: Float) {
        guard let p = point(x, y) else { return }
        switch kind {
        case .move:
            post(leftDown ? .leftMouseDragged : .mouseMoved, p)
        case .leftDrag:
            post(.leftMouseDragged, p)
        case .leftDown:
            let now = ProcessInfo.processInfo.systemUptime
            if now - lastClickTime < 0.4 && hypot(p.x - lastClickPoint.x, p.y - lastClickPoint.y) < 12 {
                clickCount += 1
            } else {
                clickCount = 1
            }
            lastClickTime = now
            lastClickPoint = p
            post(.mouseMoved, p)
            post(.leftMouseDown, p, clicks: clickCount)
            leftDown = true
        case .leftUp:
            post(.leftMouseUp, p, clicks: clickCount)
            leftDown = false
        case .rightClick:
            post(.mouseMoved, p)
            post(.rightMouseDown, p, button: .right)
            post(.rightMouseUp, p, button: .right)
        }
    }

    /// dx/dy は正規化座標系での指の移動量（0..1）
    func scroll(x: Float, y: Float, dx: Float, dy: Float) {
        guard let p = point(x, y), let id = displayID() else { return }
        let b = CGDisplayBounds(id)
        post(.mouseMoved, p)
        // 指の動きにコンテンツが追従する（タッチと同じ向き）
        let wy = Int32((CGFloat(dy) * b.height).rounded())
        let wx = Int32((CGFloat(dx) * b.width).rounded())
        if wx == 0 && wy == 0 { return }
        guard let e = CGEvent(scrollWheelEvent2Source: source, units: .pixel, wheelCount: 2, wheel1: wy, wheel2: wx, wheel3: 0) else { return }
        e.location = p
        e.post(tap: .cghidEventTap)
    }

    func releaseAll() {
        if leftDown, let id = displayID() {
            let b = CGDisplayBounds(id)
            post(.leftMouseUp, CGPoint(x: b.midX, y: b.midY))
            leftDown = false
        }
    }
}
