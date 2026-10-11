package com.classroomadventure.folddisplay

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot

/**
 * タッチをマウス操作に変換する。
 * - タップ: クリック（素早く2回でダブルクリック）
 * - 1本指ドラッグ: 左ドラッグ（テキスト選択・ウィンドウ移動）
 * - 長押し / 2本指タップ: 右クリック
 * - 2本指スワイプ: スクロール（慣性あり）
 * - S Pen / マウス: 押した瞬間に左ボタン、ホバーでカーソル移動、ペンのボタンで右クリック
 */
class TouchInput(
    private val view: View,
    private val cursor: CursorView,
    private val connProvider: () -> Connection?,
) {
    /** マウスイベントを送る前に、手元のカーソルを先に動かす */
    private inner class LocalEcho(private val c: Connection) {
        fun sendMouse(kind: Int, x: Float, y: Float) {
            if (kind != Proto.M_RIGHT_CLICK) cursor.moveLocal(x, y)
            c.sendMouse(kind, x, y)
        }
        fun sendScroll(x: Float, y: Float, dx: Float, dy: Float) = c.sendScroll(x, y, dx, dy)
    }

    private fun conn(): LocalEcho? = connProvider()?.let { LocalEcho(it) }

    private enum class State { IDLE, PENDING, DRAGGING, SCROLLING, DONE }

    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
    private var state = State.IDLE
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var maxPointers = 0
    private var scrollMoved = false
    private var lastCx = 0f
    private var lastCy = 0f
    private var flingVx = 0f
    private var flingVy = 0f
    private var lastMoveTime = 0L
    private var penDown = false

    private val longPress = Runnable {
        if (state == State.PENDING) {
            conn()?.sendMouse(Proto.M_RIGHT_CLICK, nx(downX), ny(downY))
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            state = State.DONE
        }
    }

    private fun nx(x: Float) = x / view.width.coerceAtLeast(1)
    private fun ny(y: Float) = y / view.height.coerceAtLeast(1)

    fun onTouch(e: MotionEvent): Boolean {
        val tool = e.getToolType(0)
        if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_MOUSE) return onPointer(e)
        val c = conn() ?: return true

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopFling()
                state = State.PENDING
                downX = e.x; downY = e.y; downTime = e.eventTime
                maxPointers = 1
                c.sendMouse(Proto.M_MOVE, nx(e.x), ny(e.y))
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                if (state == State.PENDING || state == State.SCROLLING) {
                    handler.removeCallbacks(longPress)
                    state = State.SCROLLING
                    scrollMoved = false
                    centroid(e).let { lastCx = it.first; lastCy = it.second }
                    flingVx = 0f; flingVy = 0f; lastMoveTime = e.eventTime
                }
            }
            MotionEvent.ACTION_MOVE -> when (state) {
                State.PENDING -> if (hypot(e.x - downX, e.y - downY) > slop) {
                    handler.removeCallbacks(longPress)
                    c.sendMouse(Proto.M_LEFT_DOWN, nx(downX), ny(downY))
                    c.sendMouse(Proto.M_LEFT_DRAG, nx(e.x), ny(e.y))
                    state = State.DRAGGING
                }
                State.DRAGGING -> c.sendMouse(Proto.M_LEFT_DRAG, nx(e.x), ny(e.y))
                State.SCROLLING -> {
                    val (cx, cy) = centroid(e)
                    val dx = cx - lastCx; val dy = cy - lastCy
                    if (scrollMoved || hypot(dx, dy) > slop / 2) {
                        scrollMoved = true
                        c.sendScroll(nx(cx), ny(cy), nx(dx), ny(dy))
                        lastCx = cx; lastCy = cy
                        val dt = (e.eventTime - lastMoveTime).coerceAtLeast(1) / 1000f
                        flingVx = flingVx * 0.6f + dx / dt * 0.4f
                        flingVy = flingVy * 0.6f + dy / dt * 0.4f
                        lastMoveTime = e.eventTime
                    }
                }
                else -> {}
            }
            MotionEvent.ACTION_POINTER_UP -> if (state == State.SCROLLING) {
                // 残った指で重心を取り直す（ジャンプ防止）
                val (cx, cy) = centroid(e, exclude = e.actionIndex)
                lastCx = cx; lastCy = cy
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                when (state) {
                    State.PENDING -> {
                        c.sendMouse(Proto.M_LEFT_DOWN, nx(downX), ny(downY))
                        c.sendMouse(Proto.M_LEFT_UP, nx(downX), ny(downY))
                    }
                    State.DRAGGING -> c.sendMouse(Proto.M_LEFT_UP, nx(e.x), ny(e.y))
                    State.SCROLLING -> {
                        if (!scrollMoved && maxPointers == 2 && e.eventTime - downTime < 400) {
                            c.sendMouse(Proto.M_RIGHT_CLICK, nx(downX), ny(downY))
                        } else if (e.eventTime - lastMoveTime < 60) {
                            startFling(lastCx, lastCy, flingVx, flingVy)
                        }
                    }
                    else -> {}
                }
                state = State.IDLE
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                if (state == State.DRAGGING) c.sendMouse(Proto.M_LEFT_UP, nx(e.x), ny(e.y))
                state = State.IDLE
            }
        }
        return true
    }

    private fun centroid(e: MotionEvent, exclude: Int = -1): Pair<Float, Float> {
        var sx = 0f; var sy = 0f; var n = 0
        for (i in 0 until e.pointerCount) {
            if (i == exclude) continue
            sx += e.getX(i); sy += e.getY(i); n++
        }
        return if (n == 0) e.x to e.y else (sx / n) to (sy / n)
    }

    // --- 慣性スクロール ---
    private var flingRunnable: Runnable? = null

    private fun startFling(x: Float, y: Float, vx0: Float, vy0: Float) {
        if (hypot(vx0, vy0) < 300f) return
        var vx = vx0; var vy = vy0
        var last = System.nanoTime()
        val r = object : Runnable {
            override fun run() {
                val now = System.nanoTime()
                val dt = (now - last) / 1e9f
                last = now
                val decay = Math.pow(0.04, dt.toDouble()).toFloat() // 1秒で 4% まで減衰
                vx *= decay; vy *= decay
                if (hypot(vx, vy) < 40f) { flingRunnable = null; return }
                conn()?.sendScroll(nx(x), ny(y), nx(vx * dt), ny(vy * dt))
                handler.postDelayed(this, 8)
            }
        }
        flingRunnable = r
        handler.post(r)
    }

    private fun stopFling() {
        flingRunnable?.let { handler.removeCallbacks(it) }
        flingRunnable = null
    }

    // --- S Pen / マウス ---
    private fun onPointer(e: MotionEvent): Boolean {
        val c = conn() ?: return true
        val x = nx(e.x); val y = ny(e.y)
        val secondary = e.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY) != 0
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (secondary) {
                c.sendMouse(Proto.M_RIGHT_CLICK, x, y)
            } else {
                c.sendMouse(Proto.M_LEFT_DOWN, x, y); penDown = true
            }
            MotionEvent.ACTION_MOVE -> if (penDown) c.sendMouse(Proto.M_LEFT_DRAG, x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (penDown) {
                c.sendMouse(Proto.M_LEFT_UP, x, y); penDown = false
            }
        }
        return true
    }

    fun onGenericMotion(e: MotionEvent): Boolean {
        val c = conn() ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                c.sendMouse(Proto.M_MOVE, nx(e.x), ny(e.y)); return true
            }
            MotionEvent.ACTION_SCROLL -> {
                val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                val h = e.getAxisValue(MotionEvent.AXIS_HSCROLL)
                if (abs(v) + abs(h) > 0f) c.sendScroll(nx(e.x), ny(e.y), -h * 0.03f, v * 0.03f)
                return true
            }
        }
        return false
    }
}
