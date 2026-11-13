package com.example.minilauncher

import android.app.Activity
import android.app.Dialog
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.exp

class MainActivity : Activity() {

    private data class App(val label: String, val pkg: String)

    private var apps = listOf<App>()
    private lateinit var root: FrameLayout
    private lateinit var header: LinearLayout
    private lateinit var greeting: TextView
    private lateinit var scrim: View
    private lateinit var clockTime: TextClock
    private lateinit var clockDate: TextClock
    private lateinit var pill: TextView
    private lateinit var bigLetter: TextView
    private var bigShown: Char? = null
    private lateinit var batteryText: TextView
    private lateinit var batteryFill: View
    private lateinit var batteryFillBg: GradientDrawable
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var strip: LinearLayout
    private var currentKey = -1
    private var lastSig: String? = null
    @Volatile private var loading = false
    private val iconExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var accentNow = Color.WHITE
    private var lowBattery = false

    // 0 = favorit, 1 = daftar huruf, 2 = aplikasi tersembunyi
    private var viewMode = 0
    private var viewLetter = 'A'

    private val prefs by lazy { getSharedPreferences("fav", MODE_PRIVATE) }

    private val KEY_FAV = "fav"
    private val KEY_HIDDEN = "hidden"
    private val keys: List<String> =
        listOf(KEY_FAV) + ('A'..'Z').map { it.toString() } + "#" + KEY_HIDDEN

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun readList(key: String): List<String> =
        (prefs.getString(key, "") ?: "").split(",").filter { it.isNotBlank() }

    private fun writeList(key: String, v: List<String>) {
        prefs.edit().putString(key, v.joinToString(",")).apply()
    }

    private var favCache: List<String>? = null
    private var hiddenCache: List<String>? = null

    private var favs: List<String>
        get() = favCache ?: readList("list").also { favCache = it }
        set(v) { favCache = v; writeList("list", v) }

    private var hidden: List<String>
        get() = hiddenCache ?: readList("hidden").also { hiddenCache = it }
        set(v) { hiddenCache = v; writeList("hidden", v) }

    private val clockNames = listOf("Tipis", "Tebal", "Ringkas")
    private val bgNames = listOf("Hitam polos", "Wallpaper redup", "Wallpaper jernih")
    private val iconNames = listOf("Tanpa ikon", "Ikon warna", "Monokrom")

