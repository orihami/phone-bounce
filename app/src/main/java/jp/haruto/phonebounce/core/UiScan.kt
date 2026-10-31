package jp.haruto.phonebounce.core

/**
 * 画面の部品（ボタン・画像・文字などの枠）を拾う、Android に依存しない部分。
 *
 * 使うのは「クラス名」「位置と大きさ」「見えているか」「押せるか／入力できるか」だけ。
 * **文字の中身・説明文・入力内容・ID は、このインターフェースに無い**（読めない作りにしてある）。
 * Android 側の実装は ScreenScanner。読んではいけない項目に触れていないことは PrivacyGuardTest が確かめる。
 */
interface UiNode {
    val className: String
    val left: Int
    val top: Int
    val right: Int
    val bottom: Int
    val visible: Boolean
    val clickable: Boolean
    val editable: Boolean
    val childCount: Int
    fun child(i: Int): UiNode?

    /** 使い終わった合図（Android の古い版では、ノードの返却が要る） */
    fun release() {}
}

/** どの種類の部品を壁にするか */
class ElementKinds(val controls: Boolean = true, val images: Boolean = true, val text: Boolean = false)

object ElementScan {
    private val CONTROL = listOf(
        "Button", "EditText", "CheckBox", "Switch", "RadioButton", "SeekBar", "Spinner", "ToggleButton",
        "AutoCompleteTextView", "CheckedTextView", "Chip", "NumberPicker", "RatingBar",
    )
    private val IMAGE = listOf("ImageView", "VideoView", "SurfaceView", "TextureView")

    const val CONTROL_KIND = 1
    const val IMAGE_KIND = 2
    const val TEXT_KIND = 3

    /** 部品の種類。どれでもなければ 0 */
    fun kindOf(n: UiNode): Int {
        val c = n.className
        return when {
            CONTROL.any { c.contains(it) } -> CONTROL_KIND
            IMAGE.any { c.contains(it) } -> IMAGE_KIND
            n.clickable || n.editable -> CONTROL_KIND       // 押せる・入力できる自作の部品（カードなど）
            c.contains("TextView") -> TEXT_KIND
            else -> 0
        }
    }

    /**
     * 木をたどって、壁にする部品の枠を集める。
     *  - 見えていない部品は、その中身ごと飛ばす
     *  - 選ばれた部品は、ひとかたまりの壁として数え、中には入らない（二重に数えない）
     *  - 画面の 35% より大きい部品は、入れ物とみなして中へ入る
     *  - 小さすぎる部品（minPx 未満）は無視
     * 枠は clip（窓の範囲）で切り取る。
     */
    fun collect(root: UiNode, clip: Box, minPx: Float, kinds: ElementKinds, maxNodes: Int = 600, maxDepth: Int = 48): List<Box> {
        val out = ArrayList<Box>()
        val seen = HashSet<Box>()
        var visited = 0
        val maxArea = clip.area * 0.35f

        fun walk(n: UiNode, depth: Int) {
            if (visited++ >= maxNodes || depth > maxDepth || !n.visible) return
            val r = Box(
                maxOf(n.left.toFloat(), clip.l), maxOf(n.top.toFloat(), clip.t),
                minOf(n.right.toFloat(), clip.r), minOf(n.bottom.toFloat(), clip.b),
            )
            if (r.w <= 0f || r.h <= 0f) return
            val k = kindOf(n)
            val enabled = when (k) {
                CONTROL_KIND -> kinds.controls
                IMAGE_KIND -> kinds.images
                TEXT_KIND -> kinds.text
                else -> false
            }
            if (enabled && r.w >= minPx && r.h >= minPx && r.area <= maxArea) {
                if (seen.add(r)) out.add(r)
                return
            }
            for (i in 0 until n.childCount) {
                val c = n.child(i) ?: continue
                try { walk(c, depth + 1) } finally { c.release() }
            }
        }
        walk(root, 0)
        return out
    }
}
