package jp.haruto.phonebounce.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot

class SimTest {
    private val bounds = Box(0f, 0f, 1080f, 2400f)
    private val dt = 1f / 60f
    private fun params(speed: Float = 300f) = Params(speed = speed, logoW = 200f, logoH = 100f)
    private fun sim(seed: Long = 1) = Sim(Random(seed))
    private fun place(s: Sim, x: Float, y: Float, vx: Float, vy: Float) { s.x = x; s.y = y; s.vx = vx; s.vy = vy }

    @Test fun staysInsideAndKeepsSpeed() {
        val p = params()
        for (seed in 1L..10L) {
            val s = sim(seed); s.reset(bounds, p)
            repeat(20_000) {
                s.update(dt, bounds, emptyList(), p)
                assertTrue(bounds.contains(s.logo(p).let { Box(it.l + 0.01f, it.t + 0.01f, it.r - 0.01f, it.b - 0.01f) }))
                assertEquals(p.speed, hypot(s.vx, s.vy), 0.5f)
            }
            assertTrue(s.bounces > 20)
        }
    }

    @Test fun keyboardActsAsAWall() {
        val p = params(500f)
        val keyboard = listOf(Obstacle(1, Box(0f, 1500f, 1080f, 2400f)))
        for (seed in 1L..20L) {
            val s = sim(seed); s.reset(bounds, p)
            place(s, 100f, 100f, 300f, 400f)
            repeat(10_000) {
                s.update(dt, bounds, keyboard, p)
                assertEquals(Sim.FREE, s.mode)
                assertTrue("キーボードにめり込んだ: y=${s.y}", s.y + p.logoH <= 1500f + 0.01f)
            }
        }
    }

    @Test fun reflectsOffAFloatingWindowFromAnySide() {
        val p = params(400f)
        val win = listOf(Obstacle(7, Box(300f, 800f, 800f, 1400f)))
        for (seed in 1L..20L) {
            val s = sim(seed); s.reset(bounds, p)
            place(s, 20f, 20f, 300f, 250f)
            var hit = false
            repeat(20_000) {
                s.update(dt, bounds, win, p)
                assertFalse("窓の中に入った", s.logo(p).let { Box(it.l + 0.01f, it.t + 0.01f, it.r - 0.01f, it.b - 0.01f) }.overlaps(win[0].rect))
                if (s.bounces > 50) hit = true
            }
            assertTrue(hit)
        }
    }

    @Test fun pushesWhenAWindowMovesIntoTheLogo_thenIntrudesWhenSqueezedAgainstTheWall() {
        val p = params(50f)
        val s = sim(); s.reset(bounds, p)
        place(s, 500f, 1000f, 0f, -50f)             // 上へゆっくり
        var left = 900f
        var pushedFrom = Float.NaN
        // 窓は右から左へ 800px/秒（侵入のしきい値 2500 より遅い）。画面全体の高さをふさぐ壁のような窓。
        while (left > 600f) {
            left -= 800f * dt
            s.update(dt, bounds, listOf(Obstacle(3, Box(left, 0f, left + 300f, 2400f))), p)
            assertEquals(Sim.FREE, s.mode)
            assertTrue("窓にめり込んだ", s.x + p.logoW <= left + 0.01f)
            if (pushedFrom.isNaN() && s.x < 500f - 1f) pushedFrom = left
        }
        assertFalse("押されていない", pushedFrom.isNaN())
        assertEquals("押し出しで速さは変わらない", 50f, hypot(s.vx, s.vy), 0.5f)
        // さらに左へ。ロゴを画面の左端と窓で挟む → 侵入
        while (left > 100f && s.mode == Sim.FREE) {
            left -= 800f * dt
            s.update(dt, bounds, listOf(Obstacle(3, Box(left, 0f, left + 300f, 2400f))), p)
        }
        assertEquals(Sim.INTRUDED, s.mode)
    }

    @Test fun aWindowOpeningOnTopOfTheLogoSwallowsItAndCarriesIt_thenItEscapes() {
        val p = params(200f)
        val s = sim(); s.reset(bounds, p)
        place(s, 400f, 1000f, 120f, 160f)
        s.update(dt, bounds, emptyList(), p)
        var win = Box(300f, 900f, 900f, 1500f)
        s.update(dt, bounds, listOf(Obstacle(9, win)), p)   // 新しく開いた窓がロゴの上に
        assertEquals(Sim.INTRUDED, s.mode)
        assertTrue(s.translucent)
        // 窓を右へ 100px 動かすと、ロゴも一緒に運ばれる
        val x0 = s.x
        win = win.offset(100f, 0f)
        s.update(0f, bounds, listOf(Obstacle(9, win)), p)
        assertEquals(x0 + 100f, s.x, 0.01f)
        // 中にいる間は窓の内側で跳ね返る
        repeat(60 * 4) {
            s.update(dt, bounds, listOf(Obstacle(9, win)), p)
            assertTrue(win.contains(s.logo(p).let { Box(it.l + 0.01f, it.t + 0.01f, it.r - 0.01f, it.b - 0.01f) }))
        }
        // 時間切れ（既定 6 秒）で外へ出て、通常に戻る
        var guard = 0
        while (s.mode != Sim.FREE && guard++ < 60 * 30) s.update(dt, bounds, listOf(Obstacle(9, win)), p)
        assertEquals(Sim.FREE, s.mode)
        assertFalse(s.logo(p).overlaps(win))
    }

