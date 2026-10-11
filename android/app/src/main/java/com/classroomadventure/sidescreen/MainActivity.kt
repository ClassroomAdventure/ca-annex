package com.classroomadventure.sidescreen

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class MainActivity : Activity() {
    private lateinit var settings: Settings
    private lateinit var discovery: Discovery
    private lateinit var usbStatus: TextView
    private lateinit var usbButton: Button
    private lateinit var wifiList: LinearLayout
    private lateinit var hostInput: EditText

    @Volatile private var probing = false
    private var usbAvailable = false
    private var autoStartedThisPlug = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        discovery = Discovery(this) { renderWifi(it) }
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        discovery.start()
        startUsbProbe()
    }

    override fun onPause() {
        super.onPause()
        discovery.stop()
        probing = false
    }

    private fun connect(host: String, usb: Boolean) {
        if (!usb) settings.lastHost = host
        startActivity(Intent(this, DisplayActivity::class.java).putExtra("host", host).putExtra("usb", usb))
    }

    // --- USB: adb reverse されていれば 127.0.0.1:47800 で Mac に届く ---
    private fun startUsbProbe() {
        if (probing) return
        probing = true
        Thread {
            while (probing) {
                val ok = probeUsb()
                runOnUiThread { setUsb(ok) }
                Thread.sleep(1500)
            }
        }.start()
    }

    private fun probeUsb(): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", Proto.PORT), 500)
            s.soTimeout = 400
            // adb reverse 先で Mac アプリが動いていないと即 EOF になる。待たされたら生きている
            try { s.getInputStream().read() >= 0 } catch (_: SocketTimeoutException) { true }
        }
    } catch (_: Exception) { false }

    private fun setUsb(ok: Boolean) {
        if (!probing) return
        if (ok != usbAvailable) {
            usbAvailable = ok
            if (!ok) autoStartedThisPlug = false
        }
        usbStatus.text = if (ok) "Mac が見つかりました（USB）" else "未検出 — USB ケーブルでつなぎ、Mac で SideScreen を起動してください"
        usbStatus.setTextColor(if (ok) Color.rgb(120, 220, 140) else Color.rgb(170, 170, 180))
        usbButton.isEnabled = ok
        if (ok && settings.autoUsb && !autoStartedThisPlug) {
            autoStartedThisPlug = true
            connect("127.0.0.1", usb = true)
        }
    }

    private fun renderWifi(list: List<Discovery.Found>) {
        wifiList.removeAllViews()
        if (list.isEmpty()) {
            wifiList.addView(label("探しています…（Mac と同じ Wi-Fi に接続してください）", 14f, Color.rgb(170, 170, 180)))
        }
        for (f in list) {
            wifiList.addView(button("${f.name}  (${f.host})") { connect(f.host, usb = false) })
        }
    }

    // --- UI（依存ライブラリなしで組む） ---
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
    }

    private fun card(vararg children: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(16))
        background = GradientDrawable().apply { setColor(Color.rgb(36, 40, 50)); cornerRadius = dp(16).toFloat() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) }
        children.forEach { addView(it) }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        root.addView(label("SideScreen", 28f, bold = true))
        root.addView(label("Android を Mac の拡張ディスプレイにします", 15f, Color.rgb(170, 170, 180)))

        usbStatus = label("確認中…", 14f)
        usbButton = button("USB で接続") { connect("127.0.0.1", usb = true) }.apply { isEnabled = false }
        val autoUsb = CheckBox(this).apply {
            text = "USB をつないだら自動で開始"
            setTextColor(Color.WHITE)
            isChecked = settings.autoUsb
            setOnCheckedChangeListener { _, c -> settings.autoUsb = c }
        }
        root.addView(card(label("有線（USB）", 18f, bold = true), usbStatus, usbButton, autoUsb))

        wifiList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        renderWifi(emptyList())
        hostInput = EditText(this).apply {
            hint = "IP アドレスを直接入力（例: 192.168.1.10）"
            setText(settings.lastHost)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(120, 120, 130))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        val manual = button("この IP に接続") {
            val h = hostInput.text.toString().trim()
            if (h.isNotEmpty()) connect(h, usb = false)
        }
        root.addView(card(label("無線（同じ Wi-Fi）", 18f, bold = true), wifiList, hostInput, manual))

        val scales = listOf(
            3.0f to "特大（少し柔らかく見えます）", 2.5f to "大きく",
            2.0f to "くっきり（Retina 等倍）", 1.75f to "やや広く", 1.5f to "広く", 1.25f to "最大（文字が小さい）",
        )
        val scaleGroup = RadioGroup(this)
        scales.forEachIndexed { i, (v, name) ->
            scaleGroup.addView(RadioButton(this).apply {
                id = 1000 + i
                text = name
                setTextColor(Color.WHITE)
                isChecked = kotlin.math.abs(settings.scale - v) < 0.01f
            })
        }
        scaleGroup.setOnCheckedChangeListener { _, id -> settings.scale = scales[id - 1000].first }
        val fpsGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf(60, 120).forEach { f ->
            fpsGroup.addView(RadioButton(this).apply {
                id = 2000 + f
                text = "$f fps"
                setTextColor(Color.WHITE)
                isChecked = settings.fps == f
            })
        }
        fpsGroup.setOnCheckedChangeListener { _, id -> settings.fps = id - 2000 }
        val hevcBox = CheckBox(this).apply {
            text = "HEVC（H.265）を使う — 同じ通信量で高画質"
            setTextColor(Color.WHITE)
            isChecked = settings.hevc
            isEnabled = VideoDecoder.hevcSupported
            setOnCheckedChangeListener { _, c -> settings.hevc = c }
        }
        root.addView(card(label("表示", 18f, bold = true), label("画面の広さ", 14f, Color.rgb(170, 170, 180)), scaleGroup,
            label("フレームレート（120 は画面が動いているときだけ）", 14f, Color.rgb(170, 170, 180)), fpsGroup, hevcBox))

        root.addView(label(
            "操作: タップ＝クリック / ドラッグ＝ドラッグ / 長押し・2本指タップ＝右クリック / 2本指スワイプ＝スクロール / S Pen はホバーでカーソル移動\n" +
                "USB 接続には「開発者向けオプション → USB デバッグ」を ON にしてください。",
            13f, Color.rgb(150, 150, 160)).apply { setPadding(0, dp(18), 0, 0) })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(22, 24, 30))
            isFillViewport = true
            addView(root, MATCH_PARENT, WRAP_CONTENT)
        }
        // 開いた状態（内側画面）でも読みやすい幅に収める
        val frame = LinearLayout(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(22, 24, 30))
            addView(scroll, LinearLayout.LayoutParams(dp(560).coerceAtMost(resources.displayMetrics.widthPixels), MATCH_PARENT))
        }
        frame.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(frame)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 開閉で横幅が変わるので組み直す
        buildUi()
        renderWifi(emptyList())
        discovery.stop(); discovery.start()
    }
}
