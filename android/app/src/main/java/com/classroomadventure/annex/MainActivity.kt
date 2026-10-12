package com.classroomadventure.annex

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.classroomadventure.annex.ui.HomeActions
import com.classroomadventure.annex.ui.HomeScreen
import com.classroomadventure.annex.ui.HomeState
import com.classroomadventure.annex.ui.SettingsActions
import com.classroomadventure.annex.ui.SettingsScreen
import com.classroomadventure.annex.ui.AnnexTheme
import com.classroomadventure.annex.ui.Target
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var settings: Settings
    private lateinit var discovery: Discovery

    private val wifiTargets = mutableStateListOf<Target>()
    private var usbAvailable by mutableStateOf(false)
    private var firstRun by mutableStateOf(true)

    @Volatile private var probing = false
    private var autoStartedThisPlug = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        discovery = Discovery(this) { list ->
            wifiTargets.clear()
            wifiTargets.addAll(list.map { Target(it.name, it.host, usb = false) })
        }
        setContent {
            AnnexTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showSettings) { showSettings = false }
                if (showSettings) {
                    SettingsScreen(
                        settings, VideoDecoder.hevcSupported, BuildConfig.VERSION_NAME, siteUrl(),
                        SettingsActions(
                            back = { showSettings = false },
                            openUrl = { open(it) },
                            resetPairing = {
                                settings.resetDeviceId()
                                toast(R.string.reset_pairing_done)
                            },
                        ),
                    )
                } else {
                    val targets = buildList {
                        if (usbAvailable) add(Target(getString(R.string.home_usb_mac), "127.0.0.1", usb = true))
                        addAll(wifiTargets)
                    }
                    HomeScreen(
                        HomeState(targets, firstRun), siteUrl(), settings.lastHost,
                        HomeActions(
                            connect = { connect(it.host, it.usb) },
                            connectManual = { connect(it, usb = false) },
                            openSettings = { showSettings = true },
                            shareMacLink = { shareLink() },
                            copyMacLink = { copyLink() },
                            openDeveloperOptions = { openDeveloperOptions() },
                            tryDemo = { startActivity(Intent(this, DemoActivity::class.java)) },
                        ),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        firstRun = !settings.hasConnected
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

    /** 紹介ページ（Mac 版の入手先）。端末の言語に合わせる */
    private fun siteUrl(): String {
        val ja = Locale.getDefault().language == "ja"
        return "https://classroomadventure.github.io/ca-annex/" + if (ja) "ja/" else ""
    }

    private fun shareLink() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, siteUrl()))
        startActivity(Intent.createChooser(send, getString(R.string.guide_share)))
    }

    private fun copyLink() {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Annex", siteUrl()))
        // Android 13 以降はシステムがコピーを知らせる
        if (Build.VERSION.SDK_INT < 33) toast(R.string.copied)
    }

    private fun openDeveloperOptions() {
        val dev = Intent(AndroidSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        val enabled = AndroidSettings.Global.getInt(contentResolver, AndroidSettings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        try {
            if (enabled) startActivity(dev) else {
                // 開発者向けオプションがまだ無い端末は「端末情報」を開いて、ビルド番号を 7 回タップしてもらう
                toast(R.string.dev_not_enabled)
                startActivity(Intent(AndroidSettings.ACTION_DEVICE_INFO_SETTINGS))
            }
        } catch (_: Exception) {
            startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
        }
    }

    private fun open(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

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
        if (ok && settings.autoUsb && !autoStartedThisPlug) {
            autoStartedThisPlug = true
            connect("127.0.0.1", usb = true)
        }
    }
}
