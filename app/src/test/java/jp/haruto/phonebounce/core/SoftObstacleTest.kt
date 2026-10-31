package jp.haruto.phonebounce.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot

/** 画面の部品（ボタン・画像など）で反射する動き。窓とちがい、押し出し・侵入はせず、重なっていたらすり抜けて出る。 */
class SoftObstacleTest {
    private val bounds = Box(0f, 0f, 1080f, 2400f)
    private val dt = 1f / 60f
    private val p = Params(speed = 400f, logoW = 200f, logoH = 100f)
    private fun softList(vararg r: Box) = r.map { Obstacle(Obstacle.SOFT_ID, it, soft = true) }
    private fun inset(b: Box) = Box(b.l + 0.01f, b.t + 0.01f, b.r - 0.01f, b.b - 0.01f)

    @Test fun aButtonReflectsTheLogoFromEverySide() {
        val btn = Box(400f, 1000f, 700f, 1200f)
        for (seed in 1L..20L) {
            val s = Sim(Random(seed)); s.reset(bounds, p)
            s.x = 100f; s.y = 100f; s.vx = 300f; s.vy = 250f
            repeat(20_000) { i ->
                val list = if (i % 3 == 0) softList(btn) else softList(btn).also { }   // 一覧が新しくなる（本物の 20 回/秒の更新に近い）
                s.update(dt, bounds, list, p)
                assertFalse("部品にめり込んだ（${s.x},${s.y}）", inset(s.logo(p)).overlaps(btn))
            }
            assertTrue(s.bounces > 50)
        }
    }

    @Test fun startingInsideAButtonPassesThroughSmoothlyAndThenItIsSolid() {
        val btn = Box(400f, 1000f, 700f, 1200f)
        val s = Sim(Random(3)); s.reset(bounds, p)
        s.x = 450f; s.y = 1050f; s.vx = 300f; s.vy = 100f
        var sawGhost = false
        var exited = false
        var px = s.x; var py = s.y
        repeat(6_000) { i ->
            val list = softList(btn)                                    // 毎コマ新しい一覧（毎回、重なりを見直す）
            s.update(dt, bounds, list, p)
            val step = hypot(s.x - px, s.y - py)
            assertTrue("ワープした: $step", step <= p.speed * dt * 1.02f + 0.01f || s.bounces > 0 && step <= p.speed * dt * 1.5f)
            px = s.x; py = s.y
            val overlap = inset(s.logo(p)).overlaps(btn)
            if (overlap) {
                assertFalse("出たあとで、また中に入った", exited)
                assertTrue("重なっているのに、すり抜け表示でない", s.ghosting)
                sawGhost = true
            } else if (sawGhost) exited = true
        }
        assertTrue(sawGhost)
        assertTrue("外へ出られなかった", exited)
    }

    @Test fun windowsStillBeatSoftObstaclesAndTheLogoNeverGetsTrapped() {
        val s = Sim(Random(5)); s.reset(bounds, p)
        s.x = 100f; s.y = 100f; s.vx = 300f; s.vy = 250f
        val keyboard = Obstacle(1, Box(0f, 1500f, 1080f, 2400f))
        repeat(10_000) { i ->
            val list = softList(Box(300f, 700f, 500f, 900f), Box(600f, 400f, 900f, 550f)) + keyboard
            s.update(dt, bounds, list, p)
            assertEquals(Sim.FREE, s.mode)
            assertTrue(s.y + p.logoH <= 1500f + 0.01f)
        }
    }

    @Test fun aDenseScreenOfManyElementsKeepsTheLogoMovingAtConstantSpeed() {
        val rnd = Random(11)
        val rects = (0 until 300).map {
            val x = rnd.nextFloat() * 950f; val y = rnd.nextFloat() * 2300f
            Box(x, y, x + 60f + rnd.nextFloat() * 160f, y + 40f + rnd.nextFloat() * 120f)
        }
        val s = Sim(Random(7)); s.reset(bounds, p)
        repeat(20_000) { i ->
            val list = if (i % 3 == 0) softList(*rects.toTypedArray()) else softList(*rects.toTypedArray())
            s.update(dt, bounds, list, p)
            assertTrue(s.x.isFinite() && s.y.isFinite())
            assertTrue(bounds.contains(inset(s.logo(p))))
            assertEquals(p.speed, hypot(s.vx, s.vy), 0.5f)
        }
        assertTrue(s.bounces > 100)
        assertTrue("動いていない", abs(s.vx) + abs(s.vy) > 0f)
    }
}
