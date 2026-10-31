package jp.haruto.phonebounce

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.PowerManager
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import jp.haruto.phonebounce.core.Obstacle
import jp.haruto.phonebounce.core.Params
import jp.haruto.phonebounce.core.Box
import jp.haruto.phonebounce.core.Sim

/**
 * 常駐の本体。アクセシビリティサービスとして、システムが起動時に自動で立ち上げ、動かし続ける。
 *
 * やっていること
 *  1. 画面いっぱいの透明な重ね表示（TYPE_ACCESSIBILITY_OVERLAY、タッチ素通り）にロゴを描く
 *  2. 画面に見えているウィンドウの「位置」だけを getWindows() で読む（キーボード・分割画面・小窓）
 *  3. 位置を core.Sim に渡して、ロゴを動かす
 *
 * 画面の文字や入力内容は読まない（AccessibilityNodeInfo・rootInActiveWindow は一切呼ばない）。
 * 画面が消えている間と、一時停止中は、コマ送りを止める（電池を使わない）。
 */
class BounceService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private var view: LogoView? = null

    private val sim = Sim()
    private val params = Params()
    private var cfg: AppSettings? = null
    private var display = Box(0f, 0f, 1080f, 2400f)
    private var bounds = display
    private var obstacles: List<Obstacle> = emptyList()

    private var screenOn = true
    private var looping = false
    private var lastFrameNs = 0L
    private var accDt = 0f
    private var pollIn = 0f
    private var windowsDirty = true
    private var loadedImageVersion = -1

    private val frame = Choreographer.FrameCallback { onFrame(it) }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> { screenOn = true; syncLoop() }
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; syncLoop() }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = AppSettings.prefs(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        addOverlay()
        applySettings()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) },
            Context.RECEIVER_NOT_EXPORTED,
        )
        screenOn = getSystemService(PowerManager::class.java).isInteractive
        refreshGeometry()
        sim.reset(bounds, params)
        syncLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 窓の出入り・位置の変化の合図だけを使う。イベントの中身（文字など）は見ない。
        windowsDirty = true
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        stopLoop()
        runCatching { unregisterReceiver(screenReceiver) }
        if (this::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(this)
        removeOverlay()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        applySettings()
        syncLoop()
    }

    // ---------------- 設定 ----------------

    private fun applySettings() {
        val c = AppSettings.load(this)
        cfg = c
        val d = resources.displayMetrics.density
        params.speed = c.speedDp * d
        params.logoH = c.logoHeightDp * d
        if (c.imageVersion != loadedImageVersion) {   // 「画像」スタイルの画像を読み直す
            view?.painter?.image = CustomLogo.loadMask(this)
            loadedImageVersion = c.imageVersion
        }
        params.logoW = params.logoH * (view?.painter?.aspect(c.style) ?: jp.haruto.phonebounce.core.LogoGeometry.ASPECT)
        params.interference = c.interference
        params.colorMode = c.colorMode
        params.intrudeSeconds = c.intrudeSeconds
        windowsDirty = true
    }

    private fun wantRunning(): Boolean = screenOn && cfg?.paused == false

    /** 動かす／止めるを、いまの状態に合わせる */
    private fun syncLoop() {
        view?.visibility = if (cfg?.paused == true) android.view.View.INVISIBLE else android.view.View.VISIBLE
        if (wantRunning()) startLoop() else stopLoop()
    }

    private fun startLoop() {
        if (looping) return
        looping = true
        lastFrameNs = 0L
        accDt = 0f
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun stopLoop() {
        looping = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    // ---------------- 重ね表示 ----------------

    private fun addOverlay() {
        if (view != null) return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "PhoneBounce"
        }
        val v = LogoView(this)
        wm.addView(v, lp)
        view = v
    }

    private fun removeOverlay() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    // ---------------- 1コマ ----------------

    private fun onFrame(frameTimeNs: Long) {
        if (!looping) return
        val dt = if (lastFrameNs == 0L) 1f / 60f else (frameTimeNs - lastFrameNs) / 1_000_000_000f
        lastFrameNs = frameTimeNs
        accDt += dt
        val minStep = if (cfg?.fps60 == false) 1f / 30f - 0.004f else 0f
        if (accDt >= minStep) {
            step(accDt)
            accDt = 0f
        }
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun step(dt: Float) {
        val c = cfg ?: return
        pollIn -= dt
        if (windowsDirty || pollIn <= 0f) {
            refreshGeometry()
            refreshObstacles(c)
            windowsDirty = false
            pollIn = 0.05f   // 窓の位置は 20 回/秒で読む（キーボードのせり上がり程度なら十分追える）
        }
        sim.update(dt, bounds, obstacles, params)
        val l = sim.logo(params)
        view?.update(
            RectF(l.l, l.t, l.r, l.b), sim.color(params), sim.translucent, c.label, c.style,
            if (c.showWalls) obstacles.map { RectF(it.rect.l, it.rect.t, it.rect.r, it.rect.b) } else emptyList(),
            RectF(bounds.l, bounds.t, bounds.r, bounds.b), c.showWalls,
        )
    }

    private fun refreshGeometry() {
        val c = cfg ?: return
        try {
            val m = wm.maximumWindowMetrics.bounds
            display = Box(0f, 0f, m.width().toFloat(), m.height().toFloat())
            bounds = display
            if (c.insetBars) {
                val ins = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                bounds = Box(ins.left.toFloat(), ins.top.toFloat(), display.r - ins.right, display.b - ins.bottom)
            }
        } catch (e: Exception) {
            // 取れなかったら前の値のまま
        }
    }

    /** 画面に見えているウィンドウのうち、ロゴが避けるもの。全画面のアプリ本体は「背景」なので避けない。 */
    private fun refreshObstacles(c: AppSettings) {
        val list = ArrayList<Obstacle>()
        val ws = try { windows } catch (e: Exception) { emptyList() }
        val r = Rect()
        for (w in ws) {
            w.getBoundsInScreen(r)
            if (r.isEmpty) continue
            val area = r.width().toFloat() * r.height()
            val ok = when (w.type) {
                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> c.wallIme
                AccessibilityWindowInfo.TYPE_APPLICATION -> c.wallWindows && area < display.area * 0.9f
                else -> false   // システムバー・自分の重ね表示・そのほかは対象にしない
            }
            if (ok) list += Obstacle(w.id, Box(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
        }
        obstacles = list
    }
}
