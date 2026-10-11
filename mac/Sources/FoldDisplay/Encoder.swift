import CoreMedia
import Foundation
import VideoToolbox

/// VideoToolbox によるリアルタイム H.264 エンコーダ。出力は Annex-B。
final class H264Encoder {
    typealias Output = (_ data: Data, _ flags: UInt8, _ ptsMicros: UInt64) -> Void

    private var session: VTCompressionSession?
    private let output: Output
    let width: Int
    let height: Int
    private var forceKey = true

    init?(width: Int, height: Int, fps: Int, bitrateKbps: Int, output: @escaping Output) {
        self.width = width
        self.height = height
        self.output = output

        let spec: [CFString: Any] = [
            kVTVideoEncoderSpecification_EnableLowLatencyRateControl: true,
            kVTVideoEncoderSpecification_RequireHardwareAcceleratedVideoEncoder: true,
        ]
        var s: VTCompressionSession?
        let st = VTCompressionSessionCreate(
            allocator: nil, width: Int32(width), height: Int32(height),
            codecType: kCMVideoCodecType_H264, encoderSpecification: spec as CFDictionary,
            imageBufferAttributes: nil, compressedDataAllocator: nil,
            outputCallback: nil, refcon: nil, compressionSessionOut: &s)
        guard st == noErr, let s else {
            NSLog("VTCompressionSessionCreate failed: \(st)")
            return nil
        }
        session = s
        let props: [CFString: Any] = [
            kVTCompressionPropertyKey_RealTime: true,
            kVTCompressionPropertyKey_ProfileLevel: kVTProfileLevel_H264_High_AutoLevel,
            kVTCompressionPropertyKey_AllowFrameReordering: false,
            kVTCompressionPropertyKey_AverageBitRate: bitrateKbps * 1000,
            kVTCompressionPropertyKey_ExpectedFrameRate: fps,
            kVTCompressionPropertyKey_MaxKeyFrameInterval: fps * 10,
            kVTCompressionPropertyKey_MaxKeyFrameIntervalDuration: 10,
        ]
        for (k, v) in props {
            let r = VTSessionSetProperty(s, key: k, value: v as CFTypeRef)
            if r != noErr { NSLog("VTSessionSetProperty \(k) failed: \(r)") }
        }
        VTCompressionSessionPrepareToEncodeFrames(s)
    }

    deinit { invalidate() }

    func invalidate() {
        if let s = session {
            VTCompressionSessionCompleteFrames(s, untilPresentationTimeStamp: .invalid)
            VTCompressionSessionInvalidate(s)
        }
        session = nil
    }

    func requestKeyFrame() { forceKey = true }

    func encode(_ pixelBuffer: CVPixelBuffer, pts: CMTime) {
        guard let s = session else { return }
        var opts: CFDictionary?
        if forceKey {
            opts = [kVTEncodeFrameOptionKey_ForceKeyFrame: true] as CFDictionary
            forceKey = false
        }
        VTCompressionSessionEncodeFrame(s, imageBuffer: pixelBuffer, presentationTimeStamp: pts,
                                        duration: .invalid, frameProperties: opts, infoFlagsOut: nil) {
            [weak self] status, _, sample in
            guard status == noErr, let sample, let self else { return }
            self.handle(sample)
        }
    }

    private static let startCode: [UInt8] = [0, 0, 0, 1]

    private func handle(_ sample: CMSampleBuffer) {
        guard let block = CMSampleBufferGetDataBuffer(sample) else { return }
        let ptsSec = CMSampleBufferGetPresentationTimeStamp(sample).seconds
        let pts = UInt64(max(0, ptsSec.isFinite ? ptsSec : 0) * 1_000_000)

        var isKey = true
        if let atts = CMSampleBufferGetSampleAttachmentsArray(sample, createIfNecessary: false) as? [[CFString: Any]],
           let first = atts.first, let notSync = first[kCMSampleAttachmentKey_NotSync] as? Bool {
            isKey = !notSync
        }

        if isKey, let fmt = CMSampleBufferGetFormatDescription(sample) {
            var config = Data()
            var count = 0
            CMVideoFormatDescriptionGetH264ParameterSetAtIndex(fmt, parameterSetIndex: 0, parameterSetPointerOut: nil,
                                                               parameterSetSizeOut: nil, parameterSetCountOut: &count,
                                                               nalUnitHeaderLengthOut: nil)
            for i in 0..<count {
                var ptr: UnsafePointer<UInt8>?
                var size = 0
                if CMVideoFormatDescriptionGetH264ParameterSetAtIndex(fmt, parameterSetIndex: i, parameterSetPointerOut: &ptr,
                                                                      parameterSetSizeOut: &size, parameterSetCountOut: nil,
                                                                      nalUnitHeaderLengthOut: nil) == noErr, let ptr {
                    config.append(contentsOf: Self.startCode)
                    config.append(ptr, count: size)
                }
            }
            output(config, Proto.flagCodecConfig, pts)
        }

        // AVCC（4バイト長プレフィックス）→ Annex-B
        var totalLength = 0
        var dataPtr: UnsafeMutablePointer<CChar>?
        guard CMBlockBufferGetDataPointer(block, atOffset: 0, lengthAtOffsetOut: nil, totalLengthOut: &totalLength,
                                          dataPointerOut: &dataPtr) == noErr, let dataPtr else { return }
        var out = Data(capacity: totalLength + 16)
        dataPtr.withMemoryRebound(to: UInt8.self, capacity: totalLength) { p in
            var off = 0
            while off + 4 <= totalLength {
                let len = Int(p[off]) << 24 | Int(p[off + 1]) << 16 | Int(p[off + 2]) << 8 | Int(p[off + 3])
                off += 4
                guard len > 0, off + len <= totalLength else { break }
                out.append(contentsOf: Self.startCode)
                out.append(p + off, count: len)
                off += len
            }
        }
        output(out, isKey ? Proto.flagKeyFrame : 0, pts)
    }
}
