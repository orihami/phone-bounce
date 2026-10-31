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
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import jp.haruto.phonebounce.core.ElementKinds
import jp.haruto.phonebounce.core.Obstacle
import jp.haruto.phonebounce.core.Params
import jp.haruto.phonebounce.core.Box
import jp.haruto.phonebounce.core.Sim

/**
 * 常駐の本体。アクセシビリティサービスとして、システムが起動時に自動で立ち上げ、動かし続ける。
 *
 * やっていること
 *  1. 画面いっぱいの透明な重ね表示（TYPE_ACCESSIBILITY_OVERLAY、タッチ素通り）にロゴを描く
 *  2. 画面に見えているウィンドウの「位置」を getWindows() で読む（キーボード・分割画面・小窓）
 *  3. 設定で許していれば、画面の部品（ボタン・画像など）の「枠」も ScreenScanner で読む
 *  4. 位置を core.Sim に渡して、ロゴを動かす
 *
 * 画面の文字や入力内容は読まない（部品の読み取りは ScreenScanner だけ。位置・大きさ・種類しか取らない）。
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

    // 画面の部品の読み取り（別スレッド）。連続した変化は間引く。
    private val scanner = ScreenScanner()
    private var scanThread: HandlerThread? = null
    private var scanHandler: Handler? = null
    @Volatile private var softBoxes: List<Box> = emptyList()
    @Volatile private var scanQueued = false
    private var lastScanMs = 0L
    private var elementsOn = false

    private val scanTask = Runnable {
        scanQueued = false
        val c = cfg
        if (c == null || !c.wallElements || !wantRunning()) return@Runnable
        val ws = try { windows } catch (e: Exception) { emptyList() }
        val minPx = 24f * resources.displayMetrics.density
        softBoxes = scanner.scan(ws, minPx, ElementKinds(c.elemControls, c.elemImages, c.elemText))
        lastScanMs = android.os.SystemClock.uptimeMillis()
        windowsDirty = true
    }

    private fun requestScan() {
        val h = scanHandler ?: return
        if (scanQueued || cfg?.wallElements != true) return
        scanQueued = true
        val wait = (lastScanMs + 250 - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0L) + 60L
        h.postDelayed(scanTask, wait)
    }

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
        scanThread = HandlerThread("pb-scan").also { it.start(); scanHandler = Handler(it.looper) }
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
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && event?.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) windowsDirty = true
        if (wantRunning()) requestScan()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        stopLoop()
        scanHandler?.removeCallbacksAndMessages(null)
        scanThread?.quitSafely()
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
        syncElementEvents(c.wallElements)
        if (!c.wallElements) softBoxes = emptyList() else requestScan()
        windowsDirty = true
    }

    /** 部品を壁にする設定のときだけ、画面の変化の合図（中身は見ない）を多めに受け取る */
    private fun syncElementEvents(on: Boolean) {
        if (on == elementsOn) return
        elementsOn = on
        val info = serviceInfo ?: return
        val extra = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_SCROLLED
        val base = AccessibilityEvent.TYPE_WINDOWS_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.eventTypes = if (on) base or extra else base
        info.flags = if (on) info.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                     else info.flags and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()
        info.notificationTimeout = if (on) 100 else 0
        serviceInfo = info
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
            RectF(l.l, l.t, l.r, l.b), sim.color(params), sim.translucent || sim.ghosting, c.label, c.style,
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
        if (c.wallElements) for (b in softBoxes) list += Obstacle(Obstacle.SOFT_ID, b, soft = true)
        obstacles = list
    }
}
