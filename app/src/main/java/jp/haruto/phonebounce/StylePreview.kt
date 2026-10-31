package jp.haruto.phonebounce

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View

/** 設定画面の見本。見た目のスタイルを、そのまま小さく描く（ブラウン管・ディスク回転は動く）。 */
class StylePreview(ctx: Context, val style: LogoStyle, image: Bitmap?) : View(ctx) {
    private val painter = LogoPainter().also { it.image = image }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF050508.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF1F1EE.toInt(); isFakeBoldText = true }
    private val note = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9B9BA8.toInt() }
    private val box = RectF()

    var chosen = false
        set(v) { field = v; invalidate() }

    private var decor = Decor.DISC
    private var label = "DVD"
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reload() }

    init { isClickable = true; reload() }

    private fun reload() {
        val c = AppSettings.load(context)
        decor = c.decor; label = c.label
        painter.decor = decor
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        AppSettings.prefs(context).registerOnSharedPreferenceChangeListener(listener)
        reload()
    }

    override fun onDetachedFromWindow() {
        AppSettings.prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
        super.onDetachedFromWindow()
    }

    fun setImage(bm: Bitmap?) { painter.image = bm; invalidate() }

    /** 見本の色。クラシックは、元のアプリで見えた緑 */
    private fun sample(): Int = when (style) {
        LogoStyle.CLASSIC -> 0xFF3CE65A.toInt()
        LogoStyle.NEON -> 0xFF25E8FF.toInt()
        LogoStyle.CRT -> 0xFF3DFF7A.toInt()
        LogoStyle.PIXEL -> 0xFFFFD400.toInt()
        LogoStyle.OUTLINE -> 0xFFFF2D95.toInt()
        LogoStyle.SPIN -> 0xFFFF7A1A.toInt()
        LogoStyle.IMAGE -> 0xFF7A6BFF.toInt()
    }

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(box, 12 * d, 12 * d, bg)
        border.strokeWidth = (if (chosen) 3f else 1f) * d
        border.color = if (chosen) 0xFF25E8FF.toInt() else 0xFF2D2D39.toInt()
        c.drawRoundRect(box.left + border.strokeWidth / 2, box.top + border.strokeWidth / 2,
            box.right - border.strokeWidth / 2, box.bottom - border.strokeWidth / 2, 12 * d, 12 * d, border)

        val lh = height * 0.42f * painter.heightScale(style)
        val lw = lh * painter.aspect(style, label)
        val maxW = width * 0.72f
        val k = if (lw > maxW) maxW / lw else 1f
        val w = lw * k
        val h = lh * k
        val t = SystemClock.uptimeMillis() / 1000f
        painter.draw(c, (width - w) / 2f, height * 0.12f + height * 0.42f / 2f - h / 2f, w, h, style, sample(), 255, label, t)

        title.textSize = 14 * d
        note.textSize = 11 * d
        c.drawText(style.title, 12 * d, height - 26 * d, title)
        c.drawText(if (style == LogoStyle.IMAGE && painter.image == null) "タップして画像を選ぶ" else style.note, 12 * d, height - 10 * d, note)
        if (style == LogoStyle.SPIN || style == LogoStyle.CRT) postInvalidateOnAnimation()
    }
}
