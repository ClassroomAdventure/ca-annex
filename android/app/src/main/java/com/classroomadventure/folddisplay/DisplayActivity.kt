package com.classroomadventure.folddisplay

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/** 全画面で Mac の仮想ディスプレイを表示し、タッチを送り返す。 */
class DisplayActivity : Activity(), SurfaceHolder.Callback, Connection.Listener {
    private lateinit var settings: Settings
    private lateinit var surfaceView: SurfaceView
    private lateinit var statusView: TextView
    private lateinit var touch: TouchInput
    private val main = Handler(Looper.getMainLooper())

    private var host = ""
    private var usb = false
    @Volatile private var conn: Connection? = null
    @Volatile private var decoder: VideoDecoder? = null
    private var surfaceW = 0
    private var surfaceH = 0
    private var surfaceReady = false
    private var gotFirstFrame = false
    private var finishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        host = intent.getStringExtra("host") ?: "127.0.0.1"
        usb = intent.getBooleanExtra("usb", false)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            // 端末のリフレッシュレートを上げる（120Hz 対応画面向け）
            preferredRefreshRate = settings.fps.toFloat()
        }
        window.setDecorFitsSystemWindows(false)

        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        touch = TouchInput(surfaceView) { conn }
        surfaceView.setOnTouchListener { _, e -> touch.onTouch(e) }
        surfaceView.setOnGenericMotionListener { _, e -> touch.onGenericMotion(e) }

        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(22, 24, 30))
            text = "接続中…"
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
            addView(statusView, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
        hideSystemBars()
    }

    private fun hideSystemBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // --- Surface ---
    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val sizeChanged = width != surfaceW || height != surfaceH
        surfaceW = width; surfaceH = height
        if (!surfaceReady) {
            surfaceReady = true
            connect()
        } else if (sizeChanged) {
            // 折りたたみの開閉・回転 → Mac 側の解像度を合わせ直す
            sendHello()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        disconnect()
    }

    // --- 接続 ---
    private fun connect() {
        if (finishing || !surfaceReady) return
        disconnect()
        gotFirstFrame = false
        showStatus(if (usb) "USB で接続中…" else "$host に接続中…")
        decoder = VideoDecoder(surfaceView.holder.surface) { conn?.requestKeyFrame() }
        conn = Connection(host, this).also { it.start() }
    }

    private fun disconnect() {
        main.removeCallbacksAndMessages(RECONNECT)
        conn?.close(); conn = null
        decoder?.release(); decoder = null
    }

    private fun sendHello() {
        if (surfaceW == 0) return
        conn?.sendHello(surfaceW, surfaceH, settings.scale, settings.bitrateKbps(usb), settings.fps)
    }

    override fun onConnected() = main.post { sendHello() }.let {}

    override fun onConfig(width: Int, height: Int) {
        decoder?.configure(width, height)
    }

    override fun onVideo(data: ByteArray, flags: Int, ptsUs: Long) {
        decoder?.feed(data, flags, ptsUs)
        if (!gotFirstFrame && flags and Proto.FLAG_KEY_FRAME != 0) {
            gotFirstFrame = true
            main.post { statusView.visibility = View.GONE }
        }
    }

    override fun onClosed(error: String?) {
        main.post {
            if (finishing || !surfaceReady) return@post
            showStatus("切断されました${error?.let { "\n($it)" } ?: ""}\n\n再接続しています…\n（戻るで終了）")
            decoder?.release(); decoder = null
            conn = null
            main.postAtTime({ connect() }, RECONNECT, android.os.SystemClock.uptimeMillis() + 2000)
        }
    }

    private fun showStatus(text: String) {
        statusView.text = text
        statusView.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        finishing = true
        disconnect()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishing = true
        disconnect()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object { private val RECONNECT = Any() }
}
