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
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import jp.haruto.phonebounce.core.ColorMode
import jp.haruto.phonebounce.core.Interference

/** 設定の画面。常駐の本体（BounceService）は、ここでの変更をすぐ反映する。 */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var battery: TextView
    private lateinit var root: LinearLayout
    private lateinit var labelEdit: EditText
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
        status.text = if (on) "● 常駐：オン（再起動しても自動で動きます）" else "○ 常駐：オフ（下のボタンでオンにします）"
        status.setTextColor(if (on) 0xFF3DFF7A.toInt() else 0xFFFFD400.toInt())
        val ignoring = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        battery.text = if (ignoring) "電池の最適化：除外済み" else "電池の最適化：まだ（除外すると、システムに止められにくくなります）"
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

    private fun build() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(32))
        }
        scroll.addView(root)
        setContentView(scroll)
        val p = AppSettings.prefs(this)
        val s = AppSettings.load(this)

        title("Phone Bounce", 24)
        text("画面に見えているウィンドウ（キーボード・分割画面・小窓）の枠で跳ね返り続けるロゴです。")

        status = text("", 16).apply { setPadding(0, dp(12), 0, dp(4)) }
        button("アクセシビリティの設定を開く") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        note(
            "「Phone Bounce」を選んでオンにします。\n" +
                "オンにできない（灰色）ときは: 設定 → アプリ → Phone Bounce → 右上の︙ → 「制限付き設定を許可」をしてから、もう一度。\n" +
                "このアプリは、ウィンドウの位置だけを使います。画面の文字や入力した内容は読み取りません。",
        )
        battery = text("", 14).apply { setPadding(0, dp(12), 0, 0) }
        button("電池の最適化を除外する") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        note("クイック設定のパネルに「跳ねるロゴ」のタイルを足すと、いつでも一時停止・再開できます。")

        section("見た目")
        note("どれも、元の Desktop Bounce の DVD ロゴ（斜めの文字＋円盤）が土台です。タップで切り替わります。")
        styleGrid(s.style)
        button("画像を選ぶ（「画像」スタイルの形）") { pickImage() }
        note("画像は、透明な所を抜いた形（透明がなければ、明るい所）がロゴの形になり、色は自動で塗られます。")

        label("ロゴの文字（12文字まで）")
        labelEdit = EditText(this).apply {
            setText(s.label)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun afterTextChanged(e: Editable?) {
                    p.edit().putString(AppSettings.LABEL, e?.toString()?.take(12) ?: "DVD").apply()
                }
            })
        }
        root.addView(labelEdit)
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in listOf("DVD", "VHS", "CD", "BD", "LD", "4K")) {
            presets.addView(Button(this).apply {
                text = t; isAllCaps = false
                setOnClickListener { labelEdit.setText(t) }
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
        }
        root.addView(presets)

        section("基本")
        switch("一時停止", s.paused) { p.edit().putBoolean(AppSettings.PAUSED, it).apply() }
        slider("速さ", 20f, 400f, 5f, s.speedDp, "dp/秒") { p.edit().putFloat(AppSettings.SPEED, it).apply() }
        slider("ロゴの大きさ（高さ）", 24f, 120f, 2f, s.logoHeightDp, "dp") { p.edit().putFloat(AppSettings.SIZE, it).apply() }

        section("ウィンドウとの関わり")
        radio("窓がロゴにぶつかったとき", listOf(
            "反射だけ" to Interference.REFLECT_ONLY,
            "押し出し（窓が動くと、ロゴが押される）" to Interference.PUSH,
            "押し出し＋侵入（おすすめ）" to Interference.PUSH_INTRUDE,
        ), s.interference) { p.edit().putString(AppSettings.INTERFERENCE, it.name).apply() }
        note("侵入: 窓が勢いよくぶつかる・押した先が壁で挟まれる・ロゴの上に窓が開く、のどれかで、ロゴが窓の中に入って半透明になり、窓と一緒に運ばれます。一定時間で、その窓の枠をすり抜けて外へ出ます。")
        slider("窓の中にいる時間", 2f, 20f, 1f, s.intrudeSeconds, "秒") { p.edit().putFloat(AppSettings.INTRUDE_SEC, it).apply() }
        switch("キーボードを壁にする", s.wallIme) { p.edit().putBoolean(AppSettings.WALL_IME, it).apply() }
        switch("分割画面・小窓を壁にする", s.wallWindows) { p.edit().putBoolean(AppSettings.WALL_WINDOWS, it).apply() }
        switch("ステータスバー・ナビゲーションバーの内側で跳ねる", s.insetBars) { p.edit().putBoolean(AppSettings.INSET_BARS, it).apply() }

        section("色・電池")
        radio("色", listOf(
            "当たるたびに変える" to ColorMode.PER_BOUNCE,
            "虹色にゆっくり変える" to ColorMode.RAINBOW,
            "当たるたびにランダム" to ColorMode.RANDOM,
        ), s.colorMode) { p.edit().putString(AppSettings.COLOR, it.name).apply() }
        radio("なめらかさ", listOf("60fps（なめらか）" to true, "30fps（省電力）" to false), s.fps60) {
            p.edit().putBoolean(AppSettings.FPS60, it).apply()
        }
        switch("壁を表示（確認用）", s.showWalls) { p.edit().putBoolean(AppSettings.SHOW_WALLS, it).apply() }
        note("赤い枠が、いまロゴが壁として使っている窓です。何もない所で跳ねる・窓にめり込むときは、これで確かめます。")

        section("")
        button("設定をもとに戻す") {
            AppSettings.reset(this)
            recreate()
        }
    }


    private fun styleGrid(current: LogoStyle) {
        val mask = CustomLogo.loadMask(this)
        var row: LinearLayout? = null
        LogoStyle.values().forEachIndexed { i, st ->
            if (i % 2 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                root.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(136)))
            }
            val card = StylePreview(this, st, mask).apply {
                chosen = st == current
                setOnClickListener {
                    if (st == LogoStyle.IMAGE && !CustomLogo.exists(this@MainActivity)) { pickImage(); return@setOnClickListener }
                    AppSettings.prefs(this@MainActivity).edit().putString(AppSettings.STYLE, st.name).apply()
                    previews.forEach { it.chosen = it.style == st }
                }
            }
            previews += card
            row!!.addView(card, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
    }

    private fun title(t: String, sp: Int) = TextView(this).apply {
        text = t; textSize = sp.toFloat(); setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(6)); root.addView(this)
    }

    private fun text(t: String, sp: Int = 14) = TextView(this).apply {
        text = t; textSize = sp.toFloat(); setPadding(0, dp(4), 0, dp(4)); root.addView(this)
    }

    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 12.5f; setTextColor(0xFF9B9BA8.toInt()); setLineSpacing(0f, 1.25f)
        setPadding(0, dp(2), 0, dp(6)); root.addView(this)
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 14f; setTextColor(0xFFB8B8C4.toInt()); setPadding(0, dp(10), 0, dp(2)); root.addView(this)
    }

    private fun section(t: String) {
        if (t.isEmpty()) { root.addView(View(this), LinearLayout.LayoutParams(1, dp(18))); return }
        TextView(this).apply {
            text = t; textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF25E8FF.toInt()); setPadding(0, dp(24), 0, dp(4)); root.addView(this)
        }
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setOnClickListener { onClick() }
        root.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
    }

    private fun switch(t: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = t; textSize = 15f; isChecked = checked; setPadding(0, dp(10), 0, dp(10))
        setOnCheckedChangeListener { _, v -> onChange(v) }
        root.addView(this)
    }

    private fun slider(name: String, min: Float, max: Float, step: Float, value: Float, unit: String, onChange: (Float) -> Unit) {
        val head = TextView(this).apply { textSize = 14f; setTextColor(0xFFB8B8C4.toInt()); setPadding(0, dp(10), 0, 0) }
        fun show(v: Float) { head.text = "$name：${if (step < 1f) "%.1f".format(v) else v.toInt().toString()} $unit" }
        show(value)
        root.addView(head)
        val bar = SeekBar(this).apply {
            this.max = ((max - min) / step).toInt()
            progress = ((value - min) / step).toInt().coerceIn(0, this.max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, pr: Int, fromUser: Boolean) {
                    val v = min + pr * step
                    show(v)
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
    }

    private fun <T> radio(name: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
        label(name)
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        options.forEach { (text, v) ->
            group.addView(RadioButton(this).apply {
                this.id = View.generateViewId(); this.text = text; textSize = 15f
                isChecked = v == selected; setPadding(dp(6), dp(8), 0, dp(8))
                setOnClickListener { onSelect(v) }
            })
        }
        root.addView(group)
    }

    companion object {
        private const val REQ_IMAGE = 42
    }
}
