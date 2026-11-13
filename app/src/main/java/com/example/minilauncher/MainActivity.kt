package com.example.minilauncher

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
    private lateinit var batteryText: TextView
    private lateinit var batteryFill: View
    private lateinit var batteryFillBg: GradientDrawable
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var strip: LinearLayout
    private var currentKey = -1

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

    private var favs: List<String>
        get() = readList("list")
        set(v) = writeList("list", v)

    private var hidden: List<String>
        get() = readList("hidden")
        set(v) = writeList("hidden", v)

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
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        root = FrameLayout(this)
        root.clipChildren = false
        root.setBackgroundColor(Color.BLACK)
        root.setOnClickListener { showFavorites() }
        root.setOnLongClickListener { showSettings(); true }
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }

        // Beranda: sapaan kecil, jam besar tipis, tanggal, dan garis baterai
        header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(32), 0, 0)
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
        header.addView(TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "hh:mm"
            textSize = 72f
            includeFontPadding = false
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setTextColor(Color.WHITE)
        })
        header.addView(TextClock(this).apply {
            format24Hour = "EEEE  ·  d MMMM"
            format12Hour = "EEEE  ·  d MMMM"
            textSize = 13f
            letterSpacing = 0.16f
            setAllCaps(true)
            setTextColor(Color.argb(200, 255, 255, 255))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(4), dp(4), 0, 0)
        })
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

        setContentView(root)
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
        try {
            val sbs = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(sbs)
        } catch (e: Exception) {
            toast("Tidak bisa membuka panel notifikasi")
        }
    }

    private fun iconKey(res: Int, desc: String): View = ImageView(this).apply {
        setImageResource(res)
        contentDescription = desc
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(16), dp(5), dp(16), dp(5))
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
        batteryFillBg.setColor(
            if (pct <= 15 && !charging) Color.parseColor("#FF6B6B") else Color.WHITE
        )
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
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(180).start()
        updateGreeting()
        loadApps()
        showFavorites()
        precomputeIcons()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        showFavorites() // tombol Home ditekan saat launcher sudah terbuka
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showFavorites()
    }

    private fun loadApps() {
        try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val collator = java.text.Collator.getInstance()
            collator.strength = java.text.Collator.PRIMARY
            apps = packageManager.queryIntentActivities(i, 0)
                .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != packageName }
                .distinctBy { it.pkg }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
        } catch (e: Exception) {
            apps = emptyList()
        }
        if (prefs.getString("list", null) == null && apps.isNotEmpty()) {
            favs = apps.take(5).map { it.pkg }
        }
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

    private fun showHeader(show: Boolean) {
        header.animate()
            .alpha(if (show) 1f else 0f)
            .translationY(if (show) 0f else -dp(10).toFloat())
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // ---------- Ikon monokrom ----------

    private val monoCache = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val colorCache = java.util.concurrent.ConcurrentHashMap<String, Drawable.ConstantState>()
    private val NONE = Any()

    private fun monoIcon(pkg: String): Bitmap? {
        val c = monoCache[pkg]
        if (c != null) return c as? Bitmap
        val bmp = try { buildMono(pkg) } catch (e: Exception) { null }
        monoCache[pkg] = bmp ?: NONE
        return bmp
    }

    private fun colorIcon(pkg: String): Drawable? {
        val cs = colorCache[pkg]
        if (cs != null) return cs.newDrawable()
        val d = packageManager.getApplicationIcon(pkg)
        d.constantState?.let { colorCache[pkg] = it }
        return d
    }

    // Siapkan ikon di latar belakang supaya menggeser huruf tidak tersendat
    private fun precomputeIcons() {
        val pkgs = apps.map { it.pkg }
        val style = iconStyle
        if (style == 0) return
        Thread {
            Thread.currentThread().priority = Thread.MIN_PRIORITY
            for (p in pkgs) {
                try {
                    if (style == 2) monoIcon(p) else colorIcon(p)
                } catch (e: Exception) { /* abaikan */ }
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
        return result
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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

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
            try {
                if (iconMode == 2) {
                    iv.setImageBitmap(monoIcon(app.pkg) ?: placeholderIcon(app.label))
                } else {
                    iv.setImageDrawable(colorIcon(app.pkg))
                }
            } catch (e: Exception) { /* abaikan */ }
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

    // Menu tekan-lama pada aplikasi
    private fun showMenu(app: App) {
        val isHidden = app.pkg in hidden
        val isFav = app.pkg in favs
        val opts = mutableListOf<Pair<String, () -> Unit>>()
        if (!isHidden) {
            if (isFav) {
                opts.add("Hapus dari favorit" to {
                    favs = favs - app.pkg
                    renderCurrent()
                })
            } else {
                opts.add("Tambah ke favorit" to {
                    favs = favs + app.pkg
                    toast("${app.label} ditambah ke favorit")
                })
            }
            opts.add("Sembunyikan" to {
                hidden = hidden + app.pkg
                favs = favs - app.pkg
                toast("${app.label} disembunyikan. Buka lewat ikon gembok di bawah huruf")
                renderCurrent()
            })
        } else {
            opts.add("Tampilkan kembali" to {
                hidden = hidden - app.pkg
                toast("${app.label} ditampilkan kembali")
                renderCurrent()
            })
        }
        opts.add("Info aplikasi" to {
            openSafe(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.pkg)))
        })
        opts.add("Copot pemasangan" to {
            openSafe(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)),
                "Tidak bisa mencopot ${app.label}")
        })
        showStyled(AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(opts.map { it.first }.toTypedArray()) { _, i -> opts[i].second() }
        )
    }

    // Pengaturan (tekan lama jam / area kosong)
    private fun showSettings() {
        val items = arrayOf(
            "Gaya ikon",
            "Getaran saat geser huruf: " + (if (haptic) "aktif" else "mati"),
            "Atur launcher default",
            "Pengaturan sistem"
        )
        showStyled(AlertDialog.Builder(this)
            .setTitle("Pengaturan")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> pickIconStyle()
                    1 -> {
                        haptic = !haptic
                        toast(if (haptic) "Getaran aktif" else "Getaran mati")
                    }
                    2 -> openSafe(Intent(Settings.ACTION_HOME_SETTINGS))
                    3 -> openSafe(Intent(Settings.ACTION_SETTINGS))
                }
            }
        )
    }

    private fun pickIconStyle() {
        showStyled(AlertDialog.Builder(this)
            .setTitle("Gaya ikon")
            .setSingleChoiceItems(
                arrayOf("Tanpa ikon", "Ikon warna", "Ikon monokrom (putih)"), iconStyle
            ) { d, which ->
                iconStyle = which
                d.dismiss()
                renderCurrent()
            }
        )
    }

    // ---------- Tampilan daftar ----------

    private fun showKey(idx: Int) {
        when (val k = keys[idx]) {
            KEY_FAV -> showFavorites()
            KEY_HIDDEN -> showHidden()
            else -> showLetter(k[0])
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
        list.gravity = Gravity.BOTTOM
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        val vis = visibleApps()
        val items = favs.mapNotNull { p -> vis.find { it.pkg == p } }
        if (items.isEmpty()) {
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
        viewMode = 1
        viewLetter = c
        showHeader(false)
        list.gravity = Gravity.CENTER_VERTICAL
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        val mode = iconStyle
        list.addView(TextView(this).apply {
            text = c.toString()
            textSize = 13f
            letterSpacing = 0.15f
            setTextColor(Color.argb(150, 255, 255, 255))
            setPadding(0, 0, 0, dp(6))
        })
        val matches = visibleApps().filter { keyOf(it.label) == c }
        if (matches.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Tidak ada aplikasi"
                textSize = 14f
                setTextColor(Color.GRAY)
            })
        }
        matches.forEach { app -> list.addView(row(app, mode, 18f)) }
        animateRows()
    }

    private fun showHidden() {
        viewMode = 2
        showHeader(false)
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
