package jp.haruto.phonebounce.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 窓がロゴにぶつかったときの動き */
enum class Interference {
    /** 反射だけ。窓が動いてもロゴは押されない（重なったら、いちばん近い外へ出す） */
    REFLECT_ONLY,

    /** 反射＋押し出し */
    PUSH,

    /** 反射＋押し出し＋侵入（ロゴが窓の中に入り込む） */
    PUSH_INTRUDE,
}

enum class ColorMode { PER_BOUNCE, RAINBOW, RANDOM }

class Params(
    var speed: Float = 300f,          // px/秒（一定の速さ）
    var logoW: Float = 200f,
    var logoH: Float = 100f,
    var interference: Interference = Interference.PUSH_INTRUDE,
    var colorMode: ColorMode = ColorMode.PER_BOUNCE,
    var intrudeSeconds: Float = 6f,   // 窓の中にいる時間
    var intrudeEdgeSpeed: Float = 2500f, // 窓の縁がこれより速くぶつかると、押さずに侵入（px/秒）
)

/**
 * ロゴの動き。Desktop Bounce（Windows 版）の動きを、スマホで使える形にしたもの。
 *
 *  - 通常（FREE）: 一定の速さで進み、画面の端と窓の外枠で鏡のように反射する。反射のたびに色が変わる。
 *  - 押し出し: 窓が動いてロゴにぶつかると、窓の動いた向きにロゴが押される（速さは変わらない）。
 *  - 侵入（INTRUDED）: 窓の縁が速い／押した先が壁で挟まれる／ロゴの上に窓が開く、のどれかで、ロゴが窓の中に入る。
 *    窓の中では窓と一緒に運ばれ、窓の内側の枠で跳ね返る（画面では半透明に描く）。
 *  - 脱出（ESCAPING）: 一定時間たつと、その窓の枠だけをすり抜けて外へ出る。
 */
class Sim(private val rnd: java.util.Random = java.util.Random()) {
    companion object {
        const val FREE = 0
        const val INTRUDED = 1
        const val ESCAPING = 2

        val PALETTE = intArrayOf(
            0xFFFF2D95.toInt(), 0xFF25E8FF.toInt(), 0xFFFFD400.toInt(), 0xFF3DFF7A.toInt(),
            0xFFFF7A1A.toInt(), 0xFF7A6BFF.toInt(), 0xFFFF3B30.toInt(), 0xFFF5F5F0.toInt(),
        )
    }

    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var mode = FREE
        private set
    var modeId = -1
        private set
    var timer = 0f
        private set
    var bounces = 0
        private set
    var corners = 0
        private set
    var colorIdx = 0
        private set
    var hue = 0f
        private set

    private var prev: Map<Int, Box> = emptyMap()
    private val sinceChange = HashMap<Int, Float>()

    val translucent: Boolean get() = mode == INTRUDED

    /** 画面の部品に重なって、すり抜けている最中か（描くときに、少しうすくする） */
    var ghosting = false
        private set

    private var lastList: List<Obstacle>? = null
    private var ghosts: Set<Obstacle> = emptySet()

    fun logo(p: Params): Box = Box(x, y, x + p.logoW, y + p.logoH)

    /** ロゴの色（ARGB） */
    fun color(p: Params): Int = if (p.colorMode == ColorMode.RAINBOW) hsv(hue, 0.85f, 1f) else PALETTE[colorIdx]

    fun reset(bounds: Box, p: Params) {
        val a = Math.toRadians(25.0 + rnd.nextDouble() * 40.0)
        vx = (cos(a) * p.speed).toFloat() * (if (rnd.nextBoolean()) 1 else -1)
        vy = (sin(a) * p.speed).toFloat() * (if (rnd.nextBoolean()) 1 else -1)
        x = bounds.l + rnd.nextFloat() * max(1f, bounds.w - p.logoW)
        y = bounds.t + rnd.nextFloat() * max(1f, bounds.h - p.logoH)
        mode = FREE
        modeId = -1
        timer = 0f
        prev = emptyMap()
        sinceChange.clear()
        lastList = null
        ghosts = emptySet()
        ghosting = false
        colorIdx = rnd.nextInt(PALETTE.size)
    }

