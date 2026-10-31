package jp.haruto.phonebounce

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max

/** 画面いっぱいの透明な重ね表示に、ロゴだけを描く。タッチはすべて素通りする（BounceService 側で設定）。 */
class LogoView(ctx: Context) : View(ctx) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xCCFF3B30.toInt()
        strokeWidth = 4f
    }
    private val areaPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = 0x8825E8FF.toInt()
        strokeWidth = 3f
    }

    private var logo = RectF()
    private var color = 0xFFFFFFFF.toInt()
    private var translucent = false
    private var label = "DVD"
    private var walls: List<RectF> = emptyList()
    private var area = RectF()
    private var showWalls = false
    private val dirty = Rect()

    fun update(newLogo: RectF, color: Int, translucent: Boolean, label: String,
               walls: List<RectF>, area: RectF, showWalls: Boolean) {
        // 前の位置と今の位置だけを描き直す（画面全体を描き直さない）
        val pad = 6
        dirty.set(
            (minOf(logo.left, newLogo.left) - pad).toInt(), (minOf(logo.top, newLogo.top) - pad).toInt(),
            (max(logo.right, newLogo.right) + pad).toInt(), (max(logo.bottom, newLogo.bottom) + pad).toInt(),
        )
        val wide = showWalls || this.showWalls
        logo = RectF(newLogo)
        this.color = color
        this.translucent = translucent
        this.label = label
        this.walls = walls
        this.area = area
        this.showWalls = showWalls
        if (wide) invalidate() else invalidate(dirty)
    }

    override fun onDraw(c: Canvas) {
        if (showWalls) {
            c.drawRect(area, areaPaint)
            for (w in walls) c.drawRect(w, wallPaint)
        }
        val a = if (translucent) 110 else 255
        val sw = max(3f, logo.height() * 0.07f)
        ring.color = color; ring.alpha = a; ring.strokeWidth = sw
        c.drawOval(logo.left + sw / 2, logo.top + sw / 2, logo.right - sw / 2, logo.bottom - sw / 2, ring)
        text.color = color; text.alpha = a
        text.textSize = logo.height() * 0.56f
        val maxW = logo.width() * 0.78f
        val w = text.measureText(label)
        if (w > maxW) text.textSize = text.textSize * maxW / w
        c.drawText(label, logo.centerX(), logo.centerY() - (text.ascent() + text.descent()) / 2f, text)
    }
}
