package jp.haruto.phonebounce

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import jp.haruto.phonebounce.core.ColorMode
import jp.haruto.phonebounce.core.Interference

/** 設定の画面。常駐の本体（BounceService）は、ここでの変更をすぐ反映する。 */
class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var labelEdit: EditText
    private lateinit var statusDot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var statusButton: TextView
    private lateinit var statusHint: TextView
    private lateinit var batteryRow: LinearLayout
    private val previews = ArrayList<StylePreview>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val full = ComponentName(this, BounceService::class.java).flattenToString()
        val short = ComponentName(this, BounceService::class.java).flattenToShortString()
        return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
    }

    private fun refreshStatus() {
        val on = serviceEnabled()
        statusDot.background = roundRect(this, if (on) Pal.ON else Pal.WARN, 8f)
        statusTitle.text = if (on) "常駐中" else "まだオフです"
        statusSub.text = if (on) "再起動しても自動で動きます" else "アクセシビリティでオンにすると動き出します"
        statusButton.text = if (on) "アクセシビリティの設定を開く" else "オンにする"
        statusButton.background = if (on) roundRect(this, 0x00000000, 14f, Pal.LINE) else roundRect(this, Pal.ACCENT, 14f)
        statusButton.setTextColor(if (on) Pal.TEXT else Pal.INK)
        statusHint.visibility = if (on) View.GONE else View.VISIBLE
        val ignoring = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        batteryRow.visibility = if (ignoring) View.GONE else View.VISIBLE
    }

    // ---------------- 画像を選ぶ ----------------

    private fun pickImage() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" },
            REQ_IMAGE,
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMAGE || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        if (CustomLogo.saveFrom(this, uri)) {
            val p = AppSettings.prefs(this)
            p.edit().putInt(AppSettings.IMG_VER, p.getInt(AppSettings.IMG_VER, 0) + 1)
                .putString(AppSettings.STYLE, LogoStyle.IMAGE.name).apply()
            val mask = CustomLogo.loadMask(this)
            previews.forEach { if (it.style == LogoStyle.IMAGE) it.setImage(mask); it.chosen = it.style == LogoStyle.IMAGE }
            Toast.makeText(this, "画像を設定しました（明るい所・不透明な所がロゴの形になります）", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "画像を読み込めませんでした", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------- 画面づくり ----------------

    private fun lp(top: Int = 0, h: Int = LinearLayout.LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h).apply { topMargin = dp(top) }

    private fun card(title: String?, top: Int = 14, fill: Card.() -> Unit) {
        val c = Card(this)
        if (title != null) c.addView(cardTitle(this, title))
        c.fill()
        root.addView(c, lp(top))
    }

    private fun build() {
        window.statusBarColor = Pal.BG
        window.navigationBarColor = Pal.BG
        val scroll = ScrollView(this).apply { setBackgroundColor(Pal.BG); isFillViewport = true; clipToPadding = false }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(36))
        }
        scroll.addView(root)
        setContentView(scroll)
        val p = AppSettings.prefs(this)
        val s = AppSettings.load(this)

        root.addView(label(this, "Phone Bounce", 28f, Pal.TEXT, true))
        root.addView(label(this, "スマホの画面で跳ね続ける、DVD のロゴ。", 14f, Pal.SUB).apply { setPadding(0, dp(2), 0, dp(14)) })
        root.addView(HeroView(this), lp(0, dp(190)))

        // 状態
        card(null) {
            val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            statusDot = View(context)
            head.addView(statusDot, LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(10) })
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            statusTitle = label(context, "", 17f, Pal.TEXT, true)
            statusSub = label(context, "", 12.5f, Pal.SUB)
            col.addView(statusTitle); col.addView(statusSub)
            head.addView(col)
            addView(head)
            statusButton = primaryButton(context, "") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            addView(statusButton, lp(12, dp(50)))
            statusHint = label(context, "一覧の「Phone Bounce」を選んでオンにします。灰色でオンにできないときは、設定 → アプリ → Phone Bounce → 右上の︙ → 「制限付き設定を許可」をしてから、もう一度。", 12.5f, Pal.SUB).apply { setPadding(0, dp(10), 0, 0) }
            addView(statusHint)
            addView(ToggleRow(context, "一時停止", "クイック設定の「跳ねるロゴ」タイルからも切り替えられます", s.paused) { p.edit().putBoolean(AppSettings.PAUSED, it).apply() })
            batteryRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
            batteryRow.addView(label(context, "電池の最適化から除外すると、システムに止められにくくなります", 12.5f, Pal.SUB), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(10) })
            batteryRow.addView(chip(context, "除外する") {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            })
            addView(batteryRow)
        }

        // 見た目
        card("見た目") {
            addView(label(context, "どれも元の Desktop Bounce のロゴ（斜めの文字＋円盤）が土台です。", 12.5f, Pal.SUB).apply { setPadding(0, 0, 0, dp(10)) })
            styleStrip(this, s.style)
            val bar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
            bar.addView(chip(context, "画像を選ぶ") { pickImage() })
            addView(bar)
            addView(label(context, "画像は、透明な所を抜いた形（透明がなければ明るい所）がロゴの形になり、色は自動で塗られます。", 12f, Pal.SUB).apply { setPadding(0, dp(8), 0, dp(14)) })

            addView(label(context, "ロゴの文字（12文字まで）", 13.5f, Pal.SUB))
            labelEdit = EditText(context).apply {
                setText(s.label)
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine()
                setTextColor(Pal.TEXT); setHintTextColor(Pal.SUB); textSize = 16f
                background = roundRect(context, 0xFF0F0F15.toInt(), 12f, Pal.LINE)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                    override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                    override fun afterTextChanged(e: Editable?) {
                        p.edit().putString(AppSettings.LABEL, e?.toString()?.take(12) ?: "DVD").apply()
                    }
                })
            }
            addView(labelEdit, lp(6))
            val presets = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
            for (t in listOf("DVD", "VHS", "CD", "BD", "LD", "4K")) {
                presets.addView(chip(context, t) { labelEdit.setText(t) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(6) })
            }
            addView(presets)

            addView(label(context, "文字の下の飾り", 13.5f, Pal.SUB).apply { setPadding(0, dp(16), 0, dp(6)) })
            addView(Segmented(context, Decor.values().map { it.title to it }, s.decor) {
                p.edit().putString(AppSettings.DECOR, it.name).apply()
            })
            addView(label(context, "当たり判定は、見えている形にぴったり合わせます（「なし」は文字だけの枠になります）。", 12f, Pal.SUB).apply { setPadding(0, dp(8), 0, 0) })
        }

        // 動き
        card("動き") {
            addView(SliderRow(context, "速さ", 20f, 400f, 5f, s.speedDp, "dp/秒") { p.edit().putFloat(AppSettings.SPEED, it).apply() })
            addView(SliderRow(context, "ロゴの大きさ", 24f, 120f, 2f, s.logoHeightDp, "dp") { p.edit().putFloat(AppSettings.SIZE, it).apply() })
            addView(label(context, "色", 15.5f).apply { setPadding(0, dp(12), 0, dp(6)) })
            addView(Segmented(context, listOf("当たるたび" to ColorMode.PER_BOUNCE, "虹色" to ColorMode.RAINBOW, "ランダム" to ColorMode.RANDOM), s.colorMode) {
                p.edit().putString(AppSettings.COLOR, it.name).apply()
            })
        }

        // 壁
        card("ぶつかるもの") {
            addView(ToggleRow(context, "キーボード", "出ているあいだ、上の縁で跳ね返る", s.wallIme) { p.edit().putBoolean(AppSettings.WALL_IME, it).apply() })
            addView(ToggleRow(context, "分割画面・小窓", "ほかのアプリの窓の枠で跳ね返る", s.wallWindows) { p.edit().putBoolean(AppSettings.WALL_WINDOWS, it).apply() })
            val kinds = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(6))
                visibility = if (s.wallElements) View.VISIBLE else View.GONE
            }
            fun kindChip(t: String, key: String, on: Boolean) {
                var cur = on
                val c = chip(context, t, cur) {}
                c.setOnClickListener { cur = !cur; c.paint(cur); p.edit().putBoolean(key, cur).apply() }
                kinds.addView(c, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(6) })
            }
            kindChip("ボタン・入力欄", AppSettings.ELEM_CONTROLS, s.elemControls)
            kindChip("画像・動画", AppSettings.ELEM_IMAGES, s.elemImages)
            kindChip("文字の枠", AppSettings.ELEM_TEXT, s.elemText)
            addView(ToggleRow(context, "画面の部品", "ボタン・画像などの枠でも跳ね返る（位置と大きさだけを使います）", s.wallElements) {
                p.edit().putBoolean(AppSettings.WALL_ELEMENTS, it).apply()
                kinds.visibility = if (it) View.VISIBLE else View.GONE
            })
            addView(kinds)
            addView(ToggleRow(context, "バーの内側で跳ねる", "ステータスバー・ナビゲーションバーに重ならない", s.insetBars) { p.edit().putBoolean(AppSettings.INSET_BARS, it).apply() })
        }

        card("窓がぶつかってきたとき") {
            addView(Segmented(context, listOf("反射だけ" to Interference.REFLECT_ONLY, "押し出し" to Interference.PUSH, "押し出し＋侵入" to Interference.PUSH_INTRUDE), s.interference) {
                p.edit().putString(AppSettings.INTERFERENCE, it.name).apply()
            })
            addView(label(context, "侵入: 窓が勢いよくぶつかる・押した先が壁で挟まれる・ロゴの上に窓が開く、のどれかで、ロゴが窓の中に入って半透明になり、窓と一緒に運ばれます。一定時間で、窓の枠をすり抜けて外へ出ます。", 12.5f, Pal.SUB).apply { setPadding(0, dp(10), 0, 0) })
            addView(SliderRow(context, "窓の中にいる時間", 2f, 20f, 1f, s.intrudeSeconds, "秒") { p.edit().putFloat(AppSettings.INTRUDE_SEC, it).apply() })
        }

        card("電池・確認") {
            addView(label(context, "なめらかさ", 15.5f).apply { setPadding(0, 0, 0, dp(6)) })
            addView(Segmented(context, listOf("60fps（なめらか）" to true, "30fps（省電力）" to false), s.fps60) { p.edit().putBoolean(AppSettings.FPS60, it).apply() })
            addView(ToggleRow(context, "壁を表示（確認用）", "ロゴが壁として使っている窓・部品を赤い枠で出す", s.showWalls) { p.edit().putBoolean(AppSettings.SHOW_WALLS, it).apply() }.apply { setPadding(0, dp(14), 0, dp(8)) })
        }

        card("読み取るもの", 14) {
            addView(label(context, "窓と、画面の部品の「位置・大きさ・種類」だけを使います。文字の中身・入力した内容・説明文・ID は読みません。インターネットの権限もありません。\n\n「画面の部品」をオフにすると、窓の位置だけになります。", 13f, Pal.SUB))
        }

        val reset = chip(this, "設定をもとに戻す") {
            AppSettings.reset(this)
            recreate()
        }
        root.addView(reset, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18); gravity = Gravity.CENTER_HORIZONTAL
        })
    }

    /** 見た目の見本を、横にスクロールして選ぶ */
    private fun styleStrip(parent: LinearLayout, current: LogoStyle) {
        val mask = CustomLogo.loadMask(this)
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        LogoStyle.values().forEach { st ->
            val card = StylePreview(this, st, mask).apply {
                chosen = st == current
                setOnClickListener {
                    if (st == LogoStyle.IMAGE && !CustomLogo.exists(this@MainActivity)) { pickImage(); return@setOnClickListener }
                    AppSettings.prefs(this@MainActivity).edit().putString(AppSettings.STYLE, st.name).apply()
                    previews.forEach { it.chosen = it.style == st }
                }
            }
            previews += card
            strip.addView(card, LinearLayout.LayoutParams(dp(160), dp(124)).apply { rightMargin = dp(8) })
        }
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false }
        hs.addView(strip)
        parent.addView(hs, lp(0, dp(124)))
    }

    companion object {
        private const val REQ_IMAGE = 42
    }
}
