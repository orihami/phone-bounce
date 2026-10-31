package jp.haruto.phonebounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** 設定画面が最後まで組み上がること。環境変数 PB_PREVIEW_DIR があれば、画面全体を画像で書き出す（見た目の確認用） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class MainScreenTest {
    @Test fun theSettingsScreenBuildsAndRenders() {
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val scroll = act.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView
        val content = scroll.getChildAt(0)
        val w = act.resources.displayMetrics.widthPixels
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        content.layout(0, 0, w, content.measuredHeight)
        assertTrue("画面が短すぎる: ${content.measuredHeight}", content.measuredHeight > act.resources.displayMetrics.heightPixels)
        val bm = Bitmap.createBitmap(w, content.measuredHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        c.drawColor(Pal.BG)
        content.draw(c)
        System.getenv("PB_PREVIEW_DIR")?.let { dir ->
            File(dir).mkdirs()
            FileOutputStream(File(dir, "main.png")).use { bm.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
    }
}
