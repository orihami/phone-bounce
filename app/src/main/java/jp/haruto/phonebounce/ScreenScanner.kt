package jp.haruto.phonebounce

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import jp.haruto.phonebounce.core.Box
import jp.haruto.phonebounce.core.ElementKinds
import jp.haruto.phonebounce.core.ElementScan
import jp.haruto.phonebounce.core.UiNode

/**
 * 画面の部品（ボタン・画像など）の「枠」を読む。AccessibilityNodeInfo に触れてよいのは、このファイルだけ。
 *
 * 読むのは 位置と大きさ・クラス名・見えているか・押せるか／入力できるか・子の数 だけ。
 * 文字の中身・説明文・入力内容・ID は読まない（UiNode にその項目が無い）。PrivacyGuardTest が確かめている。
 */
class ScreenScanner {
    private class Node(private val n: AccessibilityNodeInfo) : UiNode {
        private val r = Rect().also { n.getBoundsInScreen(it) }
        override val className: String get() = n.className?.toString() ?: ""
        override val left get() = r.left
        override val top get() = r.top
        override val right get() = r.right
        override val bottom get() = r.bottom
        override val visible get() = n.isVisibleToUser
        override val clickable get() = n.isClickable || n.isLongClickable
        override val editable get() = n.isEditable
        override val childCount get() = n.childCount
        override fun child(i: Int): UiNode? = n.getChild(i)?.let { Node(it) }
        @Suppress("DEPRECATION")
        override fun release() { n.recycle() }
    }

    /** アプリの窓ごとに部品の枠を集める。キーボードや自分の重ね表示は見ない。 */
    fun scan(windows: List<AccessibilityWindowInfo>, minPx: Float, kinds: ElementKinds, maxBoxes: Int = 400): List<Box> {
        val out = ArrayList<Box>()
        val r = Rect()
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            w.getBoundsInScreen(r)
            if (r.isEmpty) continue
            val root = try { w.root } catch (e: Exception) { null } ?: continue
            val node = Node(root)
            try {
                out += ElementScan.collect(node, Box(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()), minPx, kinds)
            } catch (e: Exception) {
                // 読めなかった窓は飛ばす
            } finally {
                node.release()
            }
            if (out.size >= maxBoxes) break
        }
        return if (out.size > maxBoxes) out.subList(0, maxBoxes) else out
    }
}
