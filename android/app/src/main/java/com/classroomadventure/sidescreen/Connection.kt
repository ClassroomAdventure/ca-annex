package com.classroomadventure.sidescreen

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

/** Mac との TCP 接続。受信はコールバック、送信は専用スレッドで行う。 */
class Connection(
    private val host: String,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected()
        fun onConfig(width: Int, height: Int, hevc: Boolean)
        fun onVideo(data: ByteArray, flags: Int, captureUs: Long, encodedUs: Long)
        fun onPong(clientNs: Long, serverUs: Long)
        fun onBitrate(kbps: Int)
        fun onCursorPos(visible: Boolean, x: Float, y: Float)
        fun onCursorImage(png: ByteArray, hotX: Float, hotY: Float, wPt: Float, hPt: Float)
        fun onClosed(error: String?)
    }

    @Volatile private var socket: Socket? = null
    @Volatile private var closed = false
    private val outQueue = LinkedBlockingQueue<ByteArray>()
    private var reader: Thread? = null
    private var writer: Thread? = null

    fun start() {
        reader = Thread({ readLoop() }, "fd-reader").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun close() {
        if (closed) return
        closed = true
        try { socket?.close() } catch (_: IOException) {}
        writer?.interrupt()
    }

    private fun readLoop() {
        var err: String? = null
        try {
            val s = Socket()
            s.tcpNoDelay = true
            s.receiveBufferSize = 4 * 1024 * 1024
            s.connect(InetSocketAddress(host, Proto.PORT), 4000)
            socket = s
            if (closed) { s.close(); return }
            writer = Thread({ writeLoop(s) }, "fd-writer").apply { start() }
            listener.onConnected()
            val input = DataInputStream(BufferedInputStream(s.getInputStream(), 1 shl 16))
            while (!closed) {
                val type = input.readUnsignedByte()
                val len = input.readInt()
                if (len < 0 || len > 64 * 1024 * 1024) throw IOException("bad length $len")
                when (type) {
                    Proto.CONFIG -> {
                        val w = input.readInt(); val h = input.readInt()
                        val codec = if (len >= 9) input.readUnsignedByte() else 0
                        input.skipBytes(len - if (len >= 9) 9 else 8)
                        listener.onConfig(w, h, codec == 1)
                    }
                    Proto.VIDEO -> {
                        val flags = input.readUnsignedByte()
                        val capture = input.readLong()
                        val encoded = input.readLong()
                        val data = ByteArray(len - 17)
                        input.readFully(data)
                        listener.onVideo(data, flags, capture, encoded)
                    }
                    Proto.CURSOR_POS -> {
                        val v = input.readUnsignedByte() != 0
                        val x = input.readFloat(); val y = input.readFloat()
                        input.skipBytes(len - 9)
                        listener.onCursorPos(v, x, y)
                    }
                    Proto.CURSOR_IMAGE -> {
                        val hx = input.readFloat(); val hy = input.readFloat()
                        val w = input.readFloat(); val h = input.readFloat()
                        val png = ByteArray(len - 16)
                        input.readFully(png)
                        listener.onCursorImage(png, hx, hy, w, h)
                    }
                    Proto.BITRATE -> {
                        val k = input.readInt()
                        input.skipBytes(len - 4)
                        listener.onBitrate(k)
                    }
                    Proto.PONG -> {
                        val c = input.readLong(); val sv = input.readLong()
                        input.skipBytes(len - 16)
                        listener.onPong(c, sv)
                    }
                    else -> input.skipBytes(len)
                }
            }
        } catch (e: Exception) {
            if (!closed) err = e.message ?: e.javaClass.simpleName
        } finally {
            close()
            listener.onClosed(err)
        }
    }

    private fun writeLoop(s: Socket) {
        try {
            val out = BufferedOutputStream(s.getOutputStream(), 8192)
            while (!closed) {
                val msg = outQueue.take()
                out.write(msg)
                // 溜まっている分はまとめて書いてから flush
                while (true) out.write(outQueue.poll() ?: break)
                out.flush()
            }
        } catch (_: InterruptedException) {
        } catch (_: IOException) {
            close()
        }
    }

    private fun send(type: Int, payload: ByteBuffer) {
        if (closed) return
        val body = payload.array()
        val msg = ByteBuffer.allocate(5 + payload.position())
        msg.put(type.toByte()).putInt(payload.position()).put(body, 0, payload.position())
        outQueue.offer(msg.array())
    }

    fun sendHello(width: Int, height: Int, scale: Float, bitrateKbps: Int, fps: Int, hevc: Boolean, udpPort: Int, deviceName: String) {
        val name = deviceName.toByteArray(Charsets.UTF_8).take(60).toByteArray()
        send(Proto.HELLO, ByteBuffer.allocate(21 + name.size).putInt(width).putInt(height).putFloat(scale).putInt(bitrateKbps)
            .put(fps.toByte()).put((if (hevc) 1 else 0).toByte()).putShort(udpPort.toShort())
            .put(name.size.toByte()).put(name))
    }

    fun sendMouse(kind: Int, x: Float, y: Float) {
        send(Proto.MOUSE, ByteBuffer.allocate(9).put(kind.toByte()).putFloat(x).putFloat(y))
    }

    fun sendScroll(x: Float, y: Float, dx: Float, dy: Float) {
        send(Proto.SCROLL, ByteBuffer.allocate(16).putFloat(x).putFloat(y).putFloat(dx).putFloat(dy))
    }

    fun sendPing() = send(Proto.PING, ByteBuffer.allocate(8).putLong(android.os.SystemClock.elapsedRealtimeNanos()))

    fun sendStats(received: Int, lost: Int, avgNetUs: Int, minNetUs: Int) =
        send(Proto.STATS, ByteBuffer.allocate(16).putInt(received).putInt(lost).putInt(avgNetUs).putInt(minNetUs))

    fun requestKeyFrame() = send(Proto.KEYFRAME, ByteBuffer.allocate(0))
}
