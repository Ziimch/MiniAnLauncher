package com.example.minilauncher

import android.content.Context
import android.widget.TextClock

// TextClock yang bisa menampilkan huruf kapital di semua versi Android
// (TextView.setAllCaps baru ada di Android 9).
class CapsTextClock(context: Context) : TextClock(context) {

    var caps: Boolean = false
        set(v) {
            field = v
            // paksa teks dibuat ulang dari format yang sama
            val f = format24Hour
            format24Hour = f
        }

    override fun setText(text: CharSequence?, type: android.widget.TextView.BufferType?) {
        super.setText(if (caps && text != null) text.toString().uppercase() else text, type)
    }
}
