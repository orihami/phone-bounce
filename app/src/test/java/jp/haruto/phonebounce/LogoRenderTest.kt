package jp.haruto.phonebounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import jp.haruto.phonebounce.core.LogoGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/**
 * 見た目の描画テスト。Android の描画（Canvas）を PC 上で動かして、全スタイルが描けること・
 * 元のロゴの比率になっていることを確かめる。環境変数 PB_PREVIEW_DIR があれば、全スタイルを並べた画像を書き出す。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LogoRenderTest {
    private val bg = 0xFF0C0C11.toInt()

    private fun opaqueCount(bm: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int, threshold: Int = 40): Int {
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) if (Color.alpha(bm.getPixel(x, y)) > threshold) n++
        return n
    }

    /** 画像スタイルの確認用の形: 真ん中をくり抜いた四角（アルファの形として使われる） */
    private fun sampleImage(): Bitmap {
        val bm = Bitmap.createBitmap(120, 70, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        val p = Paint().apply { color = Color.WHITE }
        c.drawRoundRect(0f, 0f, 120f, 70f, 20f, 20f, p)
        p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        c.drawOval(40f, 20f, 80f, 50f, p)
        return bm
    }

    @Test fun everyStyleDrawsSomethingAndTheContactSheetIsSaved() {
        val styles = LogoStyle.values()
        val cols = 4
        val cw = 380
        val chh = 230
        val rows = ceil(styles.size / cols.toFloat()).toInt()
        val sheet = Bitmap.createBitmap(cols * cw, rows * chh, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(bg)
        val painter = LogoPainter().also { it.image = LogoPainter.toMask(sampleImage()) }
        val colors = mapOf(
            LogoStyle.CLASSIC to 0xFF3CE65A.toInt(), LogoStyle.NEON to 0xFF25E8FF.toInt(), LogoStyle.CRT to 0xFF3DFF7A.toInt(),
            LogoStyle.PIXEL to 0xFFFFD400.toInt(), LogoStyle.OUTLINE to 0xFFFF2D95.toInt(), LogoStyle.SPIN to 0xFFFF7A1A.toInt(),
            LogoStyle.IMAGE to 0xFF7A6BFF.toInt(),
        )
        val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB8B8C4.toInt(); textSize = 18f }
        for ((i, st) in styles.withIndex()) {
            val x0 = (i % cols) * cw
            val y0 = (i / cols) * chh
            val h = 110f
            val w = h * painter.aspect(st)
            painter.draw(c, x0 + (cw - w) / 2f, y0 + 50f, w, h, st, colors.getValue(st), 255, "DVD", 0.9f)
            c.drawText("${st.title}  ${st.note}", x0 + 14f, y0 + chh - 18f, cap)
            // どのスタイルも、見える画素がある
            assertTrue("${st.name} が描けていない", opaqueCount(sheet, x0 + 10, y0 + 30, x0 + cw - 10, y0 + 190, threshold = 60) in 300..(cw * 160))
        }
        System.getenv("PB_PREVIEW_DIR")?.let { dir ->
            File(dir).mkdirs()
            FileOutputStream(File(dir, "styles.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun classicKeepsTheProportionsOfTheOriginalLogo() {
        val h = 270
        val w = (h * LogoGeometry.ASPECT).toInt()
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)   // 透明な背景
        LogoPainter().draw(Canvas(bm), 0f, 0f, w.toFloat(), h.toFloat(), LogoStyle.CLASSIC, Color.WHITE, 255, "DVD", 0f)
        val discTop = (h * LogoGeometry.DISC_TOP).toInt()
        // 文字と円盤のあいだ（隙間）には、何も描かれない
        val gap = opaqueCount(bm, 0, (h * 0.556f).toInt(), w, discTop - 2)
        assertEquals("文字と円盤のあいだに描かれている", 0, gap)
        // 円盤は、左右いっぱいまで広がる（幅いっぱいの楕円）
        val rowMid = discTop + (h - discTop) / 2
        assertTrue("円盤の左端が足りない", Color.alpha(bm.getPixel(3, rowMid)) > 200)
        assertTrue("円盤の右端が足りない", Color.alpha(bm.getPixel(w - 4, rowMid)) > 200)
        // 円盤の真ん中は穴（透明）、そのすぐ外側は円盤
        assertEquals("穴が空いていない", 0, Color.alpha(bm.getPixel(w / 2, rowMid)))
        assertTrue("穴の外側が円盤になっていない", Color.alpha(bm.getPixel(w / 2 - (w * 0.18f).toInt(), rowMid)) > 200)
        // 円盤の下端は枠の下端いっぱい
        assertTrue(opaqueCount(bm, w / 2 - 40, h - 6, w / 2 + 40, h) > 0)
    }

    @Test fun anImageThatHasTransparencyBecomesAMaskAndTintsWithTheColor() {
        val mask = LogoPainter.toMask(sampleImage())
        assertEquals("穴（透明）が保たれる", 0, Color.alpha(mask.getPixel(60, 35)))
        assertEquals("形の部分は不透明", 255, Color.alpha(mask.getPixel(5, 35)))
        val bm = Bitmap.createBitmap(240, 140, Bitmap.Config.ARGB_8888)
        val painter = LogoPainter().also { it.image = mask }
        painter.draw(Canvas(bm), 0f, 0f, 240f, 140f, LogoStyle.IMAGE, 0xFFFF2D95.toInt(), 255, "", 0f)
        val px = bm.getPixel(10, 70)
        assertEquals(0xFF, Color.red(px))
        assertTrue("色が塗られていない: $px", Color.green(px) < 80)
    }

    @Test fun aPhotoWithoutTransparencyUsesBrightnessAsTheShape() {
        val photo = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        photo.eraseColor(Color.BLACK)
        for (y in 0 until 10) for (x in 0 until 5) photo.setPixel(x, y, Color.WHITE)
        val mask = LogoPainter.toMask(photo)
        assertTrue(Color.alpha(mask.getPixel(2, 5)) > 250)     // 明るい所 → 形
        assertTrue(Color.alpha(mask.getPixel(8, 5)) < 5)       // 暗い所 → 抜く
    }
}
