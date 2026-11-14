package com.example.minilauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.abs

// Equalizer pixel retro: 5 batang dari kotak kecil yang naik-turun bertahap (gaya chiptune).
// Berhenti sendiri saat tidak terlihat; statis jika animasi dimatikan / HP RAM kecil.
class PixelEqView(context: Context) : View(context) {

    private val bars = 5
    private val rows = 6
    private val density = context.resources.displayMetrics.density
    private val cell = 3.6f * density
    private val gap = 1.5f * density

    private val on = Paint(Paint.ANTI_ALIAS_FLAG)
    private val off = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rnd = java.util.Random()
    private val levels = intArrayOf(2, 4, 5, 3, 2)
    private val targets = IntArray(bars) { levels[it] }

    var pixelColor: Int = Color.WHITE
        set(v) {
            field = v
            invalidate()
        }

    var offColor: Int = Color.argb(30, 255, 255, 255)
        set(v) {
            field = v
            invalidate()
        }

    // false: batang turun ke dasar (tidak ada suara)
    var playing: Boolean = true

    // false: pola statis, tidak ada timer sama sekali
    var animated: Boolean = true
        set(v) {
            field = v
            restart()
        }

    private val step = object : Runnable {
        override fun run() {
            for (i in 0 until bars) {
                if (!playing) {
                    targets[i] = 1
                } else if (levels[i] == targets[i] || rnd.nextInt(4) == 0) {
                    // batang tengah lebih tinggi, tepi lebih rendah
                    val max = rows - abs(i - bars / 2)
                    targets[i] = 1 + rnd.nextInt(max)
                }
                val diff = targets[i] - levels[i]
                levels[i] += when {
                    diff > 0 -> minOf(diff, 2)
                    diff < 0 -> -minOf(-diff, 2)
                    else -> 0
                }
            }
            invalidate()
            postDelayed(this, 120L)
        }
    }

    private fun restart() {
        removeCallbacks(step)
        if (animated && isAttachedToWindow && isShown && windowVisibility == VISIBLE) post(step)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            (bars * cell + (bars - 1) * gap).toInt(),
            (rows * cell + (rows - 1) * gap).toInt()
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restart()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(step)
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
        on.color = pixelColor
        off.color = offColor
        val r = 0.7f * density
        for (b in 0 until bars) {
            for (row in 0 until rows) {
                val left = b * (cell + gap)
                // baris 0 = paling bawah
                val top = (rows - 1 - row) * (cell + gap)
                rect.set(left, top, left + cell, top + cell)
                canvas.drawRoundRect(rect, r, r, if (row < levels[b]) on else off)
            }
        }
    }
}
