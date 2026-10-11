package com.classroomadventure.folddisplay

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

/** H.264 を MediaCodec でハードウェアデコードして Surface に直接描画する。 */
class VideoDecoder(private val surface: Surface, private val onNeedKeyFrame: () -> Unit) {
    private var codec: MediaCodec? = null
    private var outThread: Thread? = null
    private var pendingConfig: ByteArray? = null
    private var waitingKey = true
    private val lock = Object()

    fun configure(width: Int, height: Int) = synchronized(lock) {
        release()
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height)
        }
        val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        c.configure(fmt, surface, null, 0)
        c.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
        c.start()
        codec = c
        waitingKey = true
        outThread = Thread({ drain(c) }, "fd-decoder-out").apply { priority = Thread.MAX_PRIORITY; start() }
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
                if (idx >= 0) c.releaseOutputBuffer(idx, true)
            }
        } catch (_: IllegalStateException) {
            // release() 済み
        }
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

    companion object { private const val TAG = "VideoDecoder" }
}
