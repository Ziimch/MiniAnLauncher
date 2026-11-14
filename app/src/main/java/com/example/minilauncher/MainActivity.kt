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
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import kotlin.math.abs
import kotlin.math.exp

class MainActivity : Activity() {

    private data class App(val label: String, val pkg: String)

    private var apps = listOf<App>()
    private lateinit var root: FrameLayout
    private lateinit var header: LinearLayout
    private lateinit var greeting: TextView
    private lateinit var scrim: View
    private lateinit var clockMin: TextClock
    private lateinit var dotClock: DotClockView
    private lateinit var nowPlaying: LinearLayout
    private lateinit var nowText: TextView
    private lateinit var nowEq: PixelEqView
    private lateinit var searchPill: LinearLayout
    private lateinit var searchBox: EditText
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
    private var letterBase = Color.WHITE
    private val searchPillBg = GradientDrawable()
    private val pillBg = GradientDrawable()
    private var lowBattery = false
    private var navBottom = 0
    private var lastTapAt = 0L
    private var searchResults: List<App> = emptyList()
    private var nameCache: MutableMap<String, String>? = null
    private val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
    private var sessionMgr: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private var nowController: MediaController? = null

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

    private val clockNames = listOf("Ringkas", "Bertumpuk", "Dot-Matrix", "Serif")
    private val bgNames = listOf("Hitam polos", "Wallpaper redup", "Wallpaper jernih")
    private val iconNames = listOf("Tanpa ikon", "Ikon warna", "Monokrom")

