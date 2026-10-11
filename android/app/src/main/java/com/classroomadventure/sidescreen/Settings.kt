package com.classroomadventure.sidescreen

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 端末ピクセル / Mac のポイント。2.0 = Retina 等倍。小さいほど広く使える（文字は小さくなる） */
    var scale: Float
        get() = prefs.getFloat("scale", 2.0f)
        set(v) = prefs.edit().putFloat("scale", v).apply()

    var fps: Int
        get() = prefs.getInt("fps", 60)
        set(v) = prefs.edit().putInt("fps", v).apply()

    var autoUsb: Boolean
        get() = prefs.getBoolean("autoUsb", true)
        set(v) = prefs.edit().putBoolean("autoUsb", v).apply()

    var showStats: Boolean
        get() = prefs.getBoolean("showStats", false)
        set(v) = prefs.edit().putBoolean("showStats", v).apply()

    var hevc: Boolean
        get() = prefs.getBoolean("hevc", true)
        set(v) = prefs.edit().putBoolean("hevc", v).apply()

    var lastHost: String
        get() = prefs.getString("lastHost", "") ?: ""
        set(v) = prefs.edit().putString("lastHost", v).apply()

    /** USB は固定、Wi-Fi はこれを上限に自動調整 */
    fun bitrateKbps(usb: Boolean) = if (usb) 40_000 else 30_000
}
