package jp.haruto.phonebounce

import android.content.Context
import android.content.SharedPreferences
import jp.haruto.phonebounce.core.ColorMode
import jp.haruto.phonebounce.core.Interference

/** 設定。画面（MainActivity）が書き、常駐の本体（BounceService）が変更を拾って、すぐ反映する。 */
class AppSettings(
    val speedDp: Float,          // ロゴの速さ（dp/秒）
    val logoHeightDp: Float,     // ロゴの高さ（dp）。幅は高さの2倍
    val interference: Interference,
    val colorMode: ColorMode,
    val intrudeSeconds: Float,   // 窓の中にいる時間（秒）
    val wallIme: Boolean,        // キーボードを壁にする
    val wallWindows: Boolean,    // 分割画面・小窓を壁にする
    val wallElements: Boolean,   // 画面の部品（ボタン・画像など）を壁にする
    val elemControls: Boolean,   // 部品のうち、ボタン・入力欄・押せるもの
    val elemImages: Boolean,     // 部品のうち、画像・動画
    val elemText: Boolean,       // 部品のうち、文字の枠（位置だけ。中身は読まない）
    val insetBars: Boolean,      // ステータスバー・ナビゲーションバーの内側で跳ねる
    val showWalls: Boolean,      // 壁を表示（確認用）
    val fps60: Boolean,          // false なら 30fps（省電力）
    val paused: Boolean,
    val label: String,
    val style: LogoStyle,
    val decor: Decor,            // 文字の下の飾り（円盤・なし・下線・リング・板）
    val imageVersion: Int,       // 「画像」スタイルの画像を差し替えるたびに増える（常駐の本体が読み直す合図）
) {
    companion object {
        const val FILE = "phonebounce"
        const val SPEED = "speedDp"
        const val SIZE = "logoHeightDp"
        const val INTERFERENCE = "interference"
        const val COLOR = "colorMode"
        const val INTRUDE_SEC = "intrudeSeconds"
        const val WALL_IME = "wallIme"
        const val WALL_WINDOWS = "wallWindows"
        const val WALL_ELEMENTS = "wallElements"
        const val ELEM_CONTROLS = "elemControls"
        const val ELEM_IMAGES = "elemImages"
        const val ELEM_TEXT = "elemText"
        const val INSET_BARS = "insetBars"
        const val SHOW_WALLS = "showWalls"
        const val FPS60 = "fps60"
        const val PAUSED = "paused"
        const val LABEL = "label"
        const val STYLE = "style"
        const val DECOR = "decor"
        const val IMG_VER = "imageVersion"

        fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        fun load(ctx: Context): AppSettings {
            val p = prefs(ctx)
            return AppSettings(
                speedDp = p.getFloat(SPEED, 100f),
                logoHeightDp = p.getFloat(SIZE, 48f),
                interference = enumOf(p.getString(INTERFERENCE, null), Interference.PUSH_INTRUDE),
                colorMode = enumOf(p.getString(COLOR, null), ColorMode.PER_BOUNCE),
                intrudeSeconds = p.getFloat(INTRUDE_SEC, 6f),
                wallIme = p.getBoolean(WALL_IME, true),
                wallWindows = p.getBoolean(WALL_WINDOWS, true),
                wallElements = p.getBoolean(WALL_ELEMENTS, true),
                elemControls = p.getBoolean(ELEM_CONTROLS, true),
                elemImages = p.getBoolean(ELEM_IMAGES, true),
                elemText = p.getBoolean(ELEM_TEXT, false),
                insetBars = p.getBoolean(INSET_BARS, true),
                showWalls = p.getBoolean(SHOW_WALLS, false),
                fps60 = p.getBoolean(FPS60, true),
                paused = p.getBoolean(PAUSED, false),
                label = (p.getString(LABEL, null) ?: "DVD").ifBlank { "DVD" }.take(12),
                style = enumOf(p.getString(STYLE, null), LogoStyle.CLASSIC),
                decor = enumOf(p.getString(DECOR, null), Decor.DISC),
                imageVersion = p.getInt(IMG_VER, 0),
            )
        }

        fun reset(ctx: Context) {
            val paused = prefs(ctx).getBoolean(PAUSED, false)
            prefs(ctx).edit().clear().putBoolean(PAUSED, paused).apply()
        }

        private inline fun <reified T : Enum<T>> enumOf(s: String?, default: T): T =
            try { if (s == null) default else enumValueOf<T>(s) } catch (e: IllegalArgumentException) { default }
    }
}