    private val accentNamesClassic = listOf("Putih", "Biru", "Mint", "Oranye", "Pink", "Ungu")
    private val accentNamesCozy = listOf("Amber", "Terakota", "Sage", "Krem", "Mawar", "Biru debu")
    private val themeNames = listOf("Klasik hitam", "Retro cozy")
    private val accentColorsClassic = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF8AB4FF.toInt(), 0xFF7EE0B5.toInt(),
        0xFFFFB070.toInt(), 0xFFFF8FB8.toInt(), 0xFFB79CFF.toInt()
    )
    private val accentColorsCozy = intArrayOf(
        0xFFFFB45A.toInt(), 0xFFE8825F.toInt(), 0xFFA8C69F.toInt(),
        0xFFF3E7D3.toInt(), 0xFFE59BA6.toInt(), 0xFF8FB3C9.toInt()
    )

    private fun accentNames() = if (cozy) accentNamesCozy else accentNamesClassic
    private fun accentColors() = if (cozy) accentColorsCozy else accentColorsClassic

    // ---------- Tema: Klasik hitam / Retro cozy (krem hangat, amber, font mono) ----------
    private var themeIdx: Int
        get() = prefs.getInt("theme", 0).coerceIn(0, 1)
        set(v) { prefs.edit().putInt("theme", v).apply() }

    private val cozy: Boolean get() = themeIdx == 1

    private fun cText(alpha: Int = 255): Int =
        if (cozy) Color.argb(alpha, 243, 231, 211) else Color.argb(alpha, 255, 255, 255)

    private fun cSheet(alpha: Int): Int =
        if (cozy) Color.argb(alpha, 38, 29, 22) else Color.argb(alpha, 28, 28, 30)

    private fun cSurface(): Int = Color.parseColor(if (cozy) "#3A2D22" else "#2C2C2E")

    private fun cStroke(): Int =
        if (cozy) Color.argb(48, 243, 200, 140) else Color.argb(26, 255, 255, 255)

    private fun cDanger(): Int = Color.parseColor(if (cozy) "#E8745A" else "#FF6B6B")

    private fun cBg(): Int = if (cozy) Color.parseColor("#110D09") else Color.BLACK

    private fun fontBody(): Typeface =
        if (cozy) Typeface.create("monospace", Typeface.NORMAL)
        else Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun applyTheme() {
        applyClockStyle()
        applyBackground()
        applyAccent()
        lastSig = null
        renderCurrent()
    }

    private var accentIdx: Int
        get() = prefs.getInt("accent", 0).coerceIn(0, 5)
        set(v) { prefs.edit().putInt("accent", v).apply() }

    private var clockStyle: Int
        get() = prefs.getInt("clock", 0).coerceIn(0, 3)
        set(v) { prefs.edit().putInt("clock", v).apply() }

    private var bgMode: Int
        get() = prefs.getInt("bg", 0).coerceIn(0, 2)
        set(v) { prefs.edit().putInt("bg", v).apply() }

    private var nowPlayingOn: Boolean
        get() = prefs.getBoolean("nowplay", false)
        set(v) { prefs.edit().putBoolean("nowplay", v).apply() }

    private var dtap: Boolean
        get() = prefs.getBoolean("dtap", false)
        set(v) { prefs.edit().putBoolean("dtap", v).apply() }

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
        root.setOnClickListener { onEmptyTap() }
        root.setOnLongClickListener { showSettings(); true }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val cut = insets.displayCutout?.safeInsetTop ?: 0
            navBottom = insets.stableInsetBottom
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
        clockMin = TextClock(this).apply {
            includeFontPadding = false
            setTextColor(Color.WHITE)
            visibility = View.GONE
        }
        dotClock = DotClockView(this).apply { visibility = View.GONE }
        val timeBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(clockTime)
            addView(clockMin, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = -dp(14) })
            addView(dotClock, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(6) })
        }
        clockDate = TextClock(this).apply {
            setTextColor(Color.argb(210, 255, 255, 255))
        }
        header.addView(timeBox)
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
        nowEq = PixelEqView(this)
        nowText = TextView(this).apply {
            textSize = 12f
            letterSpacing = 0.04f
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(10))
            setTextColor(Color.argb(200, 255, 255, 255))
        }
        nowPlaying = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), 0, 0)
            visibility = View.GONE
            addView(nowEq, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(10) })
            addView(nowText, LinearLayout.LayoutParams(
                dp(210), ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnClickListener {
                try { nowController?.sessionActivity?.send() } catch (e: Exception) { /* abaikan */ }
            }
            setOnLongClickListener { showSettings(); true }
        }
        header.addView(nowPlaying, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
            setOnClickListener { onEmptyTap() }
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

        searchBox = EditText(this).apply {
            hint = "Cari aplikasi"
            setHintTextColor(Color.argb(120, 255, 255, 255))
            setTextColor(Color.WHITE)
            textSize = 18f
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = null
            setPadding(0, 0, 0, 0)
        }
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) {
                if (viewMode == 3) safely { renderSearch() }
            }
        })
        searchBox.setOnEditorActionListener { _, actionId, event ->
            val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                if (searchResults.size == 1) {
                    launch(searchResults[0], list.getChildAt(0) ?: root)
                }
                true
            } else {
                false
            }
        }
        searchPill = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = searchPillBg
            setPadding(dp(20), 0, dp(20), 0)
            visibility = View.GONE
            addView(searchBox, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        root.addView(searchPill, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ).apply {
            marginStart = dp(24); marginEnd = dp(64); topMargin = dp(56)
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
            background = pillBg
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
                    if (dy > dp(70) && dy > dx * 1.5f && viewMode != 3 &&
                        !scroll.canScrollVertically(-1)
                    ) {
                        swipeDone = true
                        expandNotifications()
                    } else if (-dy > dp(70) && -dy > dx * 1.5f && viewMode == 0 &&
                        !scroll.canScrollVertically(1)
                    ) {
                        swipeDone = true
                        openSearch()
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

    // Gaya jam: Ringkas, Bertumpuk, Dot-Matrix, Serif
    private fun applyClockStyle() {
        val st = clockStyle
        clockMin.visibility = if (st == 1) View.VISIBLE else View.GONE
        dotClock.visibility = if (st == 2) View.VISIBLE else View.GONE
        clockTime.visibility = if (st == 2) View.GONE else View.VISIBLE
        clockTime.letterSpacing = 0f
        when (st) {
            1 -> {
                clockTime.format24Hour = "HH"
                clockTime.format12Hour = "hh"
                clockMin.format24Hour = "mm"
                clockMin.format12Hour = "mm"
                clockTime.textSize = 66f
                clockMin.textSize = 66f
                clockTime.typeface = Typeface.create(if (cozy) "monospace" else "sans-serif", Typeface.BOLD)
                clockMin.typeface = Typeface.create(if (cozy) "monospace" else "sans-serif-light", Typeface.NORMAL)
            }
            3 -> {
                clockTime.format24Hour = "HH.mm"
                clockTime.format12Hour = "hh.mm"
                clockTime.textSize = 76f
                clockTime.typeface = Typeface.create("serif", Typeface.BOLD)
            }
            else -> {
                clockTime.format24Hour = "HH:mm"
                clockTime.format12Hour = "hh:mm"
                clockTime.textSize = if (cozy) 62f else 72f
                clockTime.typeface = Typeface.create(if (cozy) "monospace" else "sans-serif-thin", Typeface.NORMAL)
            }
        }
        if (st == 3) {
            clockDate.format24Hour = "EEE, d MMM"
            clockDate.format12Hour = "EEE, d MMM"
            clockDate.textSize = 18f
            clockDate.letterSpacing = 0f
            clockDate.setAllCaps(false)
            clockDate.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        } else {
            clockDate.format24Hour = "EEEE  ·  d MMMM"
            clockDate.format12Hour = "EEEE  ·  d MMMM"
            clockDate.textSize = 13f
            clockDate.letterSpacing = 0.16f
            clockDate.setAllCaps(true)
            clockDate.typeface = fontBody()
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
        accentNow = accentColors()[accentIdx]
        val a = accentNow
        val r = Color.red(a)
        val g = Color.green(a)
        val b = Color.blue(a)
        val mono: Typeface? = if (cozy) Typeface.create("monospace", Typeface.NORMAL) else null
        val dotOff = if (cozy) Color.argb(42, 255, 176, 90) else Color.argb(28, 255, 255, 255)
        letterBase = cText()

        clockTime.setTextColor(if (cozy) cText() else a)
        clockMin.setTextColor(if (cozy) cText(120) else Color.argb(120, r, g, b))
        clockDate.setTextColor(if (cozy) a else Color.argb(215, r, g, b))
        dotClock.dotColor = a
        dotClock.offColor = dotOff
        nowEq.pixelColor = a
        nowEq.offColor = dotOff
        nowText.setTextColor(Color.argb(200, r, g, b))
        nowText.typeface = mono ?: Typeface.DEFAULT
        bigLetter.setTextColor(Color.argb(80, r, g, b))
        bigLetter.typeface = mono ?: Typeface.create("sans-serif-thin", Typeface.NORMAL)
        greeting.setTextColor(cText(130))
        greeting.typeface = mono ?: Typeface.DEFAULT
        batteryText.setTextColor(cText(170))
        batteryText.typeface = mono ?: Typeface.DEFAULT
        batteryFillBg.setColor(if (lowBattery) cDanger() else a)

        searchBox.setTextColor(cText())
        searchBox.setHintTextColor(cText(120))
        searchBox.typeface = mono ?: Typeface.DEFAULT
        searchPillBg.setColor(cSheet(255))
        searchPillBg.cornerRadius = dp(26).toFloat()
        searchPillBg.setStroke(dp(1), cStroke())
        pillBg.setColor(cSurface())
        pillBg.cornerRadius = dp(22).toFloat()
        pill.setTextColor(cText())

        for (i in 0 until strip.childCount) {
            (strip.getChildAt(i) as? TextView)?.let {
                it.setTextColor(letterBase)
                it.typeface = mono ?: Typeface.DEFAULT
            }
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
            window.setBackgroundDrawable(ColorDrawable(cBg()))
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
                tintable.remove(pkg)
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
        safely { startNowPlaying() }
    }

    override fun onStop() {
        super.onStop()
        safely { stopNowPlaying() }
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
        batteryFillBg.setColor(if (lowBattery) cDanger() else accentNow)
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
                if (c is TextView) c.setTextColor(blend(letterBase, accentNow, w))
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
    // pkg yang ikonnya berupa lapisan monokrom (diwarnai sesuai aksen saat ditampilkan)
    private val tintable: MutableSet<String> =
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

    // Ikon monokrom, 3 lapis:
    // a) Android 13+: lapisan monokrom ikon adaptif (diwarnai aksen saat ditampilkan)
    // b) selain itu: ikon asli diubah ke grayscale kontras tinggi agar seragam
    // c) gagal total (return null / exception): pemanggil memakai lingkaran inisial
    private fun buildMono(pkg: String): Bitmap? {
        val raw = packageManager.getApplicationIcon(pkg)
        val d = raw.constantState?.newDrawable()?.mutate() ?: raw
        val s = 96

        if (Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) {
            val m = d.monochrome
            if (m != null) {
                val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
                val inset = (s * 0.25f).toInt() // hanya 66% bagian tengah layer yang terlihat
                m.setBounds(-inset, -inset, s + inset, s + inset)
                m.draw(Canvas(b))
                tintable.add(pkg)
                val fin = Bitmap.createScaledBitmap(b, 64, 64, true)
                if (fin !== b) b.recycle()
                return fin
            }
        }

        val src = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, s, s)
        d.draw(Canvas(src))
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val k = 1.6f
        val t = (-0.5f * k + 0.5f) * 255f + 38f // kontras + sedikit terang agar terlihat di latar hitam
        cm.postConcat(ColorMatrix(floatArrayOf(
            k, 0f, 0f, 0f, t,
            0f, k, 0f, 0f, t,
            0f, 0f, k, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        val fin = Bitmap.createScaledBitmap(out, 64, 64, true)
        if (fin !== out) out.recycle()
        return fin
    }

    private fun tintFor(iv: ImageView, pkg: String, mode: Int) {
        if (mode == 2 && pkg in tintable) iv.setColorFilter(accentNow) else iv.clearColorFilter()
    }

    // ---------- Aksi ----------

    private fun launch(app: App, v: View) {
        try {
            val i = packageManager.getLaunchIntentForPackage(app.pkg) ?: return
            val opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height)
            startActivity(i, opts.toBundle())
        } catch (e: Exception) {
            toast("Tidak bisa membuka ${nm(app)}")
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
        p.color = cText()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        c.drawCircle(s / 2f, s / 2f, s / 2f - 4f, p)
        p.style = Paint.Style.FILL
        p.textSize = 32f
        p.textAlign = Paint.Align.CENTER
        p.typeface = fontBody()
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
            tintFor(iv, app.pkg, mode)
            return
        }
        if (mode == 2 && app.pkg in noMono) {
            iv.setImageBitmap(placeholderIcon(nm(app)))
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
                        iv.setImageBitmap(b ?: if (mode == 2) placeholderIcon(nm(app)) else null)
                        tintFor(iv, app.pkg, mode)
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
            text = nm(app)
            textSize = if (cozy) textSp - 2f else textSp
            setTextColor(cText())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = fontBody()
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

    private fun blurSupported(): Boolean = Build.VERSION.SDK_INT >= 31 && try {
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).isCrossWindowBlurEnabled
    } catch (e: Exception) { false }

    // Latar lembar: transparan (kaca) jika blur didukung, gelap pekat jika tidak; garis tepi 1dp putih 10%
    private fun sheetBg(blur: Boolean) = GradientDrawable().apply {
        setColor(if (blur) cSheet(170) else cSheet(245))
        cornerRadius = dp(28).toFloat()
        setStroke(dp(1), cStroke())
    }

    private fun pressBg(): Drawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply {
            setColor(cSurface())
            cornerRadius = dp(18).toFloat()
        })
        addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    }

    @Suppress("DEPRECATION")
    private fun showSheet(title: String, titleIcon: Drawable?, entries: List<Entry>) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val blur = blurSupported()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = sheetBg(blur)
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
            setTextColor(cText())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = fontBody()
        })
        card.addView(head)
        card.addView(View(this).apply { setBackgroundColor(cSurface()) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(dp(8), 0, dp(8), dp(6))
            })

        entries.forEach { e ->
            val color = if (e.danger) cDanger() else cText()
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
                    iv.setColorFilter(if (e.danger) color else cText(230))
                }
                row.addView(iv, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(18) })
            }
            row.addView(TextView(this).apply {
                text = e.label
                textSize = 16f
                setTextColor(color)
                if (cozy) typeface = fontBody()
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (e.value != null) {
                row.addView(TextView(this).apply {
                    text = e.value
                    textSize = 14f
                    setTextColor(cText(150))
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
            setPadding(dp(12), dp(12), dp(12), dp(16) + navBottom)
            addView(sv, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dlg.setContentView(wrap)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(if (blur) 0.4f else 0.7f)
            if (blur) {
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = attributes
                lp.blurBehindRadius = dp(24)
                attributes = lp
            }
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
        val vis = visibleApps().map { it.pkg }.toSet()
        val vf = favs.filter { it in vis }
        val idx = vf.indexOf(app.pkg)
        val es = mutableListOf<Entry>()
        if (!isHidden) {
            if (isFav) {
                es.add(Entry(R.drawable.ic_star_border, "Hapus dari favorit") {
                    favs = favs - app.pkg
                    renderCurrent()
                })
                if (idx > 0) {
                    es.add(Entry(R.drawable.ic_arrow_up, "Geser ke atas") {
                        moveFav(app.pkg, -1)
                        renderCurrent()
                        showMenu(app)
                    })
                }
                if (idx >= 0 && idx < vf.size - 1) {
                    es.add(Entry(R.drawable.ic_arrow_down, "Geser ke bawah") {
                        moveFav(app.pkg, 1)
                        renderCurrent()
                        showMenu(app)
                    })
                }
            } else {
                es.add(Entry(R.drawable.ic_star, "Tambah ke favorit") {
                    favs = favs + app.pkg
                    toast("${nm(app)} ditambah ke favorit")
                })
            }
        }
        es.add(Entry(R.drawable.ic_edit, "Ubah nama") { showRename(app) })
        if (!isHidden) {
            es.add(Entry(R.drawable.ic_visibility_off, "Sembunyikan") {
                hidden = hidden + app.pkg
                favs = favs - app.pkg
                toast("${nm(app)} disembunyikan. Buka lewat ikon gembok di bawah huruf")
                renderCurrent()
            })
        } else {
            es.add(Entry(R.drawable.ic_visibility, "Tampilkan kembali") {
                hidden = hidden - app.pkg
                toast("${nm(app)} ditampilkan kembali")
                renderCurrent()
            })
        }
        es.add(Entry(R.drawable.ic_info, "Info aplikasi") {
            openSafe(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.pkg)))
        })
        es.add(Entry(R.drawable.ic_delete, "Copot pemasangan", danger = true) {
            openSafe(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)),
                "Tidak bisa mencopot ${nm(app)}")
        })
        val icon = try {
            colorIcon(app.pkg)?.let { BitmapDrawable(resources, it) }
        } catch (e: Exception) { null }
        showSheet(nm(app), icon, es)
    }

    private fun moveFav(pkg: String, delta: Int) {
        val vis = visibleApps().map { it.pkg }.toSet()
        val l = favs.toMutableList()
        val i = l.indexOf(pkg)
        if (i < 0) return
        var j = i + delta
        while (j in l.indices && l[j] !in vis) j += delta
        if (j !in l.indices) return
        java.util.Collections.swap(l, i, j)
        favs = l
    }

    // Pengaturan (tekan lama jam / area kosong)
    private fun showSettings() {
        fun onOff(b: Boolean) = if (b) "Aktif" else "Mati"
        showSheet("Pengaturan", null, listOf(
            Entry(0, "Tema", themeNames[themeIdx]) {
                pickOption("Tema", themeNames, themeIdx) {
                    themeIdx = it
                    applyTheme()
                }
            },
            Entry(0, "Gaya jam", clockNames[clockStyle]) {
                pickOption("Gaya jam", clockNames, clockStyle) {
                    clockStyle = it
                    applyClockStyle()
                    applyAccent()
                }
            },
            Entry(0, "Latar", bgNames[bgMode]) {
                pickOption("Latar", bgNames, bgMode) {
                    bgMode = it
                    applyBackground()
                }
            },
            Entry(0, "Warna aksen", accentNames()[accentIdx]) {
                pickOption("Warna aksen", accentNames(), accentIdx) {
                    accentIdx = it
                    applyAccent()
                    lastSig = null
                    renderCurrent()
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
            Entry(0, "Sedang diputar",
                if (!nowPlayingOn) "Mati" else if (notifAccess()) "Aktif" else "Perlu izin") {
                nowPlayingOn = !nowPlayingOn
                if (nowPlayingOn && !notifAccess()) {
                    toast("Aktifkan akses notifikasi untuk Mini Launcher")
                    openSafe(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } else {
                    safely { startNowPlaying() }
                    showSettings()
                }
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
            Entry(0, "Ketuk 2x untuk kunci layar",
                if (!dtap) "Mati" else if (lockServiceEnabled()) "Aktif" else "Perlu izin") {
                dtap = !dtap
                if (dtap && !lockServiceEnabled()) {
                    toast("Aktifkan layanan aksesibilitas Mini Launcher")
                    openSafe(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    showSettings()
                }
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

    // ---------- Nama kustom ----------

    private fun names(): MutableMap<String, String> {
        nameCache?.let { return it }
        val m = HashMap<String, String>()
        try {
            val j = org.json.JSONObject(prefs.getString("names", "{}") ?: "{}")
            val keysIt = j.keys()
            while (keysIt.hasNext()) {
                val k = keysIt.next()
                m[k] = j.getString(k)
            }
        } catch (e: Exception) { /* abaikan */ }
        nameCache = m
        return m
    }

    private fun saveNames() {
        val j = org.json.JSONObject()
        for ((k, v) in names()) j.put(k, v)
        prefs.edit().putString("names", j.toString()).apply()
    }

    private fun nm(app: App): String = names()[app.pkg] ?: app.label

    private fun showRename(app: App) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val renamed = names().containsKey(app.pkg)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = sheetBg(false)
            setPadding(dp(20), dp(20), dp(20), dp(12))
        }
        card.addView(TextView(this).apply {
            text = "Ubah nama"
            textSize = 18f
            setTextColor(cText())
            typeface = fontBody()
        })
        card.addView(TextView(this).apply {
            text = "Nama asli: " + app.label
            textSize = 13f
            setTextColor(cText(150))
            setPadding(0, dp(4), 0, dp(14))
        })
        val input = EditText(this).apply {
            setText(nm(app))
            setSelection(text.length)
            setSingleLine()
            textSize = 17f
            setTextColor(cText())
            setHintTextColor(cText(110))
            hint = "Nama panggilan"
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(cSurface())
                cornerRadius = dp(16).toFloat()
            }
        }
        card.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun save() {
            val v = input.text.toString().trim()
            if (v.isEmpty() || v == app.label) names().remove(app.pkg) else names()[app.pkg] = v
            saveNames()
            lastSig = null
            renderCurrent()
            dlg.dismiss()
        }

        fun btn(label: String, color: Int, onClick: () -> Unit): TextView = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(color)
            typeface = fontBody()
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = pressBg()
            isClickable = true
            setOnClickListener { onClick() }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        if (renamed) {
            row.addView(btn("Reset", cDanger()) {
                names().remove(app.pkg)
                saveNames()
                lastSig = null
                renderCurrent()
                dlg.dismiss()
                toast("Nama dikembalikan ke asli")
            })
        }
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(btn("Batal", cText(190)) { dlg.dismiss() })
        row.addView(btn("Simpan", accentNow) { save() })
        card.addView(row)

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save()
                true
            } else {
                false
            }
        }

        val wrap = FrameLayout(this).apply {
            setPadding(dp(20), dp(20), dp(20), dp(20))
            addView(card, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dlg.setContentView(wrap)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.6f)
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }
        dlg.show()
        input.requestFocus()
    }

    // ---------- Pencarian cepat (geser ke atas) ----------

    private fun imm() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private fun norm(x: String): String =
        java.text.Normalizer.normalize(x, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase()

    private fun openSearch() {
        if (viewMode == 3) return
        viewMode = 3
        showHeader(false)
        showBigLetter(null)
        searchPill.visibility = View.VISIBLE
        (scroll.layoutParams as FrameLayout.LayoutParams).topMargin = dp(130)
        scroll.requestLayout()
        searchBox.setText("")
        renderSearch()
        searchBox.requestFocus()
        searchBox.post { imm().showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun leaveSearchUi() {
        if (searchPill.visibility != View.VISIBLE) return
        imm().hideSoftInputFromWindow(searchBox.windowToken, 0)
        searchBox.clearFocus()
        searchBox.setText("")
        searchPill.visibility = View.GONE
        (scroll.layoutParams as FrameLayout.LayoutParams).topMargin = dp(215)
        scroll.requestLayout()
    }

    private fun renderSearch() {
        val q = norm(searchBox.text.toString().trim())
        val all = visibleApps()
        val res = if (q.isEmpty()) {
            all.sortedWith { x, y -> collator.compare(nm(x), nm(y)) }
        } else {
            all.filter { norm(nm(it)).contains(q) || norm(it.label).contains(q) }
                .sortedWith { x, y ->
                    val sx = if (norm(nm(x)).startsWith(q)) 0 else 1
                    val sy = if (norm(nm(y)).startsWith(q)) 0 else 1
                    if (sx != sy) sx - sy else collator.compare(nm(x), nm(y))
                }
        }
        searchResults = res
        lastSig = null
        list.gravity = Gravity.TOP
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        if (res.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Tidak ditemukan"
                textSize = 14f
                setTextColor(Color.GRAY)
            })
        }
        val mode = iconStyle
        res.forEach { app -> list.addView(row(app, mode, 18f)) }
    }

    // ---------- Ketuk 2x untuk kunci layar ----------

    private fun onEmptyTap() {
        val now = SystemClock.uptimeMillis()
        if (dtap && viewMode == 0 && now - lastTapAt < 350L) {
            lastTapAt = 0L
            lockScreen()
            return
        }
        lastTapAt = now
        showFavorites()
    }

    private fun lockServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(this, LockService::class.java)
        return enabled.split(":").any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }

    private fun lockScreen() {
        val svc = LockService.instance
        if (svc == null) {
            toast("Aktifkan layanan aksesibilitas Mini Launcher")
            return
        }
        if (!svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) {
            toast("Tidak bisa mengunci layar")
        }
    }

    // ---------- Sedang diputar ----------

    private fun notifAccess(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        return s.contains(packageName)
    }

    private val mediaCb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { refreshNowPlaying() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { refreshNowPlaying() }
        override fun onSessionDestroyed() { refreshNowPlaying() }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list ->
            bindControllers(list ?: emptyList())
        }

    private fun startNowPlaying() {
        if (!nowPlayingOn || !notifAccess()) {
            stopNowPlaying()
            return
        }
        try {
            val m = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(this, MediaListenerService::class.java)
            if (sessionMgr == null) {
                m.addOnActiveSessionsChangedListener(sessionsListener, cn)
                sessionMgr = m
            }
            bindControllers(m.getActiveSessions(cn))
        } catch (e: Exception) {
            stopNowPlaying()
        }
    }

    private fun bindControllers(list: List<MediaController>) {
        for (c in controllers) {
            try { c.unregisterCallback(mediaCb) } catch (e: Exception) { /* abaikan */ }
        }
        controllers = list
        for (c in list) {
            try { c.registerCallback(mediaCb) } catch (e: Exception) { /* abaikan */ }
        }
        refreshNowPlaying()
    }

    private fun stopNowPlaying() {
        try { sessionMgr?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (e: Exception) { /* abaikan */ }
        sessionMgr = null
        for (c in controllers) {
            try { c.unregisterCallback(mediaCb) } catch (e: Exception) { /* abaikan */ }
        }
        controllers = emptyList()
        nowController = null
        nowPlaying.visibility = View.GONE
    }

    private val junkRegex = Regex(
        "https?://\\S+|\\b[\\w-]+\\.(?:com|net|org|io|fm|tv|co)(?:/\\S*)?",
        RegexOption.IGNORE_CASE
    )

    // Buang sampah metadata seperti " | soundcloud.com/..." atau tautan di akhir judul
    private fun stripJunk(x: String): String {
        val t = junkRegex.replace(x.substringBefore(" | "), "")
        return t.replace(Regex("\\s{2,}"), " ").trim().trimEnd('-', '|', '·', ' ')
    }

    // Satu baris dengan equalizer pixel; hilang sepenuhnya saat tidak ada yang diputar
    private fun refreshNowPlaying() {
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        nowController = c
        val md = c?.metadata
        val rawTitle = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val rawArtist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        if (c == null || rawTitle.isNullOrBlank()) {
            nowEq.playing = false
            nowPlaying.visibility = View.GONE
            return
        }
        var title = stripJunk(rawTitle)
        if (title.isBlank()) title = rawTitle.trim()
        val artist = if (rawArtist.isNullOrBlank()) "" else stripJunk(rawArtist)
        val line = when {
            artist.isBlank() -> title
            title.contains(artist, true) -> title // judul sudah memuat nama artis
            else -> "$title · $artist"
        }
        if (nowText.text.toString() != line) nowText.text = line
        nowEq.playing = true
        if (nowPlaying.visibility != View.VISIBLE) {
            nowPlaying.alpha = 0f
            nowPlaying.visibility = View.VISIBLE
            nowPlaying.animate().alpha(1f).setDuration(200).start()
        }
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
        tintable.clear()
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
            3 -> renderSearch()
            else -> showFavorites()
        }
    }

    private fun showFavorites() {
        viewMode = 0
        leaveSearchUi()
        showHeader(true)
        showBigLetter(null)
        val sig = "f|" + favs.joinToString(",") + "|" + hidden.size + "|" + iconStyle + "|" + apps.size + "|" + names().hashCode() + "|" + themeIdx
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
        leaveSearchUi()
        showHeader(false)
        showBigLetter(c)
        val sig = "l|$c|" + hidden.joinToString(",") + "|" + iconStyle + "|" + apps.size + "|" + names().hashCode() + "|" + themeIdx
        if (sig == lastSig && list.childCount > 0) return
        lastSig = sig
        list.gravity = Gravity.CENTER_VERTICAL
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        val mode = iconStyle
        val matches = visibleApps().filter { keyOf(nm(it)) == c }
            .sortedWith { x, y -> collator.compare(nm(x), nm(y)) }
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
        leaveSearchUi()
        showHeader(false)
        showBigLetter(null)
        val sig = "h|" + hidden.joinToString(",") + "|" + iconStyle + "|" + apps.size + "|" + names().hashCode() + "|" + themeIdx
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