    @Test fun aFastEdgeIntrudesInsteadOfPushing() {
        val p = params(100f)
        val s = sim(); s.reset(bounds, p)
        place(s, 500f, 1000f, 60f, 80f)
        s.update(dt, bounds, listOf(Obstacle(4, Box(900f, 0f, 1200f, 2400f))), p)
        assertEquals(Sim.FREE, s.mode)
        // 次の1コマで 400px 飛ぶ（= 24000px/秒）
        s.update(dt, bounds, listOf(Obstacle(4, Box(500f, 0f, 800f, 2400f))), p)
        assertEquals(Sim.INTRUDED, s.mode)
    }

    @Test fun reflectOnlyNeverPushesOrIntrudes() {
        val p = params(100f).apply { interference = Interference.REFLECT_ONLY }
        val s = sim(); s.reset(bounds, p)
        place(s, 500f, 1000f, 0f, -100f)
        var left = 900f
        while (left > 400f) {
            left -= 800f * dt
            s.update(dt, bounds, listOf(Obstacle(3, Box(left, 0f, left + 300f, 2400f))), p)
            assertEquals(Sim.FREE, s.mode)
            assertFalse(s.logo(p).let { Box(it.l + 0.01f, it.t + 0.01f, it.r - 0.01f, it.b - 0.01f) }.overlaps(Box(left, 0f, left + 300f, 2400f)))
        }
    }

    @Test fun windowClosingReleasesTheLogoWhereItIs() {
        val p = params()
        val s = sim(); s.reset(bounds, p)
        place(s, 400f, 1000f, 100f, 100f)
        s.update(dt, bounds, listOf(Obstacle(9, Box(300f, 900f, 900f, 1500f))), p)
        assertEquals(Sim.INTRUDED, s.mode)
        s.update(dt, bounds, emptyList(), p)
        assertEquals(Sim.FREE, s.mode)
    }

    @Test fun theLogoLeavesThroughTheNearestFrame() {
        val p = params(200f).apply { intrudeSeconds = 0.1f }
        val s = sim(); s.reset(bounds, p)
        val win = Box(100f, 900f, 1000f, 1500f)
        place(s, 780f, 1000f, 100f, 100f)                    // 窓の右寄り
        s.update(dt, bounds, listOf(Obstacle(9, win)), p)
        assertEquals(Sim.INTRUDED, s.mode)
        repeat(10) { s.update(dt, bounds, listOf(Obstacle(9, win)), p) }
        assertEquals(Sim.ESCAPING, s.mode)
        assertTrue("右の辺（近いほう）から出るはず vx=${s.vx}", s.vx > 0f)
    }

    @Test fun cornerHitsAreCounted() {
        val p = params(300f)
        val s = sim(); s.reset(bounds, p)
        place(s, bounds.r - p.logoW - 1f, bounds.b - p.logoH - 1f, 300f, 300f)
        s.update(dt, bounds, emptyList(), p)
        assertEquals(1, s.corners)
        assertEquals(1, s.bounces)
    }

    @Test fun colorChangesOnEveryBounceAndRainbowFlowsContinuously() {
        val p = params(300f)
        val s = sim(); s.reset(bounds, p)
        place(s, bounds.r - p.logoW - 1f, 500f, 300f, 0f)
        val c0 = s.colorIdx
        s.update(dt, bounds, emptyList(), p)
        assertTrue(s.colorIdx != c0)
        p.colorMode = ColorMode.RAINBOW
        val a = s.color(p)
        repeat(30) { s.update(dt, bounds, emptyList(), p) }
        assertTrue(s.color(p) != a)
    }

    @Test fun logoIsPulledBackWhenTheScreenShrinks() {
        val p = params()
        val s = sim(); s.reset(bounds, p)
        place(s, 900f, 2200f, 100f, 100f)
        val small = Box(0f, 0f, 600f, 1000f)               // 画面の向きが変わった
        s.update(dt, small, emptyList(), p)
        assertTrue(small.contains(s.logo(p)))
    }

    @Test fun anObstacleAlreadyUnderTheLogoOnTheFirstFrameIsHandledWithoutJitter() {
        val p = params(300f)
        val s = sim(); s.reset(bounds, p)
        place(s, 400f, 1000f, 100f, 100f)
        val k = listOf(Obstacle(1, Box(0f, 900f, 1080f, 2400f)))   // いきなりキーボードが出た
        repeat(120) { s.update(dt, bounds, k, p) }
        assertTrue(abs(s.vx) + abs(s.vy) > 0f)
    }
}
