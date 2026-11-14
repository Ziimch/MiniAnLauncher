package com.example.minilauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

// Layanan aksesibilitas ringan: hanya dipakai untuk mengunci layar (ketuk 2x).
class LockService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: LockService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}
}
