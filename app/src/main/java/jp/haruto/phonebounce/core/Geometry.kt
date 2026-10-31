package jp.haruto.phonebounce.core

/**
 * 画面座標（px）の矩形。Android に依存しない。
 * core/ 以下は JVM のユニットテストだけで動かせるようにしてある（実機が無くても動きを確かめるため）。
 */
data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w: Float get() = r - l
    val h: Float get() = b - t
    val cx: Float get() = (l + r) / 2f
    val cy: Float get() = (t + b) / 2f
    val area: Float get() = maxOf(0f, w) * maxOf(0f, h)

    /** 辺が触れているだけ（重なりが 0）のときは重なっていないとみなす */
    fun overlaps(o: Box): Boolean = l < o.r && o.l < r && t < o.b && o.t < b

    fun contains(o: Box): Boolean = o.l >= l && o.r <= r && o.t >= t && o.b <= b

    fun offset(dx: Float, dy: Float): Box = Box(l + dx, t + dy, r + dx, b + dy)
}

/** ロゴが避ける「ウィンドウ」。id は窓の識別子（動いても同じ）。 */
class Obstacle(val id: Int, val rect: Box)
