package jp.haruto.phonebounce

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import jp.haruto.phonebounce.core.Box
import jp.haruto.phonebounce.core.Interference
import jp.haruto.phonebounce.core.Obstacle
import jp.haruto.phonebounce.core.Params
import jp.haruto.phonebounce.core.Sim

/** 設定画面の色 */
object Pal {
    val BG = 0xFF0C0C11.toInt()
    val CARD = 0xFF15151D.toInt()
    val LINE = 0xFF262633.toInt()
    val TEXT = 0xFFF1F1EE.toInt()
    val SUB = 0xFF9B9BA8.toInt()
    val ACCENT = 0xFF25E8FF.toInt()
    val ON = 0xFF3DFF7A.toInt()
    val WARN = 0xFFFFD400.toInt()
    val INK = 0xFF06222A.toInt()   // アクセント色の上に載せる文字
}

fun Context.dpf(v: Float) = v * resources.displayMetrics.density
fun Context.dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

fun roundRect(ctx: Context, fill: Int, radiusDp: Float, stroke: Int = 0, strokeDp: Float = 1f) =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = ctx.dpf(radiusDp)
        if (stroke != 0) setStroke(ctx.dpf(strokeDp).toInt().coerceAtLeast(1), stroke)
    }

/** 角丸のカード。中身は縦に並べる */
class Card(ctx: Context) : LinearLayout(ctx) {
    init {
        orientation = VERTICAL
        background = roundRect(ctx, Pal.CARD, 18f, Pal.LINE)
        setPadding(ctx.dpi(16), ctx.dpi(14), ctx.dpi(16), ctx.dpi(14))
    }
}

fun label(ctx: Context, t: String, sp: Float = 15f, color: Int = Pal.TEXT, bold: Boolean = false) = TextView(ctx).apply {
    text = t; textSize = sp; setTextColor(color)
    if (bold) setTypeface(typeface, Typeface.BOLD)
    setLineSpacing(0f, 1.2f)
}

fun cardTitle(ctx: Context, t: String) = label(ctx, t, 13f, Pal.ACCENT, true).apply {
    letterSpacing = 0.06f
    setPadding(0, 0, 0, ctx.dpi(6))
}

/** 押せる色付きのボタン（主役） */
fun primaryButton(ctx: Context, t: String, onClick: () -> Unit) = TextView(ctx).apply {
    text = t; textSize = 16f; setTextColor(Pal.INK); gravity = Gravity.CENTER
    setTypeface(typeface, Typeface.BOLD)
    background = roundRect(ctx, Pal.ACCENT, 14f)
    isClickable = true
    setOnClickListener { onClick() }
}

/** 控えめなボタン／チップ */
fun chip(ctx: Context, t: String, selected: Boolean = false, onClick: () -> Unit) = TextView(ctx).apply {
    text = t; textSize = 13.5f; gravity = Gravity.CENTER
    setPadding(ctx.dpi(14), ctx.dpi(9), ctx.dpi(14), ctx.dpi(9))
    isClickable = true
    setOnClickListener { onClick() }
    paint(selected)
}

fun TextView.paint(selected: Boolean) {
    setTextColor(if (selected) Pal.INK else Pal.TEXT)
    background = if (selected) roundRect(context, Pal.ACCENT, 20f) else roundRect(context, 0x00000000, 20f, Pal.LINE)
}

/** ラベルと説明つきの切り替え */
class ToggleRow(ctx: Context, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) : LinearLayout(ctx) {
    val switch = Switch(ctx)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, ctx.dpi(8), 0, ctx.dpi(8))
        val col = LinearLayout(ctx).apply { orientation = VERTICAL }
        col.addView(label(ctx, title, 15.5f))
        if (sub != null) col.addView(label(ctx, sub, 12.5f, Pal.SUB).apply { setPadding(0, ctx.dpi(2), 0, 0) })
        addView(col, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = ctx.dpi(12) })
        switch.isChecked = checked
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        switch.thumbTintList = ColorStateList(states, intArrayOf(Pal.ACCENT, 0xFF8A8A98.toInt()))
        switch.trackTintList = ColorStateList(states, intArrayOf(0x8025E8FF.toInt(), 0xFF2D2D39.toInt()))
        switch.setOnCheckedChangeListener { _, v -> onChange(v) }
        addView(switch)
        setOnClickListener { switch.toggle() }
    }
}

