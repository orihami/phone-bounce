package jp.haruto.phonebounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** アプリのアイコンとタイルの絵が、端に切れずに描けること。PB_PREVIEW_DIR があれば icons.png に書き出す（目で確かめる用） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconRenderTest {
    private fun render(res: Int, size: Int, bg: Int): Bitmap {
        val ctx = RuntimeEnvironment.getApplication()
        val bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm); c.drawColor(bg)
        val d: android.graphics.drawable.Drawable = ctx.getDrawable(res)!!
        d.setBounds(0, 0, size, size); d.draw(c)
        return bm
    }

    @Test fun launcherIconStaysInsideTheSafeZone() {
        val size = 432
        val fg = render(R.drawable.ic_fg, size, Color.TRANSPARENT)
        var l = size; var r = 0
        val u = size / 108f
        for (y in 0 until size) for (x in 0 until size) if (Color.alpha(fg.getPixel(x, y)) > 100) {
            if (x < l) l = x; if (x > r) r = x
            // 丸く切り抜く端末でも欠けない範囲（中心から半径 36 ＝ 表示される 72 の円）に、絵のすべての点が入る
            val dx = x / u - 54f; val dy = y / u - 54f
            assertTrue("丸い切り抜きからはみ出す ($x,$y)", dx * dx + dy * dy <= 36.5f * 36.5f)
        }
        assertTrue("絵が小さすぎる", (r - l) / u > 48f)
        val tile = render(R.drawable.ic_tile, 96, Color.TRANSPARENT)
        var n = 0
        for (y in 0 until 96) for (x in 0 until 96) if (Color.alpha(tile.getPixel(x, y)) > 100) n++
        assertTrue("タイルの絵が描けていない", n > 300)
        System.getenv("PB_PREVIEW_DIR")?.let { dir ->
            File(dir).mkdirs()
            val sheet = Bitmap.createBitmap(size * 2 + 120, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(sheet); c.drawColor(0xFF777777.toInt())
            c.drawBitmap(render(R.drawable.ic_fg, size, 0xFF050507.toInt()), 0f, 0f, null)
            c.drawBitmap(render(R.drawable.ic_mono, size, 0xFF333333.toInt()), size.toFloat(), 0f, null)
            c.drawBitmap(Bitmap.createScaledBitmap(render(R.drawable.ic_tile, 96, 0xFF333333.toInt()), 96, 96, true), size * 2f + 12f, 12f, null)
            FileOutputStream(File(dir, "icons.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
