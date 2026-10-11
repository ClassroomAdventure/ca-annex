package com.classroomadventure.sidescreen

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

/** H.264 を MediaCodec でハードウェアデコードして Surface に直接描画する。 */
class VideoDecoder(
    private val surface: Surface,
    private val stats: Stats,
    private val onNeedKeyFrame: () -> Unit,
) {
    private val renderThread = android.os.HandlerThread("fd-render").apply { start() }
    private val renderHandler = android.os.Handler(renderThread.looper)
    private var codec: MediaCodec? = null
    private var outThread: Thread? = null
    private var pendingConfig: ByteArray? = null
    private var waitingKey = true
    private val lock = Object()

    fun configure(width: Int, height: Int, hevc: Boolean) = synchronized(lock) {
        release()
        try {
            start(width, height, hevc, vendorKeys = true)
        } catch (e: Exception) {
            // 知らないキーで失敗するデコーダ向けに、標準の指定だけで作り直す
            Log.w(TAG, "decoder config failed with low-latency keys, retrying", e)
            release()
            start(width, height, hevc, vendorKeys = false)
        }
    }

    private fun start(width: Int, height: Int, hevc: Boolean, vendorKeys: Boolean) {
        val mime = if (hevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
        val fmt = MediaFormat.createVideoFormat(mime, width, height).apply {
            if (vendorKeys && android.os.Build.VERSION.SDK_INT >= 30) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height)
            if (vendorKeys) {
                // Snapdragon のデコーダはこれが無いと数フレーム溜めてから出す（他社チップでは無視される）
                setInteger("vendor.qti-ext-dec-low-latency.enable", 1)
                setInteger("vendor.qti-ext-dec-picture-order.enable", 1)
            }
        }
        val c = MediaCodec.createDecoderByType(mime)
        c.configure(fmt, surface, null, 0)
        c.setOnFrameRenderedListener({ _, ptsUs, nanoTime ->
            // nanoTime は System.nanoTime（MONOTONIC）基準。Stats の時計（BOOTTIME）に揃える
            stats.onRendered(ptsUs, nanoTime + android.os.SystemClock.elapsedRealtimeNanos() - System.nanoTime())
        }, renderHandler)
        c.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
        c.start()
        codec = c
        waitingKey = true
        outThread = Thread({ drain(c) }, "fd-decoder-out").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    /** UDP でフレームが欠けたら参照が壊れるので、次のキーフレームまで止める */
    fun onLost() {
        waitingKey = true
    }

    fun feed(data: ByteArray, flags: Int, ptsUs: Long) {
        val c = synchronized(lock) { codec } ?: return
        if (flags and Proto.FLAG_CODEC_CONFIG != 0) {
            pendingConfig = data
            return
        }
        val isKey = flags and Proto.FLAG_KEY_FRAME != 0
        if (waitingKey && !isKey) return
        try {
            if (isKey) {
                pendingConfig?.let { queue(c, it, ptsUs, MediaCodec.BUFFER_FLAG_CODEC_CONFIG) }
                pendingConfig = null
                waitingKey = false
            }
            if (!queue(c, data, ptsUs, if (isKey) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)) {
                // 入力が詰まって落としたら参照が壊れるので次のキーフレームまで待つ
                waitingKey = true
                onNeedKeyFrame()
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "feed failed", e)
        }
    }

    private fun queue(c: MediaCodec, data: ByteArray, ptsUs: Long, flags: Int): Boolean {
        val idx = c.dequeueInputBuffer(100_000)
        if (idx < 0) return false
        val buf = c.getInputBuffer(idx) ?: return false
        buf.clear()
        if (data.size > buf.capacity()) {
            c.queueInputBuffer(idx, 0, 0, ptsUs, 0)
            return false
        }
        buf.put(data)
        c.queueInputBuffer(idx, 0, data.size, ptsUs, flags)
        return true
    }

    private fun drain(c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        try {
            while (!Thread.currentThread().isInterrupted) {
                val idx = c.dequeueOutputBuffer(info, 50_000)
                // PTS は Mac の時計なので、描画時刻は「今すぐ」を明示する
                if (idx >= 0) {
                    stats.onDecoded(info.presentationTimeUs)
                    c.releaseOutputBuffer(idx, System.nanoTime())
                }
            }
        } catch (_: IllegalStateException) {
            // release() 済み
        }
    }

    fun shutdown() {
        release()
        renderThread.quitSafely()
    }

    fun release() = synchronized(lock) {
        val c = codec ?: return
        codec = null
        outThread?.interrupt()
        try { outThread?.join(300) } catch (_: InterruptedException) {}
        outThread = null
        try { c.stop() } catch (_: Exception) {}
        c.release()
    }

    companion object {
        private const val TAG = "VideoDecoder"

        /** ハードウェアの HEVC デコーダがあるか */
        val hevcSupported: Boolean by lazy {
            android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder && (android.os.Build.VERSION.SDK_INT < 29 || info.isHardwareAccelerated) &&
                    info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) }
            }
        }
    }
}
