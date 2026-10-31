package jp.haruto.phonebounce

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.max

/** 画面いっぱいの透明な重ね表示に、ロゴだけを描く。タッチはすべて素通りする（BounceService 側で設定）。 */
class LogoView(ctx: Context) : View(ctx) {
    val painter = LogoPainter()

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
    private var style = LogoStyle.CLASSIC
    private var walls: List<RectF> = emptyList()
    private var area = RectF()
    private var showWalls = false
    private val dirty = Rect()

    fun update(newLogo: RectF, color: Int, translucent: Boolean, label: String, style: LogoStyle,
               walls: List<RectF>, area: RectF, showWalls: Boolean) {
        // 前の位置と今の位置だけを描き直す（画面全体を描き直さない）。光・フチ・影ではみ出す分も含める
        val pad = max(painter.overflow(style, newLogo.height()), painter.overflow(this.style, logo.height())) + 8f
        dirty.set(
            (minOf(logo.left, newLogo.left) - pad).toInt(), (minOf(logo.top, newLogo.top) - pad).toInt(),
            (max(logo.right, newLogo.right) + pad).toInt(), (max(logo.bottom, newLogo.bottom) + pad).toInt(),
        )
        val wide = showWalls || this.showWalls || style != this.style
        logo = RectF(newLogo)
        this.color = color
        this.translucent = translucent
        this.label = label
        this.style = style
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
        painter.draw(
            c, logo.left, logo.top, logo.width(), logo.height(),
            style, color, if (translucent) 110 else 255, label, SystemClock.uptimeMillis() / 1000f,
        )
    }
}
