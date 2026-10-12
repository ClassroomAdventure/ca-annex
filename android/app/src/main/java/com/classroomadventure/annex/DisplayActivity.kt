package com.classroomadventure.annex

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.classroomadventure.annex.ui.Status
import com.classroomadventure.annex.ui.StatusKind
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
class DisplayActivity : androidx.activity.ComponentActivity(), SurfaceHolder.Callback, Connection.Listener {
    private lateinit var settings: Settings
    private lateinit var surfaceView: SurfaceView
    private lateinit var statusView: androidx.compose.ui.platform.ComposeView
    private var status by androidx.compose.runtime.mutableStateOf<com.classroomadventure.annex.ui.Status?>(null)
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
    @Volatile private var denied = false
    @Volatile private var macNeedsPermission = false
    /** Mac の許可待ちの案内を、つなぎ直しの間も出したままにする */
    private var holdPermissionStatus = false
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

        status = com.classroomadventure.annex.ui.Status(com.classroomadventure.annex.ui.StatusKind.CONNECTING, getString(R.string.status_connecting))
        statusView = androidx.compose.ui.platform.ComposeView(this).apply {
            setContent {
                com.classroomadventure.annex.ui.AnnexTheme {
                    status?.let { com.classroomadventure.annex.ui.StatusOverlay(it, onStop = { stop() }) }
                }
            }
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
            val text = stats.snapshot(resources, (if (usb) "USB" else "Wi-Fi") + " $codecName")
            if (showStats) statsView.text = text
            android.util.Log.i("Annex", text.replace("\n", " | "))
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
        if (!holdPermissionStatus) {
            showStatus(StatusKind.CONNECTING, if (usb) getString(R.string.status_connecting_usb) else getString(R.string.status_connecting_host, host))
        }
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
            hevc = settings.hevc && VideoDecoder.hevcSupported, udpPort = udp?.port ?: 0, deviceName = deviceName(), deviceId = settings.deviceId)
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

    override fun onApproval(state: Int) {
        main.post {
            when (state) {
                0 -> showStatus(StatusKind.WAITING_APPROVAL, getString(R.string.status_waiting_approval),
                    getString(R.string.status_waiting_approval_body))
                1 -> if (!gotFirstFrame) showStatus(StatusKind.CONNECTING, getString(R.string.status_connecting))
                2 -> { denied = true; showStatus(StatusKind.DENIED, getString(R.string.status_denied)) }
                3 -> { denied = true; showStatus(StatusKind.DENIED, getString(R.string.status_mac_unsupported), getString(R.string.status_mac_unsupported_body)) }
                // Mac 側の画面収録が未許可。つなぎ直しは続ける（許可されれば自動でつながる）ので denied にはしない
                4 -> { macNeedsPermission = true; showStatus(StatusKind.WAITING_APPROVAL, getString(R.string.status_mac_permission), getString(R.string.status_mac_permission_body)) }
            }
        }
    }

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
            settings.hasConnected = true
            main.post { holdPermissionStatus = false }
            main.post { statusView.visibility = View.GONE }
        }
    }

    override fun onClosed(error: String?) {
        main.post {
            if (finishing || !surfaceReady) return@post
            if (denied) return@post // 理由はすでに表示済み（拒否 / Mac 側が非対応）
            if (macNeedsPermission) {
                // 案内を出したまま、少し間を空けてつなぎ直す
                macNeedsPermission = false
                holdPermissionStatus = true
                decoder?.shutdown(); decoder = null
                udp?.close(); udp = null
                conn = null
                main.postAtTime({ connect() }, RECONNECT, android.os.SystemClock.uptimeMillis() + 3000)
                return@post
            }
            showStatus(StatusKind.RECONNECTING, getString(R.string.status_disconnected),
                getString(R.string.status_reconnecting) + (error?.let { "\n($it)" } ?: ""))
            decoder?.shutdown(); decoder = null
            udp?.close(); udp = null
            conn = null
            main.postAtTime({ connect() }, RECONNECT, android.os.SystemClock.uptimeMillis() + 2000)
        }
    }

    private fun stop() {
        finishing = true
        disconnect()
        finish()
    }

    private fun showStatus(kind: StatusKind, title: String, body: String? = null) {
        status = Status(kind, title, body)
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
