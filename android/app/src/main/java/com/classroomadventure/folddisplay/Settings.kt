package com.classroomadventure.folddisplay

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

    var lastHost: String
        get() = prefs.getString("lastHost", "") ?: ""
        set(v) = prefs.edit().putString("lastHost", v).apply()

    fun bitrateKbps(usb: Boolean) = if (usb) 40_000 else 16_000
}
