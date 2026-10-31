package jp.haruto.phonebounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import jp.haruto.phonebounce.core.LogoGeometry
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * ロゴを描く。画面の重ね表示（LogoView）と、設定画面の見本（StylePreview）で同じものを使う。
 * 形は LogoGeometry（元の Desktop Bounce のロゴの実測）。スタイルごとに見た目だけを変える。
 */
class LogoPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSkewX = LogoGeometry.SKEW
        textAlign = Paint.Align.CENTER
    }
    private val bmp = Paint().apply { isFilterBitmap = true }
    private val nearest = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val disc = Path()
    private val tmp = Path()
    private val b = Rect()

    /** 「画像」スタイルの形（白＋アルファ）。null なら、クラシックで代用 */
    var image: Bitmap? = null

    /** 文字の下の飾り。当たり判定の枠（aspect・heightScale）もこれに合わせて変わる */
    var decor: Decor = Decor.DISC

    private var cacheKey = ""
    private var cache: Bitmap? = null

    /** 枠の幅/高さ。画像スタイルは画像の比率、ほかは元のロゴの比率 */
    fun aspect(style: LogoStyle, label: String = "DVD"): Float {
        val im = image
        if (style == LogoStyle.IMAGE && im != null) return (im.width.toFloat() / im.height).coerceIn(0.3f, 4f)
        if (decor != Decor.NONE) return LogoGeometry.ASPECT
        // 飾りなし: 枠は文字のインクにぴったり（幅は文字の長さで決まる。字の左右の余白は含めない）
        return (ink(label).first / 100f).coerceIn(0.8f, 8f)
    }

    private var inkLabel: String? = null
    private var inkSize = Pair(100f, 0f)

    /** 文字（高さ 100 に正規化）の、実際に塗られる幅と、描く中心からのずれ。斜体・字の余白も含めて、いちど描いて測る */
    private fun ink(label: String): Pair<Float, Float> {
        if (label == inkLabel) return inkSize
        text.style = Paint.Style.FILL
        text.textSize = 100f
        text.getTextBounds("H", 0, 1, b)
        text.textSize = 100f * 100f / max(1, b.height())
        val bw = (text.measureText(label) + 160f).toInt()
        val bm = Bitmap.createBitmap(bw, 180, Bitmap.Config.ARGB_8888)
        text.color = Color.WHITE; text.alpha = 255
        Canvas(bm).drawText(label, bw / 2f, 130f, text)
        var l = bw; var r = -1
        val col = IntArray(180)
        for (x in 0 until bw) {
            bm.getPixels(col, 0, 1, x, 0, 1, 180)
            if (col.any { Color.alpha(it) > 60 }) { if (x < l) l = x; r = x }
        }
        bm.recycle()
        inkLabel = label
        inkSize = if (r < l) Pair(100f, 0f) else Pair((r - l + 1).toFloat(), (l + r + 1) / 2f - bw / 2f)
        return inkSize
    }

    /** 「ロゴの大きさ」は、飾りがあるときの全体の高さを基準にする。飾りなしは文字だけなので、文字が同じ大きさになるよう枠を低くする */
    fun heightScale(style: LogoStyle): Float =
        if (style == LogoStyle.IMAGE && image != null) 1f else if (decor == Decor.NONE) LogoGeometry.TEXT_BOTTOM else 1f

    /** 飾りの上端（枠の高さに対する割合） */
    private fun decorTop() = when (decor) {
        Decor.LINE -> 0.86f
        Decor.PLATE -> 0.66f
        else -> LogoGeometry.DISC_TOP
    }

    /** 枠の外へはみ出す量（光・フチ・影）。再描画の範囲に使う */
    fun overflow(style: LogoStyle, h: Float): Float = when (style) {
        LogoStyle.NEON -> h * 0.18f
        LogoStyle.OUTLINE -> h * 0.12f
        LogoStyle.CRT -> h * 0.05f
        else -> h * 0.02f
    }

    fun draw(c: Canvas, x: Float, y: Float, w: Float, h: Float, style: LogoStyle, color: Int, alpha: Int, label: String, t: Float) {
        when (style) {
            LogoStyle.CLASSIC -> classic(c, x, y, w, h, color, alpha, label)
            LogoStyle.NEON -> neon(c, x, y, w, h, color, alpha, label)
            LogoStyle.CRT -> crt(c, x, y, w, h, color, alpha, label, t)
            LogoStyle.PIXEL -> pixel(c, x, y, w, h, color, alpha, label)
            LogoStyle.OUTLINE -> outline(c, x, y, w, h, color, alpha, label)
            LogoStyle.SPIN -> spin(c, x, y, w, h, color, alpha, label, t)
            LogoStyle.IMAGE -> imageStyle(c, x, y, w, h, color, alpha, label)
        }
    }

    // ---------------- 形 ----------------

    /** 文字の下の飾りの形（円盤・下線・リング・板。なしは空） */
    private fun discPath(x: Float, y: Float, w: Float, h: Float, p: Path) {
        p.reset()
        p.fillType = Path.FillType.EVEN_ODD
        val top = y + h * decorTop()
        val dh = y + h - top
        when (decor) {
            Decor.NONE -> {}
            Decor.DISC, Decor.RING -> {
                p.addOval(x, top, x + w, y + h, Path.Direction.CW)
                val hw = w * (if (decor == Decor.RING) 0.62f else LogoGeometry.HOLE_W)
                val hh = dh * (if (decor == Decor.RING) 0.58f else LogoGeometry.HOLE_H_OF_DISC)
                p.addOval(x + (w - hw) / 2f, top + (dh - hh) / 2f, x + (w + hw) / 2f, top + (dh + hh) / 2f, Path.Direction.CW)
            }
            Decor.LINE -> p.addRoundRect(x, top, x + w, y + h, dh / 2f, dh / 2f, Path.Direction.CW)
            Decor.PLATE -> p.addRoundRect(x, top, x + w, y + h, dh * 0.32f, dh * 0.32f, Path.Direction.CW)
        }
    }

    /** 文字の大きさと位置を決める。文字の高さは帯に合わせ、横が広すぎるときだけ縮める。戻り値は (中心x, ベースライン) */
    private fun layoutText(x: Float, y: Float, w: Float, h: Float, label: String): Pair<Float, Float> {
        val band = if (decor == Decor.NONE) h else h * LogoGeometry.TEXT_BOTTOM
        text.style = Paint.Style.FILL
        text.textSize = band
        text.getTextBounds("H", 0, 1, b)
        val cap = max(1, b.height()).toFloat()
        var size = band * band / cap
        text.textSize = size
        val maxW = if (decor == Decor.NONE) w else w * LogoGeometry.TEXT_WIDTH
        val tw = if (decor == Decor.NONE) ink(label).first * band / 100f else text.measureText(label)
        if (tw > maxW) { size *= maxW / tw; text.textSize = size }
        text.getTextBounds("H", 0, 1, b)
        if (decor == Decor.NONE) {   // 実際に塗られる範囲の中心を、枠の中心に合わせる
            val (iw, off) = ink(label)
            val k = band / 100f * (size / (band * band / cap))
            return Pair(x + w / 2f - off * k, y + band)
        }
        // 斜体は、文字の上が右へ寄る。見た目の中心が合うように左へ戻す
        return Pair(x + w / 2f - b.height() * -LogoGeometry.SKEW / 2f, y + band)
    }

    // ---------------- スタイル ----------------

    private fun classic(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Int, label: String) {
        discPath(x, y, w, h, disc)
        fill.style = Paint.Style.FILL; fill.color = color; fill.alpha = alpha
        c.drawPath(disc, fill)
        val (cx, base) = layoutText(x, y, w, h, label)
        text.style = Paint.Style.FILL; text.color = color; text.alpha = alpha
        c.drawText(label, cx, base, text)
    }

    private fun neon(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Int, label: String) {
        discPath(x, y, w, h, disc)
        val (cx, base) = layoutText(x, y, w, h, label)
        val core = h * 0.028f
        // 外側へ、だんだん太く・うすく重ねて、光のにじみにする
        val glow = listOf(core * 7f to 0.07f, core * 4.5f to 0.12f, core * 2.8f to 0.22f)
        for ((sw, a) in glow) {
            line.color = color; line.strokeWidth = sw; line.alpha = (alpha * a).roundToInt()
            c.drawPath(disc, line)
            text.style = Paint.Style.STROKE; text.strokeWidth = sw; text.color = color; text.alpha = (alpha * a).roundToInt()
            c.drawText(label, cx, base, text)
        }
        line.color = color; line.strokeWidth = core; line.alpha = alpha
        c.drawPath(disc, line)
        text.style = Paint.Style.STROKE; text.strokeWidth = core; text.color = color; text.alpha = alpha
        c.drawText(label, cx, base, text)
        // 芯を白っぽくして、光っている感じに
        val hot = blend(color, Color.WHITE, 0.55f)
        line.color = hot; line.strokeWidth = core * 0.4f; line.alpha = alpha
        c.drawPath(disc, line)
        text.strokeWidth = core * 0.4f; text.color = hot; text.alpha = alpha
        c.drawText(label, cx, base, text)
        text.style = Paint.Style.FILL
    }

    private fun crt(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Int, label: String, t: Float) {
        val pad = 3
        val bw = ceil(w).toInt() + pad * 2
        val bh = ceil(h).toInt() + pad * 2
        val bm = cached("crt|$color|$label|$bw|$bh|$decor") {
            val m = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val cv = Canvas(m)
            val f = max(1.2f, w * 0.014f)
            val bx = pad.toFloat() + f
            val ww = w - 2f * f
            classic(cv, bx - f, pad.toFloat(), ww, h, 0xFFFF2A2A.toInt(), 120, label)   // 赤い色ズレ（左）
            classic(cv, bx + f, pad.toFloat(), ww, h, 0xFF22E0FF.toInt(), 120, label)   // 青緑の色ズレ（右）
            classic(cv, bx, pad.toFloat(), ww, h, color, 255, label)
            val sl = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); this.color = 0x70000000 }
            var yy = 0
            while (yy < bh) { cv.drawRect(0f, yy.toFloat(), bw.toFloat(), yy + 1f, sl); yy += 3 }   // 走査線
            m
        }
        val flick = 0.90f + 0.10f * sin(t * 47f) * sin(t * 3.1f)   // ブラウン管のちらつき
        bmp.alpha = (alpha * flick).roundToInt().coerceIn(0, 255)
        c.drawBitmap(bm, x - pad, y - pad, bmp)
    }

    private fun pixel(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Int, label: String) {
        val pw = 60
        val ph = max(8, (pw / (w / h)).roundToInt())
        val bm = cached("pix|$color|$label|$pw|$ph|$decor") {
            val m = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
            classic(Canvas(m), 0f, 0f, pw.toFloat(), ph.toFloat(), color, 255, label)
            val px = IntArray(pw * ph)
            m.getPixels(px, 0, pw, 0, 0, pw, ph)
            for (i in px.indices) px[i] = if (Color.alpha(px[i]) > 110) color or (0xFF shl 24) else 0   // 半透明をなくして、ドットにする
            m.setPixels(px, 0, pw, 0, 0, pw, ph)
            m
        }
        nearest.alpha = alpha
        c.drawBitmap(bm, null, RectF(x, y, x + w, y + h), nearest)
    }

    private fun outline(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Int, label: String) {
        discPath(x, y, w, h, disc)
        val (cx, base) = layoutText(x, y, w, h, label)
        val rim = h * 0.075f
        val sh = h * 0.035f
        // 影 → 白いフチ → 色（フチは、円盤と文字の外側へ広げて、ひとつながりの台紙に見せる）
        c.save(); c.translate(sh, sh * 1.3f)
        line.color = Color.BLACK; line.strokeWidth = rim; line.alpha = (alpha * 0.30f).roundToInt()
        fill.style = Paint.Style.FILL; fill.color = Color.BLACK; fill.alpha = (alpha * 0.30f).roundToInt()
        c.drawPath(disc, fill); c.drawPath(disc, line)
        text.style = Paint.Style.FILL_AND_STROKE; text.strokeWidth = rim; text.color = Color.BLACK; text.alpha = (alpha * 0.30f).roundToInt()
        c.drawText(label, cx, base, text)
        c.restore()
        line.color = Color.WHITE; line.strokeWidth = rim; line.alpha = alpha
        fill.color = Color.WHITE; fill.alpha = alpha
        c.drawPath(disc, fill); c.drawPath(disc, line)
        text.style = Paint.Style.FILL_AND_STROKE; text.strokeWidth = rim; text.color = Color.WHITE; text.alpha = alpha
        c.drawText(label, cx, base, text)
        text.style = Paint.Style.FILL; text.strokeWidth = 0f
        classic(c, x, y, w, h, color, alpha, label)
    }

    private fun spin(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Int, label: String, t: Float) {
        classic(c, x, y, w, h, color, alpha, label)
        discPath(x, y, w, h, disc)
        if (decor == Decor.NONE) return
        val top = y + h * decorTop()
        val dh = y + h - top
        c.save()
        c.clipPath(disc)
        // 円盤の溝（うすい線）
        line.color = Color.BLACK; line.strokeWidth = max(1f, h * 0.012f); line.alpha = (alpha * 0.16f).roundToInt()
        for (k in if (decor == Decor.DISC) listOf(0.82f, 0.64f) else emptyList()) {
            val ww = w * k
            val hh = dh * k
            c.drawOval(x + (w - ww) / 2f, top + (dh - hh) / 2f, x + (w + ww) / 2f, top + (dh + hh) / 2f, line)
        }
        // 光の帯が、左から右へ流れる（2.6秒で一周）
        val p = (t % 2.6f) / 2.6f
        val cxl = x + (p * 1.7f - 0.35f) * w
        val skew = dh * 0.9f
        for ((bwK, a) in listOf(0.22f to 0.14f, 0.13f to 0.20f, 0.05f to 0.30f)) {
            val bw = w * bwK
            tmp.reset()
            tmp.moveTo(cxl - bw / 2f + skew / 2f, top)
            tmp.lineTo(cxl + bw / 2f + skew / 2f, top)
            tmp.lineTo(cxl + bw / 2f - skew / 2f, y + h)
            tmp.lineTo(cxl - bw / 2f - skew / 2f, y + h)
            tmp.close()
            fill.style = Paint.Style.FILL; fill.color = Color.WHITE; fill.alpha = (alpha * a).roundToInt()
            c.drawPath(tmp, fill)
        }
        c.restore()
    }

    private fun imageStyle(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Int, label: String) {
        val im = image
        if (im == null) { classic(c, x, y, w, h, color, alpha, label); return }
        bmp.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        bmp.alpha = alpha
        c.drawBitmap(im, null, RectF(x, y, x + w, y + h), bmp)
        bmp.colorFilter = null
    }

    // ---------------- 道具 ----------------

    private fun cached(key: String, build: () -> Bitmap): Bitmap {
        val have = cache
        if (have != null && cacheKey == key) return have
        cache?.recycle()
        return build().also { cache = it; cacheKey = key }
    }

    private fun blend(a: Int, bb: Int, k: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * k).roundToInt().coerceIn(0, 255)
        return Color.argb(255, ch(Color.red(a), Color.red(bb)), ch(Color.green(a), Color.green(bb)), ch(Color.blue(a), Color.blue(bb)))
    }

    companion object {
        /** 画像を「白＋アルファ」の形に直す。アルファがあればそれ、なければ明るさ（元の Desktop Bounce の custom_logo と同じ） */
        fun toMask(src: Bitmap): Bitmap {
            val w = src.width
            val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            var hasAlpha = false
            for (p in px) if (Color.alpha(p) < 250) { hasAlpha = true; break }
            for (i in px.indices) {
                val p = px[i]
                val a = if (hasAlpha) Color.alpha(p)
                else ((Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000)
                px[i] = (a shl 24) or 0x00FFFFFF
            }
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(px, 0, w, 0, 0, w, h)
            return out
        }
    }
}
