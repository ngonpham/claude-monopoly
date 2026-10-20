package vn.cotyphu.ui

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import vn.cotyphu.R
import vn.cotyphu.gl.Engine
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import kotlin.math.hypot

private const val EGL_OPENGL_ES3_BIT = 0x40

/** Âm thanh: dùng lại 4 file mp3 của game gốc (monopoly/static/sound). */
class Sound(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = mapOf(
        "dice" to pool.load(context, R.raw.sfx_dice, 1),
        "move" to pool.load(context, R.raw.sfx_move, 1),
        "cash" to pool.load(context, R.raw.sfx_cash, 1),
        "build" to pool.load(context, R.raw.sfx_build, 1),
    )
    var muted = false

    fun play(name: String, volume: Float = 0.6f) {
        if (muted) return
        ids[name]?.let { pool.play(it, volume, volume, 1, 0, 1f) }
    }

    fun release() = pool.release()
}

/** Lưu ván và tuỳ chọn vào SharedPreferences. */
class SaveStore(context: Context) {
    private val prefs = context.getSharedPreferences("cotyphu", Context.MODE_PRIVATE)

    var savedGame: String?
        get() = prefs.getString("save", null)
        set(v) = prefs.edit().apply { if (v == null) remove("save") else putString("save", v) }.apply()

    var botSpeed: Float
        get() = prefs.getFloat("botSpeed", 1f)
        set(v) = prefs.edit().putFloat("botSpeed", v).apply()

    var muted: Boolean
        get() = prefs.getBoolean("muted", false)
        set(v) = prefs.edit().putBoolean("muted", v).apply()
}

/**
 * Khung vẽ OpenGL toàn màn hình. Cử chỉ: một ngón kéo để xoay, hai ngón chụm để thu phóng
 * và kéo để dịch; chạm nhẹ để chọn ô (gọi [onTap] với toạ độ pixel).
 */
@SuppressLint("ViewConstructor")
class GameSurface(context: Context, private val engine: Engine, private val onTap: (Float, Float) -> Unit) : GLSurfaceView(context) {
    private var lastX = 0f
    private var lastY = 0f
    private var lastSpan = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var multi = false

    init {
        setEGLContextClientVersion(3)
        // khử răng cưa MSAA 4x như antialias của bản web; máy không hỗ trợ thì lùi về cấu hình thường
        setEGLConfigChooser { egl, display ->
            fun choose(samples: Int): EGLConfig? {
                val attribs = intArrayOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                    EGL10.EGL_SAMPLE_BUFFERS, if (samples > 0) 1 else 0, EGL10.EGL_SAMPLES, samples, EGL10.EGL_NONE,
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val num = IntArray(1)
                return if (egl.eglChooseConfig(display, attribs, configs, 1, num) && num[0] > 0) configs[0] else null
            }
            choose(4) ?: choose(0) ?: throw IllegalStateException("Thiết bị không hỗ trợ OpenGL ES 3.0")
        }
        preserveEGLContextOnPause = true
        setRenderer(engine)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        engine.post { engine.camera.viewportHeight = h.toFloat() }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y; downX = e.x; downY = e.y; downTime = e.eventTime; multi = false
                engine.post { engine.camera.userInteracting = true }
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount >= 2) {
                multi = true
                lastSpan = span(e); lastMidX = midX(e); lastMidY = midY(e)
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    val s = span(e); val mx = midX(e); val my = midY(e)
                    if (lastSpan > 0 && s > 0) { val scale = lastSpan / s; engine.post { engine.camera.zoom(scale) } }
                    val dx = mx - lastMidX; val dy = my - lastMidY
                    engine.post { engine.camera.pan(dx, dy) }
                    lastSpan = s; lastMidX = mx; lastMidY = my
                } else if (!multi) {
                    val dx = e.x - lastX; val dy = e.y - lastY
                    engine.post { engine.camera.rotate(dx, dy) }
                    lastX = e.x; lastY = e.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> if (e.pointerCount == 2) {
                // còn một ngón: tiếp tục xoay từ vị trí ngón còn lại
                val keep = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(keep); lastY = e.getY(keep)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                engine.post { engine.camera.userInteracting = false }
                val tap = !multi && e.actionMasked == MotionEvent.ACTION_UP &&
                    hypot(e.x - downX, e.y - downY) < 12 * resources.displayMetrics.density && e.eventTime - downTime < 400
                if (tap) onTap(e.x, e.y)
            }
        }
        return true
    }

    private fun span(e: MotionEvent) = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
    private fun midX(e: MotionEvent) = (e.getX(0) + e.getX(1)) / 2
    private fun midY(e: MotionEvent) = (e.getY(0) + e.getY(1)) / 2
}
