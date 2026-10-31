package jp.haruto.phonebounce.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementScanTest {
    private class N(
        override val className: String,
        override val left: Int, override val top: Int, override val right: Int, override val bottom: Int,
        override val visible: Boolean = true,
        override val clickable: Boolean = false,
        override val editable: Boolean = false,
        private val kids: List<N> = emptyList(),
    ) : UiNode {
        override val childCount: Int get() = kids.size
        override fun child(i: Int): UiNode? = kids.getOrNull(i)
    }

    private val screen = Box(0f, 0f, 1080f, 2400f)
    private fun scan(root: N, kinds: ElementKinds = ElementKinds(), min: Float = 60f) = ElementScan.collect(root, screen, min, kinds)

    private val ui = N("android.widget.FrameLayout", 0, 0, 1080, 2400, kids = listOf(
        N("android.widget.Button", 100, 200, 400, 320),
        N("android.widget.ImageView", 500, 200, 800, 500),
        N("android.widget.TextView", 100, 600, 900, 700),
    ))

    @Test fun buttonsAndImagesAreWalls_textIsNotByDefault() {
        val r = scan(ui)
        assertEquals(2, r.size)
        assertTrue(r.contains(Box(100f, 200f, 400f, 320f)))
        assertTrue(r.contains(Box(500f, 200f, 800f, 500f)))
    }

    @Test fun kindsCanBeSwitchedOnAndOff() {
        assertEquals(3, scan(ui, ElementKinds(controls = true, images = true, text = true)).size)
        assertEquals(1, scan(ui, ElementKinds(controls = true, images = false, text = false)).size)
        assertEquals(0, scan(ui, ElementKinds(controls = false, images = false, text = false)).size)
    }

    @Test fun aBigContainerIsEnteredNotCounted() {
        val list = N("androidx.recyclerview.widget.RecyclerView", 0, 300, 1080, 2000, clickable = true, kids = listOf(
            N("android.widget.Button", 50, 400, 350, 520),
        ))
        val root = N("android.widget.FrameLayout", 0, 0, 1080, 2400, kids = listOf(list))
        val r = scan(root)
        assertEquals(listOf(Box(50f, 400f, 350f, 520f)), r)    // 一覧そのもの（画面の 35% 超）は壁にせず、中のボタンだけ
    }

    @Test fun aClickableCardIsOneSolidBlockAndItsChildrenAreNotCountedTwice() {
        val card = N("android.view.View", 100, 800, 980, 1100, clickable = true, kids = listOf(
            N("android.widget.ImageView", 120, 820, 300, 1000),
            N("android.widget.Button", 700, 1000, 960, 1080),
        ))
        val r = scan(N("android.widget.FrameLayout", 0, 0, 1080, 2400, kids = listOf(card)))
        assertEquals(listOf(Box(100f, 800f, 980f, 1100f)), r)
    }

    @Test fun invisibleSubtreesTinyThingsAndOffscreenPartsAreHandled() {
        val root = N("android.widget.FrameLayout", 0, 0, 1080, 2400, kids = listOf(
            N("android.widget.LinearLayout", 0, 0, 500, 500, visible = false, kids = listOf(N("android.widget.Button", 10, 10, 300, 200))),
            N("android.widget.Button", 600, 600, 630, 630),                     // 小さすぎる
            N("android.widget.Button", 1000, 2300, 1300, 2500),                 // 画面の外へはみ出す
        ))
        val r = scan(root)
        assertEquals(listOf(Box(1000f, 2300f, 1080f, 2400f)), r)               // 画面の範囲で切り取る
    }

    @Test fun checkedTextViewIsAControlNotPlainText() {
        val root = N("android.widget.FrameLayout", 0, 0, 1080, 2400, kids = listOf(N("android.widget.CheckedTextView", 100, 100, 700, 200)))
        assertEquals(1, scan(root, ElementKinds(controls = true, images = false, text = false)).size)
        assertEquals(0, scan(root, ElementKinds(controls = false, images = false, text = true)).size)
    }

    @Test fun duplicateRectsAreCountedOnce_andTheNodeLimitStopsTheWalk() {
        val same = List(5) { N("android.widget.Button", 100, 100, 400, 200) }
        assertEquals(1, scan(N("android.widget.FrameLayout", 0, 0, 1080, 2400, kids = same)).size)
        val many = List(50) { N("android.widget.Button", 10 + it * 5, 1000, 80 + it * 5, 1100) }
        val limited = ElementScan.collect(N("android.widget.FrameLayout", 0, 0, 1080, 2400, kids = many), screen, 60f, ElementKinds(), maxNodes = 10)
        assertTrue(limited.size <= 9)
    }
}
