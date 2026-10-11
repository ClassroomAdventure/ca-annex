package com.classroomadventure.sidescreen

import android.app.Activity
import android.graphics.Color
import android.os.Build
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
    private lateinit var cursorView: CursorView
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
    private val stats = Stats()
    private lateinit var statsView: TextView
    private var showStats = false
    @Volatile private var codecName = ""
    @Volatile private var udp: UdpReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        host = intent.getStringExtra("host") ?: "127.0.0.1"
        usb = intent.getBooleanExtra("usb", false)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 30) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            window.setDecorFitsSystemWindows(false)
        } else if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        cursorView = CursorView(this).apply { scale = settings.scale }
        touch = TouchInput(surfaceView, cursorView) { conn }
        surfaceView.setOnTouchListener { _, e ->
            // 手元のカーソルも滑らかに動くよう、触っている間も 120Hz
            lastFrameAt = android.os.SystemClock.uptimeMillis()
            // 3本指タップで統計表示を切り替え
            if (e.actionMasked == android.view.MotionEvent.ACTION_POINTER_DOWN && e.pointerCount == 3) toggleStats()
            touch.onTouch(e)
        }
        surfaceView.setOnGenericMotionListener { _, e -> touch.onGenericMotion(e) }

        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(22, 24, 30))
            text = "接続中…"
        }
        statsView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundColor(Color.argb(170, 0, 0, 0))
            setPadding(24, 16, 24, 16)
            visibility = View.GONE
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
            addView(cursorView, FrameLayout.LayoutParams(-1, -1))
            addView(statsView, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, 80, 40, 0) })
            addView(statusView, FrameLayout.LayoutParams(-1, -1))
        }
        showStats = settings.showStats
        if (showStats) statsView.visibility = View.VISIBLE
        main.post(statsTick)
        main.post(abrTick)
        main.post(refreshTick)
        setContentView(root)
        hideSystemBars()
    }

    private fun toggleStats() {
        showStats = !showStats
        settings.showStats = showStats
        statsView.visibility = if (showStats) View.VISIBLE else View.GONE
    }

    private val statsTick = object : Runnable {
        override fun run() {
            conn?.sendPing()
            val text = stats.snapshot((if (usb) "USB" else "Wi-Fi") + " $codecName")
            if (showStats) statsView.text = text
            android.util.Log.i("SideScreen", text.replace("\n", " | "))
            main.postDelayed(this, 1000)
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
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
        decoder = VideoDecoder(surfaceView.holder.surface, stats) { conn?.requestKeyFrame() }
        if (!usb) {
            // Wi-Fi は映像だけ UDP で受ける
            udp = UdpReceiver(onFrame = { d, f, c, e -> onVideo(d, f, c, e) }, onLoss = { onUdpLoss() }).also { it.start() }
        }
        conn = Connection(host, this).also { it.start() }
    }

    @Volatile private var lastKeyRequest = 0L

    private fun onUdpLoss() {
        stats.onLost()
        decoder?.onLost()
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastKeyRequest > 150) {
            lastKeyRequest = now
            conn?.requestKeyFrame()
        }
    }

    // --- 120Hz は画面が動いているときだけ（止まったら端末の可変リフレッシュレートに任せて節電） ---
    @Volatile private var lastFrameAt = 0L
    private var highRefresh = false

    private val refreshTick = object : Runnable {
        override fun run() {
            val active = settings.fps > 60 && android.os.SystemClock.uptimeMillis() - lastFrameAt < 1000
            if (active != highRefresh) {
                highRefresh = active
                val rate = if (active) settings.fps.toFloat() else 0f
                window.attributes = window.attributes.apply { preferredRefreshRate = rate }
                // 可変リフレッシュレートの画面はレイヤー単位の指定を見る
                if (Build.VERSION.SDK_INT >= 30) try {
                    val surface = surfaceView.holder.surface
                    if (surface.isValid) surface.setFrameRate(rate, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                } catch (_: Exception) {}
            }
            main.postDelayed(this, 200)
        }
    }

    private val abrTick = object : Runnable {
        override fun run() {
            val a = stats.takeAbr()
            conn?.sendStats(a[0], a[1], a[2], a[3])
            main.postDelayed(this, 250)
        }
    }

    private fun disconnect() {
        main.removeCallbacksAndMessages(RECONNECT)
        conn?.close(); conn = null
        udp?.close(); udp = null
        decoder?.shutdown(); decoder = null
    }

    /** Mac のディスプレイ設定に出る名前（例: "Galaxy Z Fold8" / "Pixel 9"） */
    private fun deviceName(): String {
        val n = android.provider.Settings.Global.getString(contentResolver, "device_name")
        return if (!n.isNullOrBlank()) n else android.os.Build.MODEL
    }

    private fun sendHello() {
        if (surfaceW == 0) return
        conn?.sendHello(surfaceW, surfaceH, settings.scale, settings.bitrateKbps(usb), settings.fps,
            hevc = settings.hevc && VideoDecoder.hevcSupported, udpPort = udp?.port ?: 0, deviceName = deviceName())
    }

    override fun onConnected() = main.post {
        sendHello()
        // 最初に数回 ping して時計をすばやく合わせる
        for (i in 1..5) main.postDelayed({ conn?.sendPing() }, i * 60L)
    }.let {}

    override fun onConfig(width: Int, height: Int, hevc: Boolean) {
        codecName = if (hevc) "HEVC" else "H.264"
        decoder?.configure(width, height, hevc)
    }

    override fun onPong(clientNs: Long, serverUs: Long) = stats.onPong(clientNs, serverUs)

    override fun onBitrate(kbps: Int) { stats.targetKbps = kbps }

    override fun onCursorPos(visible: Boolean, x: Float, y: Float) = cursorView.moveRemote(visible, x, y).let {}

    override fun onCursorImage(png: ByteArray, hotX: Float, hotY: Float, wPt: Float, hPt: Float) =
        cursorView.setImage(png, hotX, hotY, wPt, hPt)

    override fun onVideo(data: ByteArray, flags: Int, captureUs: Long, encodedUs: Long) {
        if (flags and Proto.FLAG_CODEC_CONFIG == 0) {
            stats.onReceived(captureUs, encodedUs, data.size)
            lastFrameAt = android.os.SystemClock.uptimeMillis()
        }
        decoder?.feed(data, flags, captureUs)
        if (!gotFirstFrame && flags and Proto.FLAG_KEY_FRAME != 0) {
            gotFirstFrame = true
            main.post { statusView.visibility = View.GONE }
        }
    }

    override fun onClosed(error: String?) {
        main.post {
            if (finishing || !surfaceReady) return@post
            showStatus("切断されました${error?.let { "\n($it)" } ?: ""}\n\n再接続しています…\n（戻るで終了）")
            decoder?.shutdown(); decoder = null
            udp?.close(); udp = null
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
        main.removeCallbacks(statsTick)
        main.removeCallbacks(abrTick)
        main.removeCallbacks(refreshTick)
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
