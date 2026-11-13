package com.example.minilauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var strip: LinearLayout
    private var currentKey = -1

    // 0 = favorit, 1 = daftar huruf, 2 = aplikasi tersembunyi
    private var viewMode = 0
    private var viewLetter = 'A'

    private val prefs by lazy { getSharedPreferences("fav", MODE_PRIVATE) }

    private val KEY_FAV = "★"
    private val KEY_HIDDEN = "🔒"
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

    // 0 = tanpa ikon, 1 = ikon warna, 2 = ikon monokrom putih
    private var iconStyle: Int
        get() = prefs.getInt("icon", 2)
        set(v) { prefs.edit().putInt("icon", v).apply() }

    private var dim: Int
        get() = prefs.getInt("dim", 150)
        set(v) { prefs.edit().putInt("dim", v).apply() }

    private val whiteFilter = ColorMatrixColorFilter(
        floatArrayOf(
            0f, 0f, 0f, 0f, 255f,
            0f, 0f, 0f, 0f, 255f,
            0f, 0f, 0f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        root = FrameLayout(this)
        root.clipChildren = false
        root.setBackgroundColor(Color.argb(dim, 0, 0, 0))
        root.setOnClickListener { showFavorites() }
        root.setOnLongClickListener { showSettings(); true }
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }

        // Jam: hari besar & tipis + jam kecil
        header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(40), 0, 0)
            setOnLongClickListener { showSettings(); true }
        }
        header.addView(TextClock(this).apply {
            format24Hour = "EEE"
            format12Hour = "EEE"
            textSize = 76f
            setAllCaps(true)
            includeFontPadding = false
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setTextColor(Color.WHITE)
        })
        header.addView(TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "hh:mm"
            textSize = 18f
            letterSpacing = 0.2f
            setTextColor(Color.WHITE)
        })
        root.addView(header, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Daftar aplikasi
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            setOnClickListener { showFavorites() }
            setOnLongClickListener { showSettings(); true }
            addView(list, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = dp(180); bottomMargin = dp(32)
            marginStart = dp(28); marginEnd = dp(64)
        })

        // Strip huruf di kanan
        strip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }
        keys.forEach { k ->
            strip.addView(TextView(this).apply {
                text = k
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(dp(44), 0, 1f))
        }
        strip.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val pos = (e.y / v.height * keys.size).coerceIn(0f, keys.size - 0.001f)
                    fisheye(pos)
                    val idx = pos.toInt()
                    if (idx != currentKey) {
                        currentKey = idx
                        showKey(idx)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    currentKey = -1
                    resetFisheye()
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

    override fun onResume() {
        super.onResume()
        loadApps()
        showFavorites()
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
            apps = packageManager.queryIntentActivities(i, 0)
                .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            apps = emptyList()
        }
        if (prefs.getString("list", null) == null && apps.isNotEmpty()) {
            favs = apps.take(5).map { it.pkg }
        }
    }

    private fun visibleApps(): List<App> {
        val h = hidden
        return apps.filter { it.pkg !in h }
    }

    // Efek lengkung seperti Niagara: huruf dekat jari bergeser ke kiri & membesar
    private fun fisheye(pos: Float) {
        for (i in 0 until strip.childCount) {
            val c = strip.getChildAt(i)
            val d = abs(i + 0.5f - pos)
            val w = exp(-d * d / 4f)
            c.animate().cancel()
            c.translationX = -dp(40) * w
            c.scaleX = 1f + 0.9f * w
            c.scaleY = 1f + 0.9f * w
            c.alpha = 0.45f + 0.55f * w
        }
    }

    private fun resetFisheye() {
        for (i in 0 until strip.childCount) {
            strip.getChildAt(i).animate()
                .translationX(0f).scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(150).start()
        }
    }

    private fun launch(app: App) {
        try {
            packageManager.getLaunchIntentForPackage(app.pkg)?.let { startActivity(it) }
        } catch (e: Exception) {
            toast("Tidak bisa membuka ${app.label}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun openSafe(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("Tidak bisa membuka pengaturan")
        }
    }

    private fun row(app: App, iconMode: Int, textSp: Float): View {
        val padV = dp(if (iconMode != 0) 8 else 10)
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, padV, 0, padV)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        if (iconMode != 0) {
            val iv = ImageView(this)
            try {
                val d = packageManager.getApplicationIcon(app.pkg)
                if (iconMode == 2) {
                    // monokrom: pakai lapisan depan ikon adaptif, diwarnai putih
                    if (d is AdaptiveIconDrawable) {
                        iv.setImageDrawable(d.foreground ?: d)
                        iv.scaleX = 1.5f
                        iv.scaleY = 1.5f
                    } else {
                        iv.setImageDrawable(d)
                    }
                    iv.setColorFilter(whiteFilter)
                } else {
                    iv.setImageDrawable(d)
                }
            } catch (e: Exception) { /* abaikan */ }
            r.addView(iv, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(18) })
        }
        r.addView(TextView(this).apply {
            text = app.label
            textSize = textSp
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        r.setOnClickListener { launch(app) }
        r.setOnLongClickListener { showMenu(app); true }
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
                toast("${app.label} disembunyikan. Buka lewat tombol 🔒")
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
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(opts.map { it.first }.toTypedArray()) { _, i -> opts[i].second() }
            .show()
    }

    // Pengaturan (tekan lama jam / area kosong)
    private fun showSettings() {
        val items = arrayOf(
            "Gaya ikon favorit",
            "Kegelapan wallpaper",
            "Atur launcher default",
            "Pengaturan sistem"
        )
        AlertDialog.Builder(this)
            .setTitle("Pengaturan")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> pickIconStyle()
                    1 -> pickDim()
                    2 -> openSafe(Intent(Settings.ACTION_HOME_SETTINGS))
                    3 -> openSafe(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .show()
    }

    private fun pickIconStyle() {
        AlertDialog.Builder(this)
            .setTitle("Gaya ikon favorit")
            .setSingleChoiceItems(
                arrayOf("Tanpa ikon", "Ikon warna", "Ikon monokrom (putih)"), iconStyle
            ) { d, which ->
                iconStyle = which
                d.dismiss()
                renderCurrent()
            }
            .show()
    }

    private fun pickDim() {
        val values = intArrayOf(90, 150, 210)
        AlertDialog.Builder(this)
            .setTitle("Kegelapan wallpaper")
            .setSingleChoiceItems(arrayOf("Terang", "Sedang", "Gelap"), values.indexOf(dim)) { d, which ->
                dim = values[which]
                root.setBackgroundColor(Color.argb(dim, 0, 0, 0))
                d.dismiss()
            }
            .show()
    }

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
        header.visibility = View.VISIBLE
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
            list.addView(row(app, mode, if (mode == 0) 26f else 22f))
        }
    }

    private fun showLetter(c: Char) {
        viewMode = 1
        viewLetter = c
        header.visibility = View.INVISIBLE
        list.gravity = Gravity.CENTER_VERTICAL
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        visibleApps().filter {
            val f = it.label.firstOrNull()?.uppercaseChar() ?: '#'
            if (c == '#') f !in 'A'..'Z' else f == c
        }.forEach { app ->
            list.addView(row(app, 1, 18f))
        }
    }

    private fun showHidden() {
        viewMode = 2
        header.visibility = View.INVISIBLE
        list.gravity = Gravity.CENTER_VERTICAL
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        list.addView(TextView(this).apply {
            text = "🔒 Aplikasi tersembunyi"
            textSize = 14f
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
        items.forEach { app -> list.addView(row(app, 1, 18f)) }
    }
}