    /** 1コマ進める。bounds = ロゴが動ける範囲、obs = 上から順の窓（避ける対象）。 */
    fun update(dt0: Float, bounds: Box, obs: List<Obstacle>, p: Params) {
        val dt = dt0.coerceIn(0f, 0.05f)
        normalizeSpeed(p)
        val cur = HashMap<Int, Box>()
        for (o in obs) if (!o.soft) cur[o.id] = o.rect
        for (id in cur.keys) sinceChange[id] = (sinceChange[id] ?: 0f) + dt
        sinceChange.keys.retainAll(cur.keys)

        if (mode != INTRUDED) clampInto(bounds, p)   // 回転などで範囲が変わったら、枠の中へ戻す
        // 窓や部品の一覧が新しくなったとき、いまロゴに重なっている部品は「すり抜け」にする（出るまで当たらない）
        if (obs !== lastList) {
            lastList = obs
            val me = logo(p)
            val g = HashSet<Obstacle>()
            for (o in obs) if (o.soft && me.overlaps(o.rect)) g.add(o)
            ghosts = g
        }
        for (o in obs) if (!o.soft) react(o, prev[o.id], bounds, obs, p)
        if (mode != FREE && cur[modeId] == null) mode = FREE   // 入っていた窓が閉じた: その場に出る

        if (mode == INTRUDED) {
            val r = cur.getValue(modeId)
            moveInside(r, dt, p)
            timer -= dt
            if (timer <= 0f) startEscape(r, bounds, p)
        } else {
            moveFree(dt, bounds, obs, p)
        }
        if (mode == ESCAPING) {
            val r = cur[modeId]
            if (r == null || !logo(p).overlaps(r)) mode = FREE
        }
        if (p.colorMode == ColorMode.RAINBOW) hue = (hue + 60f * dt) % 360f
        ghosting = mode == FREE && ghosts.isNotEmpty() && ghosts.any { logo(p).overlaps(it.rect) }
        prev = cur
    }

    // ---------------- 窓の変化への反応 ----------------

    private fun react(o: Obstacle, old: Box?, bounds: Box, obs: List<Obstacle>, p: Params) {
        val r = o.rect
        val changed = old == null || old != r
        val elapsed = max(sinceChange[o.id] ?: 0f, 1f / 120f)
        if (changed) sinceChange[o.id] = 0f

        if (mode == INTRUDED && modeId == o.id) {          // 運ばれる
            if (old != null && changed) {
                x += r.l - old.l
                y += r.t - old.t
            }
            return
        }
        if (mode == ESCAPING && modeId == o.id) return      // すり抜け中はこの窓を無視
        if (!logo(p).overlaps(r)) return

        val edgeSpeed = if (old == null) Float.MAX_VALUE
        else maxOf(abs(r.l - old.l), abs(r.r - old.r), abs(r.t - old.t), abs(r.b - old.b)) / elapsed
        val fast = changed && edgeSpeed > p.intrudeEdgeSpeed
        if (p.interference == Interference.PUSH_INTRUDE && (old == null || fast)) {
            intrude(o, p)
        } else if (!pushOut(o, old, bounds, obs, p)) {
            intrude(o, p)   // 押し出せない（挟まれた・逃げ場が無い）。ロゴが詰まらないよう、窓の中へ
        }
    }

    /** ロゴを窓の外へ出す。成功したら true。押す向きは窓が動いた向き（動いていなければいちばん近い辺）。 */
    private fun pushOut(o: Obstacle, old: Box?, bounds: Box, obs: List<Obstacle>, p: Params): Boolean {
        val r = o.rect
        val me = logo(p)
        val left = r.l - me.r
        val right = r.r - me.l
        val up = r.t - me.b
        val down = r.b - me.t
        fun valid(dx: Float, dy: Float): Boolean {
            val moved = me.offset(dx, dy)
            return bounds.contains(moved) && obs.none { !it.soft && it.id != o.id && it.rect.overlaps(moved) }
        }
        fun apply(dx: Float, dy: Float) { x += dx; y += dy }

        if (p.interference != Interference.REFLECT_ONLY && old != null) {
            val mdx = r.cx - old.cx
            val mdy = r.cy - old.cy
            if (abs(mdx) > 0.01f || abs(mdy) > 0.01f) {
                val (dx, dy) = if (abs(mdx) >= abs(mdy)) (if (mdx > 0) right else left) to 0f else 0f to (if (mdy > 0) down else up)
                if (valid(dx, dy)) { apply(dx, dy); return true }
                // 押した先が壁・ほかの窓で挟まれる
                if (p.interference == Interference.PUSH_INTRUDE) return false
            }
        }
        val nearest = listOf(left to 0f, right to 0f, 0f to up, 0f to down).sortedBy { abs(it.first) + abs(it.second) }
        for ((dx, dy) in nearest) if (valid(dx, dy)) { apply(dx, dy); return true }
        return false
    }

    private fun intrude(o: Obstacle, p: Params) {
        mode = INTRUDED
        modeId = o.id
        timer = p.intrudeSeconds
    }

