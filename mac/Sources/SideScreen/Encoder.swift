import CoreMedia
import Foundation
import VideoToolbox

enum VideoCodec: UInt8 {
    case h264 = 0, hevc = 1
}

/// VideoToolbox によるリアルタイムエンコーダ（H.264 / HEVC）。出力は Annex-B。
final class VideoEncoder {
    typealias Output = (_ data: Data, _ flags: UInt8, _ ptsMicros: UInt64) -> Void

    private var session: VTCompressionSession?
    private let output: Output
    let width: Int
    let height: Int
    let codec: VideoCodec
    private(set) var bitrateKbps: Int
    private var forceKey = true

    /// 希望のコーデックで作れなければ H.264 にフォールバックする
    static func make(codec: VideoCodec, width: Int, height: Int, fps: Int, bitrateKbps: Int, output: @escaping Output) -> VideoEncoder? {
        if let e = VideoEncoder(codec: codec, width: width, height: height, fps: fps, bitrateKbps: bitrateKbps, output: output) { return e }
        if codec != .h264 {
            NSLog("\(codec) encoder unavailable, falling back to H.264")
            return VideoEncoder(codec: .h264, width: width, height: height, fps: fps, bitrateKbps: bitrateKbps, output: output)
        }
        return nil
    }

    private init?(codec: VideoCodec, width: Int, height: Int, fps: Int, bitrateKbps: Int, output: @escaping Output) {
        self.codec = codec
        self.width = width
        self.height = height
        self.bitrateKbps = bitrateKbps
        self.output = output

        let spec: [CFString: Any] = [
            kVTVideoEncoderSpecification_EnableLowLatencyRateControl: true,
            kVTVideoEncoderSpecification_RequireHardwareAcceleratedVideoEncoder: true,
        ]
        var s: VTCompressionSession?
        let st = VTCompressionSessionCreate(
            allocator: nil, width: Int32(width), height: Int32(height),
            codecType: codec == .hevc ? kCMVideoCodecType_HEVC : kCMVideoCodecType_H264,
            encoderSpecification: spec as CFDictionary,
            imageBufferAttributes: nil, compressedDataAllocator: nil,
            outputCallback: nil, refcon: nil, compressionSessionOut: &s)
        guard st == noErr, let s else {
            NSLog("VTCompressionSessionCreate(\(codec)) failed: \(st)")
            return nil
        }
        session = s
        let props: [CFString: Any] = [
            kVTCompressionPropertyKey_RealTime: true,
            kVTCompressionPropertyKey_ProfileLevel: codec == .hevc ? kVTProfileLevel_HEVC_Main_AutoLevel : kVTProfileLevel_H264_High_AutoLevel,
            kVTCompressionPropertyKey_AllowFrameReordering: false,
            kVTCompressionPropertyKey_AverageBitRate: bitrateKbps * 1000,
            kVTCompressionPropertyKey_ExpectedFrameRate: fps,
            // 定期キーフレームは出さない（通信量が跳ねてカクつく原因）。必要なときだけ requestKeyFrame する
            kVTCompressionPropertyKey_MaxKeyFrameInterval: Int32.max,
            kVTCompressionPropertyKey_MaxKeyFrameIntervalDuration: 0,
        ]
        for (k, v) in props {
            let r = VTSessionSetProperty(s, key: k, value: v as CFTypeRef)
            if r != noErr { NSLog("VTSessionSetProperty \(k) failed: \(r)") }
        }
        VTCompressionSessionPrepareToEncodeFrames(s)
        NSLog("encoder \(codec) \(width)x\(height) \(bitrateKbps)kbps")
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

    /// 走行中にビットレートを変える（回線状況に合わせた自動調整用）
    func setBitrate(kbps: Int) {
        guard let s = session, kbps != bitrateKbps else { return }
        bitrateKbps = kbps
        VTSessionSetProperty(s, key: kVTCompressionPropertyKey_AverageBitRate, value: (kbps * 1000) as CFNumber)
    }

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

    private func parameterSets(_ fmt: CMFormatDescription) -> Data {
        typealias Getter = (CMFormatDescription, Int, UnsafeMutablePointer<UnsafePointer<UInt8>?>?, UnsafeMutablePointer<Int>?,
                            UnsafeMutablePointer<Int>?, UnsafeMutablePointer<Int32>?) -> OSStatus
        let get: Getter = codec == .hevc ? CMVideoFormatDescriptionGetHEVCParameterSetAtIndex : CMVideoFormatDescriptionGetH264ParameterSetAtIndex
        var config = Data()
        var count = 0
        _ = get(fmt, 0, nil, nil, &count, nil)
        for i in 0..<count {
            var ptr: UnsafePointer<UInt8>?
            var size = 0
            if get(fmt, i, &ptr, &size, nil, nil) == noErr, let ptr {
                config.append(contentsOf: Self.startCode)
                config.append(ptr, count: size)
            }
        }
        return config
    }

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
            output(parameterSets(fmt), Proto.flagCodecConfig, pts)
        }

        // AVCC/HVCC（4バイト長プレフィックス）→ Annex-B
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
        if isKey { NSLog("keyframe \(codec) \(out.count / 1024)KB") }
        output(out, isKey ? Proto.flagKeyFrame : 0, pts)
    }
}
