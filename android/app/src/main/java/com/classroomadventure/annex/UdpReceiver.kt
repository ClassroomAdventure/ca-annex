package com.classroomadventure.annex

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

/**
 * Wi-Fi 時の映像受信（UDP）。Mac 側 UDPSender.swift と対。
 * 欠けたシャードはパリティで1つまで復元し、それでも足りないフレームは「欠落」として報告する。
 */
class UdpReceiver(
    private val onFrame: (data: ByteArray, flags: Int, captureUs: Long, encodedUs: Long) -> Unit,
    private val onLoss: () -> Unit,
) {
    private class Assembly(val dataCount: Int, val parityCount: Int, val frameLen: Int) {
        val shards = arrayOfNulls<ByteArray>(dataCount + parityCount)
        val firstSeen = System.nanoTime()

        fun add(index: Int, data: ByteArray) {
            if (index < shards.size && shards[index] == null) shards[index] = data
        }

        /** 揃っていれば（必要なら復元して）フレームを返す */
        fun tryBuild(): ByteArray? {
            for (g in 0 until parityCount) {
                val from = g * GROUP
                val to = minOf(dataCount, from + GROUP)
                val missing = (from until to).filter { shards[it] == null }
                val parity = shards[dataCount + g]
                if (missing.size == 1 && parity != null) {
                    val r = parity.copyOf()
                    for (i in from until to) if (i != missing[0]) {
                        val s = shards[i]!!
                        for (j in 0 until SHARD) r[j] = (r[j].toInt() xor s[j].toInt()).toByte()
                    }
                    shards[missing[0]] = r
                }
            }
            for (i in 0 until dataCount) if (shards[i] == null) return null
            val out = ByteArray(frameLen)
            for (i in 0 until dataCount) {
                val off = i * SHARD
                System.arraycopy(shards[i]!!, 0, out, off, minOf(SHARD, frameLen - off))
            }
            return out
        }
    }

    private val socket = DatagramSocket(0).apply {
        receiveBufferSize = 4 * 1024 * 1024
        soTimeout = 30
    }
    val port: Int get() = socket.localPort
    @Volatile private var closed = false
    private val pending = sortedMapOf<Long, Assembly>()
    private var lastDelivered = -1L

    fun start() {
        Thread({ loop() }, "fd-udp").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun close() {
        closed = true
        socket.close()
    }

    private fun loop() {
        val buf = ByteArray(2048)
        val pkt = DatagramPacket(buf, buf.size)
        while (!closed) {
            try {
                pkt.setData(buf)
                socket.receive(pkt)
                handle(ByteBuffer.wrap(buf, 0, pkt.length))
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
                if (closed) return
            }
            expireOld()
        }
    }

    private fun handle(b: ByteBuffer) {
        if (b.remaining() < HEADER + SHARD) return
        val frameId = b.int.toLong() and 0xffffffffL
        val index = b.short.toInt() and 0xffff
        val dataCount = b.short.toInt() and 0xffff
        val parityCount = b.short.toInt() and 0xffff
        val frameLen = b.int
        if (frameId <= lastDelivered) return // 遅れて届いたもの / 復元済みフレームの残り
        val shard = ByteArray(SHARD)
        b.get(shard)
        val asm = pending.getOrPut(frameId) { Assembly(dataCount, parityCount, frameLen) }
        asm.add(index, shard)
        val frame = asm.tryBuild() ?: return

        // 送信は順番どおりなので、これより前の未完成フレームはもう揃わない
        val older = pending.headMap(frameId)
        repeat(older.size) { onLoss() }
        older.clear()
        pending.remove(frameId)
        lastDelivered = frameId
        deliver(frame)
    }

    /** 次のフレームが来ない（画面が止まった）ときでも欠落に気づけるように */
    private fun expireOld() {
        val now = System.nanoTime()
        val it = pending.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (now - e.value.firstSeen > 80_000_000L) {
                it.remove()
                if (e.key > lastDelivered) lastDelivered = e.key
                onLoss()
            }
        }
    }

    private fun deliver(f: ByteArray) {
        val b = ByteBuffer.wrap(f)
        val flags = b.get().toInt() and 0xff
        val capture = b.long
        val encoded = b.long
        onFrame(f.copyOfRange(17, f.size), flags, capture, encoded)
    }

    companion object {
        const val SHARD = 1200
        const val GROUP = 8
        const val HEADER = 14
    }
}
