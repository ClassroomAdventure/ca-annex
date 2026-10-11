package com.classroomadventure.folddisplay

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
        fun onConfig(width: Int, height: Int)
        fun onVideo(data: ByteArray, flags: Int, ptsUs: Long)
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
                        input.skipBytes(len - 8)
                        listener.onConfig(w, h)
                    }
                    Proto.VIDEO -> {
                        val flags = input.readUnsignedByte()
                        val pts = input.readLong()
                        val data = ByteArray(len - 9)
                        input.readFully(data)
                        listener.onVideo(data, flags, pts)
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

    fun sendHello(width: Int, height: Int, scale: Float, bitrateKbps: Int, fps: Int) {
        send(Proto.HELLO, ByteBuffer.allocate(17).putInt(width).putInt(height).putFloat(scale).putInt(bitrateKbps).put(fps.toByte()))
    }

    fun sendMouse(kind: Int, x: Float, y: Float) {
        send(Proto.MOUSE, ByteBuffer.allocate(9).put(kind.toByte()).putFloat(x).putFloat(y))
    }

    fun sendScroll(x: Float, y: Float, dx: Float, dy: Float) {
        send(Proto.SCROLL, ByteBuffer.allocate(16).putFloat(x).putFloat(y).putFloat(dx).putFloat(dy))
    }

    fun requestKeyFrame() = send(Proto.KEYFRAME, ByteBuffer.allocate(0))
}
