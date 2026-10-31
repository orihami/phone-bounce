package jp.haruto.phonebounce.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot

/**
 * ぶつかり方の「ストレスの無さ」を、乱数で作った画面（スクロールで動く部品・窓・いろいろな形のロゴ）で確かめる。
 *  - 画面の外へ出ない／NaN にならない
 *  - 速さがずっと一定（ぶつかっても、ゆっくりになったり急に速くなったりしない）
 *  - 固まらない（一定時間に、ちゃんと動く）
 *  - 部品に重なって「すり抜け」ているあいだの時間が、ほんの一部だけ（ちらつかない）
 *  - 1コマの動きが、速さ×時間より大きく飛ばない（瞬間移動しない）。ただし窓に押されたとき・脱出中は別
 */
class HitFeelTest {
    private val bounds = Box(0f, 40f, 1080f, 2300f)
    private val dt = 1f / 60f

    private fun screen(rnd: Random, scrollY: Float, gap: Float): List<Obstacle> {
        val list = ArrayList<Obstacle>()
        // 一覧のようにスクロールして動く、同じ大きさのボタンの列（20 回/秒で新しい一覧になる）
        val step = 180f + gap
        var y = 200f - scrollY % step
        while (y < 2300f) {
            list += Obstacle(Obstacle.SOFT_ID, Box(60f, y, 1020f, y + 180f), soft = true)
            list += Obstacle(Obstacle.SOFT_ID, Box(80f, y + 20f, 220f, y + 160f), soft = true)
            y += step
        }
        return list
    }

    private fun run(label: String, logoW: Float, logoH: Float, seed: Long, scrolling: Boolean, keyboard: Boolean, gap: Float = 200f, maxGhost: Double = 0.08) {
        val rnd = Random(seed)
        val p = Params(speed = 100f * 3f, logoW = logoW, logoH = logoH, interference = Interference.REFLECT_ONLY)
        val s = Sim(Random(seed)); s.reset(bounds, p)
        var list = screen(rnd, 0f, gap)
        var ghostFrames = 0
        var scroll = 0f
        val frames = 60 * 60
        val settle = if (scrolling) 780 else 0   // スクロールが止まって 3 秒たってから、すり抜け表示の割合を数える
        var travelled = 0f
        var px = s.x; var py = s.y
        repeat(frames) { i ->
            if (scrolling && i < 600) scroll += 250f * dt   // 最初の 10 秒だけスクロール（指で流したあと、止まる）
            if (i % 3 == 0) {   // 20 回/秒で読み直す
                list = screen(rnd, scroll, gap)
                if (keyboard) list = list + Obstacle(1, Box(0f, 1500f, 1080f, 2300f))
            }
            s.update(dt, bounds, list, p)
            val l = s.logo(p)
            assertTrue("$label: 画面の外 $l", l.l >= bounds.l - 0.5f && l.r <= bounds.r + 0.5f && l.t >= bounds.t - 0.5f && l.b <= bounds.b + 0.5f)
            assertTrue("$label: 速さが一定でない (${hypot(s.vx, s.vy)})", abs(hypot(s.vx, s.vy) - p.speed) < p.speed * 0.02f)
            val step = hypot(s.x - px, s.y - py)
            // スクロールで動く部品に押し出されることはない（反射のみ）。1コマで速さ×時間の 3 倍以上は飛ばない
            assertTrue("$label: 瞬間移動した $step (frame $i)", i == 0 || step < p.speed * dt * 3f + 2f)
            travelled += step; px = s.x; py = s.y
            if (s.ghosting && i >= settle) ghostFrames++
        }
        assertTrue("$label: ほとんど動かない（固まった）: $travelled", travelled > p.speed * 60f * 0.6f)
        assertTrue("$label: すり抜け表示の時間が長い: ${ghostFrames * 100 / (frames - settle)}%", ghostFrames <= (frames - settle) * maxGhost)
    }

    @Test fun variousLogoShapesOnAScrollingListStayCalm() {
        // 円盤つき（横長）・文字だけ（小さく、短い文字は小さい）・長い文字・画像（縦長）
        val shapes = listOf("円盤" to (230f to 111f), "文字だけ" to (150f to 70f), "短い文字" to (90f to 70f), "長い文字" to (480f to 70f), "縦長画像" to (80f to 150f))
        for ((name, wh) in shapes) for (seed in 1L..3L) {
            run("$name/静止/seed$seed", wh.first, wh.second, seed, scrolling = false, keyboard = false)
            run("$name/スクロール/seed$seed", wh.first, wh.second, seed, scrolling = true, keyboard = false)
            run("$name/スクロール+キーボード/seed$seed", wh.first, wh.second, seed, scrolling = true, keyboard = true)
            // ぎゅうぎゅうの一覧（隙間がロゴより狭い）。すり抜けが多くなるのは仕方ないが、固まらず・飛ばず・画面外へ出ない
            run("$name/ぎゅうぎゅう/seed$seed", wh.first, wh.second, seed, scrolling = true, keyboard = false, gap = 60f, maxGhost = 1.0)
        }
    }

    @Test fun anEmptyGapSmallerThanTheLogoNeverTrapsIt() {
        // ボタンの隙間（60px）はロゴ（高さ 70）より狭い。入り込まず、固まらず、跳ね続ける
        val p = Params(speed = 300f, logoW = 150f, logoH = 70f, interference = Interference.REFLECT_ONLY)
        val a = Obstacle(Obstacle.SOFT_ID, Box(100f, 400f, 1000f, 700f), soft = true)
        val b = Obstacle(Obstacle.SOFT_ID, Box(100f, 760f, 1000f, 1060f), soft = true)
        val list = listOf(a, b)
        for (seed in 1L..10L) {
            val s = Sim(Random(seed)); s.reset(bounds, p)
            var moved = 0f; var px = s.x; var py = s.y
            repeat(60 * 90) {
                s.update(dt, bounds, list, p)
                moved += hypot(s.x - px, s.y - py); px = s.x; py = s.y
                assertEquals(false, s.logo(p).overlaps(Box(a.rect.l + 1, a.rect.t + 1, a.rect.r - 1, a.rect.b - 1)) && !s.ghosting)
            }
            assertTrue("固まった: $moved", moved > 300f * 90f * 0.9f)
        }
    }
}
