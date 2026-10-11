import CoreMedia
import Foundation
import ScreenCaptureKit

/// 指定ディスプレイを ScreenCaptureKit で取り込み、フレームを渡す。
final class DisplayCapture: NSObject, SCStreamOutput, SCStreamDelegate {
    private var stream: SCStream?
    private let queue: DispatchQueue
    private let onFrame: (CVPixelBuffer, CMTime) -> Void
    var onStop: ((Error?) -> Void)?

    init(queue: DispatchQueue, onFrame: @escaping (CVPixelBuffer, CMTime) -> Void) {
        self.queue = queue
        self.onFrame = onFrame
    }

    func start(displayID: CGDirectDisplayID, width: Int, height: Int, fps: Int) async throws {
        // 作った直後の仮想ディスプレイは SCShareableContent に出てくるまで少しかかる
        var target: SCDisplay?
        for _ in 0..<40 {
            let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: false)
            target = content.displays.first { $0.displayID == displayID }
            if target != nil { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        guard let target else { throw NSError(domain: "SideScreen", code: 1, userInfo: [NSLocalizedDescriptionKey: "仮想ディスプレイが見つかりません"]) }

        let filter = SCContentFilter(display: target, excludingWindows: [])
        let cfg = SCStreamConfiguration()
        cfg.width = width
        cfg.height = height
        cfg.minimumFrameInterval = CMTime(value: 1, timescale: CMTimeScale(fps))
        cfg.pixelFormat = kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
        cfg.showsCursor = false // カーソルは Android 側で描く
        cfg.queueDepth = 4
        cfg.scalesToFit = true
        cfg.capturesAudio = false
        cfg.colorSpaceName = CGColorSpace.sRGB

        let s = SCStream(filter: filter, configuration: cfg, delegate: self)
        try s.addStreamOutput(self, type: .screen, sampleHandlerQueue: queue)
        try await s.startCapture()
        stream = s
    }

    func stop() async {
        guard let s = stream else { return }
        stream = nil
        try? await s.stopCapture()
    }

    func stream(_ stream: SCStream, didOutputSampleBuffer sampleBuffer: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .screen, sampleBuffer.isValid else { return }
        if let atts = CMSampleBufferGetSampleAttachmentsArray(sampleBuffer, createIfNecessary: false) as? [[SCStreamFrameInfo: Any]],
           let raw = atts.first?[.status] as? Int, let status = SCFrameStatus(rawValue: raw), status != .complete {
            return
        }
        guard let pb = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        onFrame(pb, CMSampleBufferGetPresentationTimeStamp(sampleBuffer))
    }

    func stream(_ stream: SCStream, didStopWithError error: Error) {
        NSLog("SCStream stopped: \(error)")
        onStop?(error)
    }
}
