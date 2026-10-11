package com.classroomadventure.folddisplay

import android.os.SystemClock

/**
 * 遅延の計測。Mac の時計と ping/pong で合わせ、フレームごとに
 * キャプチャ → エンコード完了 → 受信 → 画面に出た までの各区間を測る。
 */
class Stats {
    private class Sample(val rttUs: Long, val offsetUs: Long)
    private class Frame(val encodedUs: Long, val recvUs: Long, val bytes: Int) { var decodedUs = 0L }

    private val lock = Any()
    private val clockSamples = ArrayDeque<Sample>()
    @Volatile var offsetUs = 0L; private set   // server = client + offset
    @Volatile var rttUs = 0L; private set
    private var synced = false

    private val frames = HashMap<Long, Frame>()
    private var nFrames = 0
    private var bytes = 0L
    private var sumEncode = 0L
    private var sumNet = 0L
    private var sumDecode = 0L
    private var sumDecodeOnly = 0L
    private var sumTotal = 0L
    private var maxTotal = 0L
    private var windowStart = SystemClock.elapsedRealtime()

    fun clientUs(ns: Long = SystemClock.elapsedRealtimeNanos()) = ns / 1000

    fun onPong(clientNs: Long, serverUs: Long) = synchronized(lock) {
        val now = clientUs()
        val rtt = now - clientNs / 1000
        clockSamples.addLast(Sample(rtt, serverUs - (clientNs / 1000 + rtt / 2)))
        if (clockSamples.size > 10) clockSamples.removeFirst()
        // RTT が最小のサンプルが一番正確
        val best = clockSamples.minBy { it.rttUs }
        offsetUs = best.offsetUs
        rttUs = rtt
        synced = true
    }

    fun onReceived(captureUs: Long, encodedUs: Long, size: Int) = synchronized(lock) {
        frames[captureUs] = Frame(encodedUs, clientUs() + offsetUs, size)
        if (frames.size > 300) frames.clear()
    }

    fun onDecoded(captureUs: Long) = synchronized(lock) {
        frames[captureUs]?.decodedUs = clientUs() + offsetUs
    }

    /** MediaCodec の OnFrameRendered（実際に画面へ出た時刻） */
    fun onRendered(captureUs: Long, renderNs: Long) = synchronized(lock) {
        val f = frames.remove(captureUs) ?: return
        val shownUs = renderNs / 1000 + offsetUs
        nFrames++
        bytes += f.bytes
        sumEncode += f.encodedUs - captureUs
        sumNet += f.recvUs - f.encodedUs
        sumDecode += shownUs - f.recvUs
        if (f.decodedUs > 0) sumDecodeOnly += f.decodedUs - f.recvUs
        val total = shownUs - captureUs
        sumTotal += total
        if (total > maxTotal) maxTotal = total
    }

    /** 直近の集計を文字列で返し、リセットする */
    fun snapshot(transport: String): String = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        val sec = ((now - windowStart).coerceAtLeast(1)) / 1000.0
        windowStart = now
        val n = nFrames.coerceAtLeast(1)
        fun ms(sum: Long) = "%.1f".format(sum / n / 1000.0)
        val s = buildString {
            append("$transport  ${"%.0f".format(nFrames / sec)} fps  ${"%.1f".format(bytes * 8 / sec / 1e6)} Mbps\n")
            append("RTT ${"%.1f".format(rttUs / 1000.0)} ms${if (synced) "" else " (未同期)"}\n")
            if (nFrames > 0) {
                append("Mac内 ${ms(sumEncode)}  通信 ${ms(sumNet)}  デコード ${ms(sumDecodeOnly)}  表示待ち ${ms(sumDecode - sumDecodeOnly)}\n")
                append("合計 ${ms(sumTotal)} ms（最大 ${"%.1f".format(maxTotal / 1000.0)}）")
            } else {
                append("（画面に変化がないとフレームは来ません）")
            }
        }
        nFrames = 0; bytes = 0; sumEncode = 0; sumNet = 0; sumDecode = 0; sumDecodeOnly = 0; sumTotal = 0; maxTotal = 0
        s
    }
}