/** 名前と今の値を並べたスライダー */
class SliderRow(
    ctx: Context, name: String, min: Float, max: Float, step: Float, value: Float, unit: String,
    onChange: (Float) -> Unit,
) : LinearLayout(ctx) {
    init {
        orientation = VERTICAL
        setPadding(0, ctx.dpi(8), 0, ctx.dpi(2))
        val head = LinearLayout(ctx).apply { orientation = HORIZONTAL }
        val valueText = label(ctx, "", 14.5f, Pal.ACCENT, true)
        head.addView(label(ctx, name, 15.5f), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        head.addView(valueText)
        addView(head)
        fun show(v: Float) { valueText.text = "${if (step < 1f) "%.1f".format(v) else v.toInt().toString()} $unit" }
        show(value)
        val bar = SeekBar(ctx).apply {
            this.max = ((max - min) / step).toInt()
            progress = ((value - min) / step).toInt().coerceIn(0, this.max)
            progressTintList = ColorStateList.valueOf(Pal.ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(0xFF2D2D39.toInt())
            thumbTintList = ColorStateList.valueOf(Pal.ACCENT)
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
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, ctx.dpi(40)))
    }
}

/** 横に並んだ選択肢（ひとつだけ選ぶ） */
class Segmented<T>(ctx: Context, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) : LinearLayout(ctx) {
    init {
        orientation = HORIZONTAL
        background = roundRect(ctx, 0xFF0F0F15.toInt(), 14f, Pal.LINE)
        setPadding(ctx.dpi(3), ctx.dpi(3), ctx.dpi(3), ctx.dpi(3))
        val cells = ArrayList<Pair<TextView, T>>()
        for ((name, v) in options) {
            val cell = TextView(ctx).apply {
                text = name; textSize = 13.5f; gravity = Gravity.CENTER; maxLines = 2
                setPadding(ctx.dpi(4), ctx.dpi(11), ctx.dpi(4), ctx.dpi(11))
                isClickable = true
            }
            cells += cell to v
            addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        fun refresh(cur: T) {
            for ((c, v) in cells) {
                val on = v == cur
                c.setTextColor(if (on) Pal.INK else Pal.SUB)
                c.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
                c.background = if (on) roundRect(ctx, Pal.ACCENT, 11f) else null
            }
        }
        refresh(selected)
        for ((c, v) in cells) c.setOnClickListener { refresh(v); onSelect(v) }
    }
}

/**
 * 画面の一番上の見本。いま選んでいる見た目・文字・速さで、小さな「スマホの画面」の中を実際に跳ね回る。
 * 壁（キーボード風の板）と部品（ボタン風の箱）も置いてあって、反射のしかたが見える。
 */
class HeroView(ctx: Context) : View(ctx) {
    private val painter = LogoPainter()
    private val sim = Sim()
    private val params = Params(interference = Interference.REFLECT_ONLY)
    private var cfg = AppSettings.load(ctx)
    private var bounds = Box(0f, 0f, 1f, 1f)
    private var obstacles: List<Obstacle> = emptyList()
    private var lastNs = 0L
    private var ready = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF050508.toInt() }
    private val gridPaint = Paint().apply { color = 0xFF14141C.toInt(); strokeWidth = 1f }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF191923.toInt() }
    private val wallLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF2E2E3D.toInt() }
    private val tag = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF55556A.toInt() }
    private val rect = RectF()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reload() }

    init { painter.image = CustomLogo.loadMask(ctx) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        AppSettings.prefs(context).registerOnSharedPreferenceChangeListener(listener)
        reload()
    }

    override fun onDetachedFromWindow() {
        AppSettings.prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
        super.onDetachedFromWindow()
    }

    private fun reload() {
        val c = AppSettings.load(context)
        if (c.imageVersion != cfg.imageVersion) painter.image = CustomLogo.loadMask(context)
        cfg = c
        applyParams()
        invalidate()
    }

    private fun applyParams() {
        if (width == 0) return
        val d = resources.displayMetrics.density
        params.speed = (cfg.speedDp * 0.8f + 30f) * d
        params.logoH = (cfg.logoHeightDp * 0.9f).coerceIn(28f, 64f) * d
        params.logoW = params.logoH * painter.aspect(cfg.style)
        params.colorMode = cfg.colorMode
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        bounds = Box(0f, 0f, w.toFloat(), h.toFloat())
        val d = resources.displayMetrics.density
        obstacles = listOf(
            Obstacle(1, Box(w * 0.08f, h - 46 * d, w * 0.92f, h.toFloat())),                       // キーボード風の板
            Obstacle(Obstacle.SOFT_ID, Box(w * 0.62f, h * 0.2f, w * 0.88f, h * 0.2f + 40 * d), soft = true),   // ボタン風の箱
            Obstacle(Obstacle.SOFT_ID, Box(w * 0.1f, h * 0.18f, w * 0.34f, h * 0.18f + 54 * d), soft = true),   // 画像風の箱
        )
        applyParams()
        sim.reset(bounds, params)
        ready = true
    }

    override fun onDraw(c: Canvas) {
        if (!ready) return
        val d = resources.displayMetrics.density
        val now = SystemClock.elapsedRealtimeNanos()
        val dt = if (lastNs == 0L) 1f / 60f else ((now - lastNs) / 1e9f).coerceAtMost(0.05f)
        lastNs = now
        sim.update(dt, bounds, obstacles, params)

        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(rect, 18 * d, 18 * d, bgPaint)
        c.save()
        c.clipRect(rect)
        var gx = 0f
        while (gx < width) { c.drawLine(gx, 0f, gx, height.toFloat(), gridPaint); gx += 28 * d }
        var gy = 0f
        while (gy < height) { c.drawLine(0f, gy, width.toFloat(), gy, gridPaint); gy += 28 * d }

        wallLine.strokeWidth = d
        tag.textSize = 10.5f * d
        for (o in obstacles) {
            rect.set(o.rect.l, o.rect.t, o.rect.r, o.rect.b)
            val r = if (o.soft) 10 * d else 14 * d
            c.drawRoundRect(rect, r, r, wallPaint)
            c.drawRoundRect(rect, r, r, wallLine)
            c.drawText(if (o.soft) (if (o.rect.h > 48 * d) "画像" else "ボタン") else "キーボード", rect.left + 8 * d, rect.top + 15 * d, tag)
        }
        val l = sim.logo(params)
        painter.draw(c, l.l, l.t, l.w, l.h, cfg.style, sim.color(params), 255, cfg.label, SystemClock.uptimeMillis() / 1000f)
        c.restore()
        rect.set(d / 2, d / 2, width - d / 2, height - d / 2)
        wallLine.strokeWidth = d
        c.drawRoundRect(rect, 18 * d, 18 * d, wallLine)
        postInvalidateOnAnimation()
    }
}

