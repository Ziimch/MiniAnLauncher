package com.example.minilauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.format.DateFormat
import android.view.View
import java.util.Calendar

// Jam gaya dot-matrix retro: setiap angka tersusun dari titik 5x7.
// Titik dua berkedip tiap detik (jika animasi aktif); berhenti sendiri saat layar/aktivitas tidak terlihat.
class DotClockView(context: Context) : View(context) {

    private val on = Paint(Paint.ANTI_ALIAS_FLAG)
    private val off = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cell = 9f * context.resources.displayMetrics.density

    var dotColor: Int = Color.WHITE
        set(v) {
            field = v
            invalidate()
        }

    var offColor: Int = Color.argb(28, 255, 255, 255)
        set(v) {
            field = v
            invalidate()
        }

    // true: titik dua berkedip (tick tiap detik). false: statis (tick tiap menit).
    var blink: Boolean = true
        set(v) {
            field = v
            restart()
        }

    private val digits = arrayOf(
        arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        arrayOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100")
    )

    // Posisi kolom awal: jam (2 angka), titik dua di kolom 12, menit (2 angka)
    private val starts = intArrayOf(0, 6, 14, 20)

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            val now = System.currentTimeMillis()
            val delay = if (blink) 1000L - now % 1000L + 5L else 60_000L - now % 60_000L + 30L
            postDelayed(this, delay)
        }
    }

    private fun restart() {
        removeCallbacks(tick)
        if (isAttachedToWindow && isShown && windowVisibility == VISIBLE) post(tick)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((25 * cell).toInt(), (7 * cell).toInt())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restart()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        restart()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        restart()
    }

    override fun onDraw(canvas: Canvas) {
        on.color = dotColor
        off.color = offColor
        val cal = Calendar.getInstance()
        val is24 = DateFormat.is24HourFormat(context)
        var h = if (is24) cal.get(Calendar.HOUR_OF_DAY) else cal.get(Calendar.HOUR)
        if (!is24 && h == 0) h = 12
        val m = cal.get(Calendar.MINUTE)
        val nums = intArrayOf(h / 10, h % 10, m / 10, m % 10)
        val r = cell * 0.36f
        val colonLit = !blink || (System.currentTimeMillis() / 1000L) % 2L == 0L

        for (row in 0 until 7) {
            for (col in 0 until 25) {
                val cx = col * cell + cell / 2f
                val cy = row * cell + cell / 2f
                var lit = false
                for (k in 0 until 4) {
                    val c = col - starts[k]
                    if (c in 0..4 && digits[nums[k]][row][c] == '1') lit = true
                }
                if (col == 12 && (row == 2 || row == 4)) lit = colonLit
                canvas.drawCircle(cx, cy, r, if (lit) on else off)
            }
        }
    }
}