    private val accentNames = listOf("Putih", "Biru", "Mint", "Oranye", "Pink", "Ungu")
    private val accentColors = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF8AB4FF.toInt(), 0xFF7EE0B5.toInt(),
        0xFFFFB070.toInt(), 0xFFFF8FB8.toInt(), 0xFFB79CFF.toInt()
    )

    private var accentIdx: Int
        get() = prefs.getInt("accent", 0).coerceIn(0, accentColors.size - 1)
        set(v) { prefs.edit().putInt("accent", v).apply() }

    private var clockStyle: Int
        get() = prefs.getInt("clock", 0).coerceIn(0, 2)
        set(v) { prefs.edit().putInt("clock", v).apply() }

    private var bgMode: Int
        get() = prefs.getInt("bg", 0).coerceIn(0, 2)
        set(v) { prefs.edit().putInt("bg", v).apply() }

    private var showInfo: Boolean
        get() = prefs.getBoolean("info", false)
        set(v) { prefs.edit().putBoolean("info", v).apply() }

    private var hideStatus: Boolean
        get() = prefs.getBoolean("hide_status", true)
        set(v) { prefs.edit().putBoolean("hide_status", v).apply() }

    private var haptic: Boolean
        get() = prefs.getBoolean("haptic", true)
        set(v) { prefs.edit().putBoolean("haptic", v).apply() }

    // 0 = tanpa ikon, 1 = ikon warna, 2 = ikon monokrom putih
    private var iconStyle: Int
        get() = prefs.getInt("icon", 2)
        set(v) { prefs.edit().putInt("icon", v).apply() }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        root.clipChildren = false
        root.setOnClickListener { showFavorites() }
        root.setOnLongClickListener { showSettings(); true }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val cut = insets.displayCutout?.safeInsetTop ?: 0
            v.setPadding(0, maxOf(insets.stableInsetTop, cut), 0, insets.stableInsetBottom)
            insets
        }

        scrim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, Color.argb(170, 0, 0, 0))
            )
            visibility = View.GONE
        }
        root.addView(scrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(460), Gravity.BOTTOM))

        // Beranda: sapaan kecil, jam besar tipis, tanggal, dan garis baterai
        header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(40), 0, 0)
            setOnLongClickListener { showSettings(); true }
        }
        greeting = TextView(this).apply {
            textSize = 12f
            letterSpacing = 0.2f
            setAllCaps(true)
            setTextColor(Color.argb(130, 255, 255, 255))
            setPadding(dp(4), 0, 0, dp(2))
        }
        header.addView(greeting)
        clockTime = TextClock(this).apply {
            includeFontPadding = false
            setTextColor(Color.WHITE)
        }
        clockDate = TextClock(this).apply {
            setTextColor(Color.argb(210, 255, 255, 255))
        }
        header.addView(clockTime)
        header.addView(clockDate)
        batteryFillBg = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(2).toFloat()
        }
        batteryFill = View(this).apply { background = batteryFillBg }
        val track = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(60, 255, 255, 255))
                cornerRadius = dp(2).toFloat()
            }
            addView(batteryFill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        batteryText = TextView(this).apply {
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(Color.argb(170, 255, 255, 255))
        }
        val batteryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(16), 0, dp(6))
            addView(track, LinearLayout.LayoutParams(dp(44), dp(3)).apply { marginEnd = dp(10) })
            addView(batteryText)
            setOnClickListener {
                if (viewMode != 0) showFavorites()
                else openSafe(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), "Tidak bisa membuka pengaturan baterai")
            }
            setOnLongClickListener { showSettings(); true }
        }
        header.addView(batteryRow)
        header.getChildAt(1).setOnClickListener { openClock() }
        header.getChildAt(1).setOnLongClickListener { showSettings(); true }
        header.getChildAt(2).setOnClickListener { openCalendar() }
        header.getChildAt(2).setOnLongClickListener { showSettings(); true }
        applyClockStyle()
        applyHeaderOptions()
        root.addView(header, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Daftar aplikasi
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(28))
            setOnClickListener { showFavorites() }
            setOnLongClickListener { showSettings(); true }
            addView(list, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = dp(215); bottomMargin = dp(32)
            marginStart = dp(28); marginEnd = dp(64)
        })

        // Strip huruf di kanan
        strip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }
        keys.forEach { k ->
            val v: View = when (k) {
                KEY_FAV -> iconKey(R.drawable.ic_star, "Favorit")
                KEY_HIDDEN -> iconKey(R.drawable.ic_lock, "Aplikasi tersembunyi")
                else -> TextView(this).apply {
                    text = k
                    textSize = 10.5f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                }
            }
            v.alpha = 0.75f
            strip.addView(v, LinearLayout.LayoutParams(dp(44), 0, 1f))
        }
        curW = FloatArray(keys.size)
        strip.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                v.systemGestureExclusionRects = listOf(Rect(0, 0, r - l, b - t))
            }
        }
        strip.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val pos = (e.y / v.height * keys.size).coerceIn(0f, keys.size - 0.001f)
                    touchPos = pos
                    startFrames()
                    val idx = pos.toInt()
                    if (idx != currentKey) {
                        currentKey = idx
                        if (haptic) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        showKey(idx)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    currentKey = -1
                    touchPos = -1f
                    startFrames()
                }
            }
            true
        }
        root.addView(strip, FrameLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.END
            topMargin = dp(120); bottomMargin = dp(32)
        })

        bigLetter = TextView(this).apply {
            textSize = 110f
            includeFontPadding = false
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setTextColor(Color.argb(70, 255, 255, 255))
            setPadding(dp(24), dp(44), 0, 0)
            alpha = 0f
        }
        root.addView(bigLetter, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        pill = TextView(this).apply {
            textSize = 14f
            maxWidth = dp(300)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2C2C2E"))
                cornerRadius = dp(22).toFloat()
            }
            alpha = 0f
        }
        root.addView(pill, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(28)
        })

        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })

        setContentView(root)
        applySystemUi()
        applyBackground()
        applyAccent()
    }

    // ---------- Gesek ke bawah: panel notifikasi ----------

    private var downX = 0f
    private var downY = 0f
    private var swipeTracking = false
    private var swipeDone = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                swipeDone = false
                // abaikan sentuhan yang dimulai di strip huruf
                swipeTracking = ev.rawX < resources.displayMetrics.widthPixels - dp(48)
            }
            MotionEvent.ACTION_MOVE -> {
                if (swipeTracking && !swipeDone) {
                    val dy = ev.rawY - downY
                    val dx = abs(ev.rawX - downX)
                    // hanya jika daftar sudah di posisi paling atas (tidak sedang di-scroll)
                    if (dy > dp(70) && dy > dx * 1.5f && !scroll.canScrollVertically(-1)) {
                        swipeDone = true
                        expandNotifications()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun expandNotifications() {
        val sbs = getSystemService("statusbar")
        val cls = try { Class.forName("android.app.StatusBarManager") } catch (e: Exception) { null }
        if (sbs != null && cls != null) {
            for (name in arrayOf("expandNotificationsPanel", "expand")) {
                try {
                    cls.getMethod(name).invoke(sbs)
                    return
                } catch (e: Exception) { /* coba cara berikutnya */ }
            }
        }
        toast("Tidak bisa membuka panel notifikasi")
    }

    private fun iconKey(res: Int, desc: String): View = ImageView(this).apply {
        setImageResource(res)
        contentDescription = desc
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(16), dp(5), dp(16), dp(5))
    }

    // Gaya jam: Tipis (bawaan), Tebal (serif seperti layar kunci), Ringkas
    private fun applyClockStyle() {
        when (clockStyle) {
            1 -> {
                clockTime.format24Hour = "HH.mm"
                clockTime.format12Hour = "hh.mm"
                clockTime.textSize = 76f
                clockTime.letterSpacing = 0f
                clockTime.typeface = Typeface.create("serif", Typeface.BOLD)
                clockDate.format24Hour = "EEE, d MMM"
                clockDate.format12Hour = "EEE, d MMM"
                clockDate.textSize = 18f
                clockDate.letterSpacing = 0f
                clockDate.setAllCaps(false)
                clockDate.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            2 -> {
                clockTime.format24Hour = "HH:mm"
                clockTime.format12Hour = "hh:mm"
                clockTime.textSize = 44f
                clockTime.letterSpacing = 0f
                clockTime.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                clockDate.format24Hour = "EEE, d MMM"
                clockDate.format12Hour = "EEE, d MMM"
                clockDate.textSize = 15f
                clockDate.letterSpacing = 0.02f
                clockDate.setAllCaps(false)
                clockDate.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            else -> {
                clockTime.format24Hour = "HH:mm"
                clockTime.format12Hour = "hh:mm"
                clockTime.textSize = 72f
                clockTime.letterSpacing = 0f
                clockTime.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                clockDate.format24Hour = "EEEE  ·  d MMMM"
                clockDate.format12Hour = "EEEE  ·  d MMMM"
                clockDate.textSize = 13f
                clockDate.letterSpacing = 0.16f
                clockDate.setAllCaps(true)
                clockDate.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        }
        clockDate.setPadding(dp(4), dp(4), 0, 0)
    }

    // Sapaan & baterai hanya tampil jika diaktifkan (bawaan: mati agar beranda simpel)
    private fun applyHeaderOptions() {
        val v = if (showInfo) View.VISIBLE else View.GONE
        greeting.visibility = v
        header.getChildAt(3).visibility = v
    }

    private fun applyAccent() {
        accentNow = accentColors[accentIdx]
        val a = accentNow
        val r = Color.red(a)
        val g = Color.green(a)
        val b = Color.blue(a)
        clockTime.setTextColor(a)
        clockDate.setTextColor(Color.argb(215, r, g, b))
        bigLetter.setTextColor(Color.argb(80, r, g, b))
        batteryFillBg.setColor(if (lowBattery) Color.parseColor("#FF6B6B") else a)
        for (i in 0 until strip.childCount) {
            (strip.getChildAt(i) as? TextView)?.setTextColor(Color.WHITE)
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
        val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        return Color.rgb(r, g, bl)
    }

    // Jangan sampai kesalahan kecil membuat launcher crash (lalu HP kembali ke launcher bawaan)
    private fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) { /* abaikan */ }
    }

    private fun isDefaultHome(): Boolean = try {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName
    } catch (e: Exception) { false }

    // Latar: hitam polos, atau wallpaper sistem dengan lapisan gelap
    @Suppress("DEPRECATION")
    private fun applyBackground() {
        if (bgMode == 0) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            root.setBackgroundColor(Color.TRANSPARENT)
            scrim.visibility = View.GONE
        } else {
            scrim.visibility = View.VISIBLE
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            root.setBackgroundColor(Color.argb(if (bgMode == 1) 150 else 55, 0, 0, 0))
        }
    }

    // Status bar disembunyikan (geser dari tepi atas untuk memunculkannya sementara)
    @Suppress("DEPRECATION")
    private fun applySystemUi() {
        var f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (hideStatus) {
            f = f or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        window.decorView.systemUiVisibility = f
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemUi()
    }

    @Volatile private var appsDirty = true

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            appsDirty = true
            val pkg = i?.data?.schemeSpecificPart
            if (pkg != null) {
                iconCache.remove("m:$pkg")
                iconCache.remove("c:$pkg")
                noMono.remove(pkg)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(pkgReceiver) } catch (e: Exception) { /* abaikan */ }
        iconExec.shutdownNow()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i != null) updateBattery(i)
        }
    }

    override fun onStart() {
        super.onStart()
        val sticky = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) updateBattery(sticky)
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(batteryReceiver) } catch (e: Exception) { /* abaikan */ }
    }

    private fun updateBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val pct = level * 100 / scale
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        batteryText.text = if (charging) "$pct%  ·  mengisi" else "$pct%"
        val lp = batteryFill.layoutParams
        lp.width = if (pct <= 0) 0 else maxOf(dp(2), dp(44) * pct / 100)
        batteryFill.layoutParams = lp
        lowBattery = pct <= 15 && !charging
        batteryFillBg.setColor(if (lowBattery) Color.parseColor("#FF6B6B") else accentNow)
    }

    private fun updateGreeting() {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        greeting.text = when {
            h < 4 -> "Selamat malam"
            h < 11 -> "Selamat pagi"
            h < 15 -> "Selamat siang"
            h < 18 -> "Selamat sore"
            else -> "Selamat malam"
        }
    }

    override fun onResume() {
        super.onResume()
        safely { updateGreeting() }
        safely { showFavorites() }
        loadAppsAsync()
        safely { precomputeIcons() }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        safely { showFavorites() } // tombol Home ditekan saat launcher sudah terbuka
    }

    // Saat sistem butuh memori, lepaskan cache ikon agar proses tidak mudah dimatikan
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= 40) iconCache.evictAll()          // proses di latar belakang
        else if (level >= 20) iconCache.trimToSize(2 * 1024 * 1024) // UI tersembunyi
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showFavorites()
    }

    private fun queryApps(): List<App> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = java.text.Collator.getInstance()
        collator.strength = java.text.Collator.PRIMARY
        return packageManager.queryIntentActivities(i, 0)
            .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    // Dimuat di thread terpisah supaya tidak membekukan layar (risiko ANR di HP yang lambat)
    private fun loadAppsAsync() {
        if ((!appsDirty && apps.isNotEmpty()) || loading) return
        loading = true
        Thread {
            val result = try { queryApps() } catch (e: Exception) { null }
            runOnUiThread {
                loading = false
                if (result != null) {
                    apps = result
                    appsDirty = false
                }
                safely {
                    if (prefs.getString("list", null) == null && apps.isNotEmpty()) {
                        favs = apps.take(5).map { it.pkg }
                    }
                    lastSig = null
                    renderCurrent()
                    precomputeIcons()
                }
            }
        }.start()
    }

    private fun keyOf(label: String): Char {
        val n = java.text.Normalizer.normalize(label.trim(), java.text.Normalizer.Form.NFD)
        val ch = n.firstOrNull()?.uppercaseChar() ?: return '#'
        return if (ch in 'A'..'Z') ch else '#'
    }

    private fun visibleApps(): List<App> {
        val h = hidden
        return apps.filter { it.pkg !in h }
    }

    // ---------- Animasi ----------

    // Efek lengkung ala Niagara. Satu loop per frame yang menggeser nilai pelan-pelan
    // menuju target, jadi halus dan ringan (tidak membuat puluhan animator tiap sentuhan).
    private var touchPos = -1f
    private var curW = FloatArray(0)
    private var act = 0f
    private var frameRunning = false

    private val frame = object : Runnable {
        override fun run() {
            val touching = touchPos >= 0f
            var moving = false
            val actDiff = (if (touching) 1f else 0f) - act
            act += actDiff * 0.3f
            if (abs(actDiff) > 0.004f) moving = true else act = if (touching) 1f else 0f
            val base = 0.75f - 0.4f * act
            for (i in 0 until strip.childCount) {
                val d = abs(i + 0.5f - touchPos)
                val target = if (touching) exp(-d * d / 6f) else 0f
                val diff = target - curW[i]
                curW[i] += diff * 0.4f
                if (abs(diff) > 0.004f) moving = true else curW[i] = target
                val w = curW[i]
                val c = strip.getChildAt(i)
                c.translationX = -dp(44) * w
                val sc = 1f + 0.9f * w
                c.scaleX = sc
                c.scaleY = sc
                c.alpha = base + (1f - base) * w
                if (c is TextView) c.setTextColor(blend(Color.WHITE, accentNow, w))
            }
            if (moving || touching) strip.postOnAnimation(this) else frameRunning = false
        }
    }

    private fun startFrames() {
        if (!frameRunning) {
            frameRunning = true
            strip.postOnAnimation(frame)
        }
    }

    // Baris muncul bergantian: fade + geser dari kanan
    private fun animateRows() {
        for (i in 0 until minOf(list.childCount, 8)) {
            val v = list.getChildAt(i)
            v.alpha = 0f
            v.translationX = dp(20).toFloat()
            v.animate()
                .alpha(1f).translationX(0f)
                .setStartDelay(minOf(i, 6) * 18L)
                .setDuration(170)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun showBigLetter(c: Char?) {
        if (c == null) {
            bigShown = null
            bigLetter.animate().alpha(0f).setDuration(150).start()
            return
        }
        if (bigShown != c) {
            bigLetter.text = c.toString()
            bigLetter.scaleX = 0.9f
            bigLetter.scaleY = 0.9f
        }
        bigShown = c
        bigLetter.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(140)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun showHeader(show: Boolean) {
        header.animate()
            .alpha(if (show) 1f else 0f)
            .translationY(if (show) 0f else -dp(10).toFloat())
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // ---------- Ikon monokrom ----------

    // Ikon disimpan sebagai bitmap kecil dalam cache berbatas 6 MB.
    // (Sebelumnya drawable ikon utuh ikut tersimpan -> memori besar.)
    private val iconCache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val noMono: MutableSet<String> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    private fun monoIcon(pkg: String): Bitmap? {
        iconCache.get("m:$pkg")?.let { return it }
        if (pkg in noMono) return null
        val bmp = try { buildMono(pkg) } catch (e: Exception) { null }
        if (bmp != null) iconCache.put("m:$pkg", bmp) else noMono.add(pkg)
        return bmp
    }

    private fun colorIcon(pkg: String): Bitmap? {
        iconCache.get("c:$pkg")?.let { return it }
        val d = packageManager.getApplicationIcon(pkg)
        val px = dp(32)
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, px, px)
        d.draw(Canvas(b))
        iconCache.put("c:$pkg", b)
        return b
    }

    @Volatile private var precomputing = false

    // Siapkan ikon di latar belakang pelan-pelan (favorit dulu) agar tidak menumpuk memori
    private fun precomputeIcons() {
        val style = iconStyle
        if (style == 0 || precomputing) return
        val fav = favs
        val pkgs = apps.map { it.pkg }.sortedBy { if (it in fav) 0 else 1 }
        precomputing = true
        Thread {
            Thread.currentThread().priority = Thread.MIN_PRIORITY
            try {
                for (p in pkgs) {
                    try {
                        if (style == 2) monoIcon(p) else colorIcon(p)
                    } catch (e: Exception) { /* abaikan */ }
                    Thread.sleep(6)
                }
            } finally {
                precomputing = false
            }
        }.start()
    }

    // Ubah ikon jadi siluet putih: latar ikon dibuang, hanya simbolnya yang tersisa
    private fun buildMono(pkg: String): Bitmap? {
        val s = 96
        val raw = packageManager.getApplicationIcon(pkg)
        val d = raw.constantState?.newDrawable()?.mutate() ?: raw
        val src = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, s, s)
        d.draw(Canvas(src))
        val px = IntArray(s * s)
        src.getPixels(px, 0, s, 0, 0, s, s)

        // Warna latar = nilai tengah dari beberapa titik di tepi ikon
        val pts = arrayOf(
            intArrayOf(50, 10), intArrayOf(50, 90), intArrayOf(10, 50), intArrayOf(90, 50),
            intArrayOf(22, 22), intArrayOf(78, 22), intArrayOf(22, 78), intArrayOf(78, 78)
        )
        val rs = ArrayList<Int>()
        val gs = ArrayList<Int>()
        val bs = ArrayList<Int>()
        for (p in pts) {
            val c = px[(p[1] * s / 100) * s + (p[0] * s / 100)]
            if (Color.alpha(c) > 220) {
                rs.add(Color.red(c)); gs.add(Color.green(c)); bs.add(Color.blue(c))
            }
        }
        val hasBg = rs.size >= 5
        val bgR = if (hasBg) rs.sorted()[rs.size / 2] else 0
        val bgG = if (hasBg) gs.sorted()[gs.size / 2] else 0
        val bgB = if (hasBg) bs.sorted()[bs.size / 2] else 0

        val out = IntArray(s * s)
        var minX = s; var minY = s; var maxX = -1; var maxY = -1
        var on = 0
        for (y in 0 until s) {
            for (x in 0 until s) {
                val c = px[y * s + x]
                val a0 = Color.alpha(c)
                val a = when {
                    a0 == 0 -> 0
                    hasBg -> {
                        val dist = maxOf(
                            abs(Color.red(c) - bgR),
                            abs(Color.green(c) - bgG),
                            abs(Color.blue(c) - bgB)
                        )
                        (((dist - 30) / 80f).coerceIn(0f, 1f) * a0).toInt()
                    }
                    else -> a0
                }
                out[y * s + x] = Color.argb(a, 255, 255, 255)
                if (a > 60) {
                    on++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        // Kosong atau terlalu penuh (jadi gumpalan putih) -> tidak ditampilkan
        if (maxX < 0 || on > (s * s * 0.5f).toInt()) return null

        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val side = maxOf(bw, bh)
        val dest = side + side / 6
        val glyph = Bitmap.createBitmap(out, s, s, Bitmap.Config.ARGB_8888)
        val result = Bitmap.createBitmap(dest, dest, Bitmap.Config.ARGB_8888)
        val left = (dest - bw) / 2
        val top = (dest - bh) / 2
        Canvas(result).drawBitmap(
            glyph,
            Rect(minX, minY, maxX + 1, maxY + 1),
            Rect(left, top, left + bw, top + bh),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        src.recycle()
        glyph.recycle()
        val fin = Bitmap.createScaledBitmap(result, 64, 64, true)
        if (fin !== result) result.recycle()
        return fin
    }

    // ---------- Aksi ----------

    private fun launch(app: App, v: View) {
        try {
            val i = packageManager.getLaunchIntentForPackage(app.pkg) ?: return
            val opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height)
            startActivity(i, opts.toBundle())
        } catch (e: Exception) {
            toast("Tidak bisa membuka ${app.label}")
        }
    }

    private val hidePill = Runnable {
        pill.animate().alpha(0f).translationY(dp(12).toFloat()).setDuration(200).start()
    }

    private fun toast(msg: String) {
        pill.removeCallbacks(hidePill)
        pill.animate().cancel()
        pill.text = msg
        pill.translationY = dp(12).toFloat()
        pill.animate()
            .alpha(1f).translationY(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
        pill.postDelayed(hidePill, 2000)
    }

    private fun openSafe(intent: Intent, err: String = "Tidak bisa membuka pengaturan") {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast(err)
        }
    }

    private fun openClock() {
        if (viewMode != 0) { showFavorites(); return }
        openSafe(Intent(AlarmClock.ACTION_SHOW_ALARMS), "Aplikasi jam tidak ditemukan")
    }

    private fun openCalendar() {
        if (viewMode != 0) { showFavorites(); return }
        openSafe(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
            "Aplikasi kalender tidak ditemukan"
        )
    }

    // Dialog gelap dengan sudut membulat
    private fun showStyled(b: AlertDialog.Builder) {
        val d = b.create()
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#1C1C1E"))
            cornerRadius = dp(22).toFloat()
        }
        d.window?.setBackgroundDrawable(InsetDrawable(bg, dp(16)))
        d.show()
    }

    // Pengganti ikon monokrom yang gagal dibuat: lingkaran dengan huruf awal
    private fun placeholderIcon(label: String): Bitmap {
        val s = 64
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        c.drawCircle(s / 2f, s / 2f, s / 2f - 4f, p)
        p.style = Paint.Style.FILL
        p.textSize = 32f
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val ch = label.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        c.drawText(ch, s / 2f, s / 2f + 11f, p)
        return b
    }

    // Ikon dari cache langsung dipasang; jika belum ada, dibuat di latar belakang lalu muncul halus
    private fun bindIcon(iv: ImageView, app: App, mode: Int) {
        val key = (if (mode == 2) "m:" else "c:") + app.pkg
        val cached = iconCache.get(key)
        if (cached != null) {
            iv.setImageBitmap(cached)
            return
        }
        if (mode == 2 && app.pkg in noMono) {
            iv.setImageBitmap(placeholderIcon(app.label))
            return
        }
        iv.tag = app.pkg
        iv.alpha = 0f
        try {
            iconExec.execute {
                val b = try {
                    if (mode == 2) monoIcon(app.pkg) else colorIcon(app.pkg)
                } catch (e: Throwable) { null }
                iv.post {
                    if (iv.tag == app.pkg) {
                        iv.setImageBitmap(b ?: if (mode == 2) placeholderIcon(app.label) else null)
                        iv.animate().alpha(1f).setDuration(120).start()
                    }
                }
            }
        } catch (e: Exception) {
            iv.alpha = 1f
        }
    }

    private fun row(app: App, iconMode: Int, textSp: Float): View {
        val padV = dp(if (iconMode != 0) 9 else 10)
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, padV, 0, padV)
            minimumHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        if (iconMode != 0) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            bindIcon(iv, app, iconMode)
            r.addView(iv, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(20) })
        }
        r.addView(TextView(this).apply {
            text = app.label
            textSize = textSp
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        r.setOnClickListener { launch(app, r) }
        r.setOnLongClickListener { showMenu(app); true }
        r.post { r.pivotX = 0f; r.pivotY = r.height / 2f }
        r.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().setStartDelay(0).alpha(0.55f).scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().setStartDelay(0).alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            false
        }
        return r
    }

    // ---------- Lembar menu (bottom sheet) ----------

    // icon: 0 = tanpa ikon, -1 = ruang kosong sebesar ikon, selain itu id drawable
    private class Entry(
        val icon: Int,
        val label: String,
        val value: String? = null,
        val danger: Boolean = false,
        val action: () -> Unit
    )

    private fun pressBg(): Drawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply {
            setColor(Color.parseColor("#2C2C2E"))
            cornerRadius = dp(18).toFloat()
        })
        addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    }

    @Suppress("DEPRECATION")
    private fun showSheet(title: String, titleIcon: Drawable?, entries: List<Entry>) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = dp(28).toFloat()
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
        }
        if (titleIcon != null) {
            head.addView(ImageView(this).apply { setImageDrawable(titleIcon) },
                LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
        }
        head.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        card.addView(head)
        card.addView(View(this).apply { setBackgroundColor(Color.parseColor("#2C2C2E")) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(dp(8), 0, dp(8), dp(6))
            })

        entries.forEach { e ->
            val color = if (e.danger) Color.parseColor("#FF6B6B") else Color.WHITE
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(54)
                setPadding(dp(16), 0, dp(16), 0)
                background = pressBg()
                setOnClickListener {
                    dlg.dismiss()
                    e.action()
                }
            }
            if (e.icon != 0) {
                val iv = ImageView(this)
                if (e.icon > 0) {
                    iv.setImageResource(e.icon)
                    iv.setColorFilter(if (e.danger) color else Color.argb(230, 255, 255, 255))
                }
                row.addView(iv, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(18) })
            }
            row.addView(TextView(this).apply {
                text = e.label
                textSize = 16f
                setTextColor(color)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (e.value != null) {
                row.addView(TextView(this).apply {
                    text = e.value
                    textSize = 14f
                    setTextColor(Color.argb(150, 255, 255, 255))
                })
            }
            card.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val sv = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            addView(card, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val wrap = FrameLayout(this).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(sv, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dlg.setContentView(wrap)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.7f)
            // tidak fokus dulu agar status bar tidak muncul sekejap saat dialog tampil
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
        }
        dlg.setCanceledOnTouchOutside(true)
        card.alpha = 0f
        card.translationY = dp(40).toFloat()
        dlg.show()
        dlg.window?.let { w ->
            w.decorView.systemUiVisibility = window.decorView.systemUiVisibility
            w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        card.animate()
            .alpha(1f).translationY(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // Menu tekan-lama pada aplikasi
    private fun showMenu(app: App) {
        val isHidden = app.pkg in hidden
        val isFav = app.pkg in favs
        val es = mutableListOf<Entry>()
        if (!isHidden) {
            if (isFav) {
                es.add(Entry(R.drawable.ic_star_border, "Hapus dari favorit") {
                    favs = favs - app.pkg
                    renderCurrent()
                })
            } else {
                es.add(Entry(R.drawable.ic_star, "Tambah ke favorit") {
                    favs = favs + app.pkg
                    toast("${app.label} ditambah ke favorit")
                })
            }
            es.add(Entry(R.drawable.ic_visibility_off, "Sembunyikan") {
                hidden = hidden + app.pkg
                favs = favs - app.pkg
                toast("${app.label} disembunyikan. Buka lewat ikon gembok di bawah huruf")
                renderCurrent()
            })
        } else {
            es.add(Entry(R.drawable.ic_visibility, "Tampilkan kembali") {
                hidden = hidden - app.pkg
                toast("${app.label} ditampilkan kembali")
                renderCurrent()
            })
        }
        es.add(Entry(R.drawable.ic_info, "Info aplikasi") {
            openSafe(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.pkg)))
        })
        es.add(Entry(R.drawable.ic_delete, "Copot pemasangan", danger = true) {
            openSafe(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)),
                "Tidak bisa mencopot ${app.label}")
        })
        val icon = try {
            colorIcon(app.pkg)?.let { BitmapDrawable(resources, it) }
        } catch (e: Exception) { null }
        showSheet(app.label, icon, es)
    }

    // Pengaturan (tekan lama jam / area kosong)
    private fun showSettings() {
        fun onOff(b: Boolean) = if (b) "Aktif" else "Mati"
        showSheet("Pengaturan", null, listOf(
            Entry(0, "Gaya jam", clockNames[clockStyle]) {
                pickOption("Gaya jam", clockNames, clockStyle) {
                    clockStyle = it
                    applyClockStyle()
                }
            },
            Entry(0, "Latar", bgNames[bgMode]) {
                pickOption("Latar", bgNames, bgMode) {
                    bgMode = it
                    applyBackground()
                }
            },
            Entry(0, "Warna aksen", accentNames[accentIdx]) {
                pickOption("Warna aksen", accentNames, accentIdx) {
                    accentIdx = it
                    applyAccent()
                }
            },
            Entry(0, "Gaya ikon", iconNames[iconStyle.coerceIn(0, 2)]) {
                pickOption("Gaya ikon", iconNames, iconStyle) {
                    iconStyle = it
                    renderCurrent()
                    if (it != 0) precomputeIcons()
                }
            },
            Entry(0, "Sapaan & baterai", onOff(showInfo)) {
                showInfo = !showInfo
                applyHeaderOptions()
                showSettings()
            },
            Entry(0, "Getaran saat geser huruf", onOff(haptic)) {
                haptic = !haptic
                showSettings()
            },
            Entry(0, "Sembunyikan status bar", onOff(hideStatus)) {
                hideStatus = !hideStatus
                applySystemUi()
                showSettings()
            },
            Entry(0, "Bersihkan cache", cacheSizeText()) { clearCache() },
            Entry(0, "Ganti wallpaper") {
                openSafe(
                    Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Pilih wallpaper"),
                    "Tidak bisa membuka pilihan wallpaper"
                )
            },
            Entry(0, "Launcher default", if (isDefaultHome()) "Aktif" else "Belum diatur") { openSafe(Intent(Settings.ACTION_HOME_SETTINGS)) }
        ))
    }

    // ---------- Cache ----------

    private fun dirSize(dir: java.io.File?): Long =
        dir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    private fun formatSize(bytes: Long): String =
        if (bytes < 1024L * 1024L) "${bytes / 1024} KB"
        else "%.1f MB".format(bytes / 1048576.0)

    private fun cacheBytes(): Long =
        iconCache.size().toLong() + dirSize(cacheDir) + dirSize(externalCacheDir)

    private fun cacheSizeText(): String = formatSize(cacheBytes())

    // Membersihkan cache launcher ini saja (ikon di memori + file cache).
    // Android tidak mengizinkan aplikasi biasa menghapus cache aplikasi lain.
    private fun clearCache() {
        val before = cacheBytes()
        iconCache.evictAll()
        noMono.clear()
        try {
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        } catch (e: Exception) { /* abaikan */ }
        lastSig = null
        renderCurrent()
        precomputeIcons()
        toast("Cache dibersihkan · ${formatSize(before)}")
    }

    // Daftar pilihan dengan centang; setelah memilih kembali ke Pengaturan
    private fun pickOption(title: String, names: List<String>, current: Int, onPick: (Int) -> Unit) {
        showSheet(title, null, names.mapIndexed { i, n ->
            Entry(if (i == current) R.drawable.ic_check else -1, n) {
                onPick(i)
                showSettings()
            }
        })
    }

    // ---------- Tampilan daftar ----------

    private fun showKey(idx: Int) {
        safely {
            when (val k = keys[idx]) {
                KEY_FAV -> showFavorites()
                KEY_HIDDEN -> showHidden()
                else -> showLetter(k[0])
            }
        }
    }

    private fun renderCurrent() {
        when (viewMode) {
            1 -> showLetter(viewLetter)
            2 -> showHidden()
            else -> showFavorites()
        }
    }

    private fun showFavorites() {
        viewMode = 0
        showHeader(true)
        showBigLetter(null)
        val sig = "f|" + favs.joinToString(",") + "|" + hidden.size + "|" + iconStyle + "|" + apps.size
        if (sig == lastSig && list.childCount > 0) return
        lastSig = sig
        list.gravity = Gravity.BOTTOM
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        val vis = visibleApps()
        val items = favs.mapNotNull { p -> vis.find { it.pkg == p } }
        if (items.isEmpty() && apps.isNotEmpty()) {
            list.addView(TextView(this).apply {
                text = "Geser huruf di kanan, lalu tekan lama aplikasi untuk menambah favorit"
                textSize = 14f
                setTextColor(Color.LTGRAY)
            })
        }
        val mode = iconStyle
        items.forEach { app ->
            list.addView(row(app, mode, if (mode == 0) 24f else 20f))
        }
        animateRows()
    }

    private fun showLetter(c: Char) {
        val wasLetter = viewMode == 1
        viewMode = 1
        viewLetter = c
        showHeader(false)
        showBigLetter(c)
        val sig = "l|$c|" + hidden.joinToString(",") + "|" + iconStyle + "|" + apps.size
        if (sig == lastSig && list.childCount > 0) return
        lastSig = sig
        list.gravity = Gravity.CENTER_VERTICAL
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        val mode = iconStyle
        val matches = visibleApps().filter { keyOf(it.label) == c }
        if (matches.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Tidak ada aplikasi"
                textSize = 14f
                setTextColor(Color.GRAY)
            })
        }
        matches.forEach { app -> list.addView(row(app, mode, 18f)) }
        if (!wasLetter) animateRows()
    }

    private fun showHidden() {
        viewMode = 2
        showHeader(false)
        showBigLetter(null)
        val sig = "h|" + hidden.joinToString(",") + "|" + iconStyle + "|" + apps.size
        if (sig == lastSig && list.childCount > 0) return
        lastSig = sig
        list.gravity = Gravity.CENTER_VERTICAL
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        list.addView(TextView(this).apply {
            text = "Aplikasi tersembunyi"
            textSize = 14f
            letterSpacing = 0.1f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(8))
        })
        val h = hidden
        val items = apps.filter { it.pkg in h }
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Belum ada. Tekan lama aplikasi lalu pilih Sembunyikan."
                textSize = 14f
                setTextColor(Color.LTGRAY)
            })
        }
        val mode = iconStyle
        items.forEach { app -> list.addView(row(app, mode, 18f)) }
        animateRows()
    }
}
