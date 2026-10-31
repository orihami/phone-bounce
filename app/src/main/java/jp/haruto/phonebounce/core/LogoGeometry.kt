package jp.haruto.phonebounce.core

/**
 * 元の Desktop Bounce の内蔵ロゴの寸法（Windows 版を実際に描かせて測ったもの、2026-10-31）。
 * 斜めの「DVD」が上、その下に平たい円盤（真ん中に楕円の穴）。1色のべた塗り。
 * 全体の枠を幅 W × 高さ H として、割合で持つ。
 */
object LogoGeometry {
    /** 幅 / 高さ（実測 112 : 54） */
    const val ASPECT = 2.07f

    /** 文字の帯: 上端から H の 53.3% まで（文字の下端＝ベースライン） */
    const val TEXT_BOTTOM = 0.533f

    /** 円盤の上端: H の 64.0%（文字との隙間は約 10.7%）。下端は H */
    const val DISC_TOP = 0.640f

    /** 文字の最大幅: W の 68% */
    const val TEXT_WIDTH = 0.68f

    /** 穴の幅: W の 22.3%、高さ: 円盤の高さの 41%。円盤の真ん中 */
    const val HOLE_W = 0.223f
    const val HOLE_H_OF_DISC = 0.41f

    /** 文字の傾き（textSkewX。実測で約 12.7 度） */
    const val SKEW = -0.22f
}