    private fun startEscape(r: Box, bounds: Box, p: Params) {
        val w = p.logoW
        val h = p.logoH
        // 出口の候補: 左・右・上・下。出たあとの位置が範囲に収まる、いちばん近い辺
        class Exit(val tx: Float, val ty: Float, val nx: Int, val ny: Int)
        val exits = listOf(
            Exit(r.l - w, y, -1, 0), Exit(r.r, y, 1, 0),
            Exit(x, r.t - h, 0, -1), Exit(x, r.b, 0, 1),
        )
        fun fits(e: Exit) = bounds.contains(Box(e.tx, e.ty, e.tx + w, e.ty + h))
        fun dist(e: Exit) = abs(e.tx - x) + abs(e.ty - y)
        val pick = exits.filter(::fits).minByOrNull(::dist) ?: exits.minByOrNull(::dist)!!
        val along = 0.9f
        val across = 0.44f * (if (rnd.nextBoolean()) 1 else -1)
        val (ux, uy) = if (pick.nx != 0) (pick.nx * along) to across else across to (pick.ny * along)
        val m = hypot(ux, uy)
        vx = ux / m * p.speed
        vy = uy / m * p.speed
        mode = ESCAPING
        timer = 0f
    }

    // ---------------- 動き ----------------

    private fun moveFree(dt: Float, bounds: Box, obs: List<Obstacle>, p: Params) {
        val w = p.logoW
        val h = p.logoH
        fun blocked(o: Obstacle) = !(mode == ESCAPING && o.id == modeId) && !(o.soft && ghosts.contains(o))

        x += vx * dt
        var hx = false
        if (x < bounds.l) { x = bounds.l; vx = abs(vx); hx = true }
        else if (x + w > bounds.r) { x = bounds.r - w; vx = -abs(vx); hx = true }
        for (o in obs) {
            if (!blocked(o) || !logo(p).overlaps(o.rect)) continue
            if (vx > 0f) { x = o.rect.l - w; vx = -abs(vx) } else { x = o.rect.r; vx = abs(vx) }
            hx = true
        }

        y += vy * dt
        var hy = false
        if (y < bounds.t) { y = bounds.t; vy = abs(vy); hy = true }
        else if (y + h > bounds.b) { y = bounds.b - h; vy = -abs(vy); hy = true }
        for (o in obs) {
            if (!blocked(o) || !logo(p).overlaps(o.rect)) continue
            if (vy > 0f) { y = o.rect.t - h; vy = -abs(vy) } else { y = o.rect.b; vy = abs(vy) }
            hy = true
        }
        if (hx || hy) onBounce(hx, hy)
    }

    private fun moveInside(r: Box, dt: Float, p: Params) {
        val w = p.logoW
        val h = p.logoH
        x += vx * dt
        y += vy * dt
        var hx = false
        var hy = false
        if (r.w <= w) x = r.cx - w / 2f
        else if (x < r.l) { x = r.l; vx = abs(vx); hx = true }
        else if (x + w > r.r) { x = r.r - w; vx = -abs(vx); hx = true }
        if (r.h <= h) y = r.cy - h / 2f
        else if (y < r.t) { y = r.t; vy = abs(vy); hy = true }
        else if (y + h > r.b) { y = r.b - h; vy = -abs(vy); hy = true }
        if (hx || hy) onBounce(hx, hy)
    }

    private fun clampInto(b: Box, p: Params) {
        x = min(max(x, b.l), max(b.l, b.r - p.logoW))
        y = min(max(y, b.t), max(b.t, b.b - p.logoH))
    }

    private fun onBounce(hx: Boolean, hy: Boolean) {
        bounces++
        if (hx && hy) corners++
        when (lastColorMode) {
            ColorMode.PER_BOUNCE -> colorIdx = (colorIdx + 1) % PALETTE.size
            ColorMode.RANDOM -> colorIdx = (colorIdx + 1 + rnd.nextInt(PALETTE.size - 1)) % PALETTE.size
            ColorMode.RAINBOW -> Unit
        }
    }

    private var lastColorMode = ColorMode.PER_BOUNCE

    private fun normalizeSpeed(p: Params) {
        lastColorMode = p.colorMode
        val m = hypot(vx, vy)
        if (m < 1e-3f) {
            vx = p.speed * 0.8f
            vy = p.speed * 0.6f
        } else if (abs(m - p.speed) > 0.01f) {
            vx = vx / m * p.speed
            vy = vy / m * p.speed
        }
    }

    private fun hsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val hp = (h % 360f) / 60f
        val xx = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, xx, 0f)
            hp < 2f -> Triple(xx, c, 0f)
            hp < 3f -> Triple(0f, c, xx)
            hp < 4f -> Triple(0f, xx, c)
            hp < 5f -> Triple(xx, 0f, c)
            else -> Triple(c, 0f, xx)
        }
        val m = v - c
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}
