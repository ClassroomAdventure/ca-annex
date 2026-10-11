import Foundation
import Network

/// TCP サーバ。Wi-Fi は Bonjour で広告、USB は adb reverse 経由で 127.0.0.1 に届く。
final class Server {
    private var listener: NWListener?
    let displays = DisplayManager()
    private(set) var session: ClientSession?
    var onStatus: (() -> Void)?

    func start() throws {
        let tcp = NWProtocolTCP.Options()
        tcp.noDelay = true
        let params = NWParameters(tls: nil, tcp: tcp)
        params.allowLocalEndpointReuse = true
        params.includePeerToPeer = true
        let l = try NWListener(using: params, on: NWEndpoint.Port(rawValue: Proto.port)!)
        l.service = NWListener.Service(name: Host.current().localizedName ?? "Mac", type: Proto.bonjourType)
        l.stateUpdateHandler = { st in NSLog("listener: \(st)") }
        l.newConnectionHandler = { [weak self] c in self?.accept(c) }
        l.start(queue: .main)
        listener = l
    }

    private func accept(_ c: NWConnection) {
        // 1台ずつ。新しい接続が来たら古いほうを切る
        if let old = session {
            old.onClose = nil
            old.close()
        }
        let s = ClientSession(conn: c, displays: displays)
        s.onClose = { [weak self] s in
            guard let self, self.session === s else { return }
            self.session = nil
            self.displays.scheduleRelease()
            self.onStatus?()
        }
        s.onStatus = { [weak self] in self?.onStatus?() }
        session = s
        s.start()
        onStatus?()
    }
}
