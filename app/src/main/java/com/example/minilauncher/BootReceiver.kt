package com.example.minilauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Sengaja kosong. Keberadaannya membuat Mini Launcher muncul di daftar "Mulai otomatis"
// pada HP yang membatasinya (Vivo iManager, MIUI, ColorOS, dll.) sehingga bisa diizinkan
// berjalan saat HP menyala.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}
