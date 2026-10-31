package jp.haruto.phonebounce

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * プライバシーの守り役。このアプリが画面から読んでよいのは「窓と部品の位置・大きさ・種類（クラス名）・見えているか・押せるか」だけ。
 * 文字の中身・説明文・入力内容・ID を読むコードが紛れ込んだら、ここで失敗させる（CI も止まる）。
 */
class PrivacyGuardTest {
    private fun sources(): List<File> {
        val dir = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** コメントを取り除いた本文（コメントに禁止語を書くのは自由） */
    private fun code(f: File): String =
        f.readText().replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\n]*"), "")

    @Test fun onlyTheScreenScannerTouchesAccessibilityNodes() {
        for (f in sources()) {
            if (f.name == "ScreenScanner.kt") continue
            val c = code(f)
            assertTrue("${f.name} が AccessibilityNodeInfo を使っている（ScreenScanner.kt だけに閉じ込める）", !c.contains("AccessibilityNodeInfo"))
            assertTrue("${f.name} が rootInActiveWindow を使っている", !c.contains("rootInActiveWindow"))
        }
    }

    @Test fun theScannerNeverReadsTextOrDescriptionsOrIds() {
        val f = sources().first { it.name == "ScreenScanner.kt" }
        val c = code(f)
        val forbidden = listOf(
            "\\.text\\b", "getText", "contentDescription", "hintText", "tooltipText", "viewIdResourceName",
            "stateDescription", "paneTitle", "\\.error\\b", "labeledBy", "labelFor", "\\.extras\\b", "performAction",
            "textSelection", "isPassword", "inputType", "\\.collectionInfo", "\\.rangeInfo",
        )
        for (word in forbidden) assertTrue("ScreenScanner.kt が読んではいけない項目に触れている: $word", !Regex(word).containsMatchIn(c))
    }

    @Test fun theServiceNeverLooksInsideAccessibilityEvents() {
        val f = sources().first { it.name == "BounceService.kt" }
        val c = code(f)
        for (word in listOf("event\\??\\.(text|source|contentDescription|recordCount|getRecord|className|packageName)", "\\.getSource", "\\.getText")) {
            assertTrue("BounceService.kt がイベントの中身を読んでいる: $word", !Regex(word).containsMatchIn(c))
        }
    }

    @Test fun noInternetPermission() {
        val m = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }.readText()
        assertTrue("INTERNET 権限が付いている", !m.contains("android.permission.INTERNET"))
    }
}
