package com.classroomadventure.folddisplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View

/**
 * Mac のカーソルを映像の上に重ねて描く。
 * タッチした位置へは通信を待たずに即座に動かし（ローカル予測）、
 * Mac 側のトラックパッドで動かしたときは Mac から届いた位置に従う。
 */
class CursorView(context: Context) : View(context) {
    private var bitmap: Bitmap? = null
    private var hotX = 0f
    private var hotY = 0f
    private var wPt = 0f
    private var hPt = 0f
    private var nx = -1f
    private var ny = -1f
    private var visibleOnMac = false
    private var localUntil = 0L
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()

    /** 端末ピクセル / Mac ポイント */
    var scale = 2f

    init {
        // 頻繁に動くので専用レイヤーに載せる
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** 指・ペンで操作したとき（UI スレッド） */
    fun moveLocal(x: Float, y: Float) {
        localUntil = SystemClock.uptimeMillis() + 250
        visibleOnMac = true
        set(x, y)
    }

    /** Mac から届いた位置（受信スレッドから呼ばれる） */
    fun moveRemote(visible: Boolean, x: Float, y: Float) = post {
        if (SystemClock.uptimeMillis() < localUntil) return@post
        visibleOnMac = visible
        set(x, y)
    }

    fun setImage(png: ByteArray, hotX: Float, hotY: Float, wPt: Float, hPt: Float) {
        val bmp = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return
        post {
            bitmap = bmp
            this.hotX = hotX; this.hotY = hotY; this.wPt = wPt; this.hPt = hPt
            invalidate()
        }
    }

    private fun set(x: Float, y: Float) {
        nx = x; ny = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        if (!visibleOnMac || nx < 0) return
        val px = nx * width
        val py = ny * height
        val left = px - hotX * scale
        val top = py - hotY * scale
        dst.set(left, top, left + wPt * scale, top + hPt * scale)
        canvas.drawBitmap(b, null, dst, paint)
    }
}
