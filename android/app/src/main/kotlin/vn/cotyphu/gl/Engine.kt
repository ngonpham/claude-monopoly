package vn.cotyphu.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/** Điểm trên màn hình (pixel) sau khi chiếu từ toạ độ thế giới. */
data class ScreenPoint(val x: Float, val y: Float, val visible: Boolean)

/**
 * Bộ render OpenGL ES 3.0 + vòng lặp hoạt cảnh. Cây cảnh chỉ được sửa trên luồng OpenGL:
 * code ở luồng khác dùng [post], [run] hoặc [tween] (suspend, chờ hoạt cảnh chạy xong).
 */
class Engine : GLSurfaceView.Renderer {
    val scene = Node()
    val camera = OrbitCamera()

    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    private val animators = ArrayList<Animator>()
    private val tickers = ArrayList<(Float, Float) -> Unit>()

    // Ánh sáng giống scene.js của bản web.
    private val sunPos = Vec3(22f, 34f, 14f)
    private val sunColor = scaled(0xFFF1D6, 2.6f)
    private val fillDir = Vec3(-20f, 15f, -18f).normalize()
    private val fillColor = scaled(0xBFD8FF, 0.5f)
    private val hemiSky = scaled(0xDCEFFF, 1.25f)
    private val hemiGround = scaled(0x8A6A45, 1.25f)
    private val fogColor = floatArrayOf(0xCF / 255f, 0xE3 / 255f, 0xEE / 255f)
    private val fogRange = floatArrayOf(70f, 190f)
    private val exposure = 1.05f

    private var generation = 0
    private var mainProgram = 0
    private var depthProgram = 0
    private var skyProgram = 0
    private var skyVao = 0
    private var shadowFbo = 0
    private var shadowTex = 0
    private val shadowSize = 2048
    private var anisotropy = 1f
    private val u = HashMap<String, Int>()
    private val du = HashMap<String, Int>()

    private var width = 1
    private var height = 1
    private var startNs = 0L
    private var lastNs = 0L
    var time = 0f
        private set

    private val lightVP = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val snapshot = FloatArray(16)
    private val invSnapshot = FloatArray(16)
    @Volatile private var snapW = 1
    @Volatile private var snapH = 1

    private val modelNormal = FloatArray(16)
    private val tmp16 = FloatArray(16)
    private val tmp3 = FloatArray(3)
    private val opaque = ArrayList<Mesh>()
    private val transparent = ArrayList<Mesh>()
    private val ticking = ArrayList<Node>()

    /** Gọi trên luồng OpenGL ngay khi khung hình đầu tiên sẵn sàng. */
    var onReady: (() -> Unit)? = null

    private class Animator(val durationMs: Float, val update: (Float) -> Unit, val done: () -> Unit) {
        var start = -1f
    }

    private fun scaled(rgb: Int, k: Float) = srgbToLinear(rgb).also { for (i in 0..2) it[i] *= k }

    // ---------- API dùng từ luồng khác ----------

    fun post(block: () -> Unit) { commands += block }

    /** Chạy [block] trên luồng OpenGL và trả về kết quả. */
    suspend fun <T> run(block: () -> T): T = suspendCancellableCoroutine { cont ->
        post { cont.resume(block()) }
    }

    /** Hoạt cảnh [durationMs] mili giây; [update] nhận k ∈ [0, 1] mỗi khung hình trên luồng OpenGL. */
    suspend fun tween(durationMs: Long, update: (Float) -> Unit) = suspendCancellableCoroutine { cont ->
        post { animators += Animator(durationMs.toFloat(), update) { if (cont.isActive) cont.resume(Unit) } }
    }

    /** Hoạt cảnh chạy nền, không chờ (an toàn gọi từ mọi luồng). */
    fun animate(durationMs: Long, update: (Float) -> Unit) = post { animators += Animator(durationMs.toFloat(), update) {} }

    /** Hàm chạy mỗi khung hình (t giây, dt giây) trên luồng OpenGL. */
    fun onTick(fn: (Float, Float) -> Unit) = post { tickers += fn }

    /** Chiếu điểm thế giới ra màn hình theo khung hình gần nhất (an toàn đa luồng). */
    fun project(p: Vec3): ScreenPoint {
        val m = FloatArray(16)
        val w: Int; val h: Int
        synchronized(snapshot) { System.arraycopy(snapshot, 0, m, 0, 16); w = snapW; h = snapH }
        val x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
        val y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
        val z = m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14]
        val ww = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        if (ww <= 0f) return ScreenPoint(0f, 0f, false)
        return ScreenPoint((x / ww * 0.5f + 0.5f) * w, (-y / ww * 0.5f + 0.5f) * h, z / ww < 1f)
    }

    /** Giao điểm tia từ điểm chạm (pixel) với mặt phẳng y = [planeY], hoặc null. */
    fun pickPlane(sx: Float, sy: Float, planeY: Float): Vec3? {
        val inv = FloatArray(16)
        val w: Int; val h: Int
        synchronized(snapshot) { System.arraycopy(invSnapshot, 0, inv, 0, 16); w = snapW; h = snapH }
        val nx = sx / w * 2 - 1
        val ny = -(sy / h * 2 - 1)
        fun unproject(z: Float): Vec3 {
            val v = floatArrayOf(nx, ny, z, 1f)
            val o = FloatArray(4)
            Matrix.multiplyMV(o, 0, inv, 0, v, 0)
            return Vec3(o[0] / o[3], o[1] / o[3], o[2] / o[3])
        }
        val a = unproject(-1f)
        val b = unproject(1f)
        val d = b - a
        if (kotlin.math.abs(d.y) < 1e-6f) return null
        val t = (planeY - a.y) / d.y
        if (t < 0) return null
        return a + d * t
    }

    // ---------- GLSurfaceView.Renderer ----------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        generation++ // context mới: mọi geometry/texture tự tải lại
        mainProgram = Shaders.program(Shaders.MAIN_VS, Shaders.MAIN_FS)
        depthProgram = Shaders.program(Shaders.DEPTH_VS, Shaders.DEPTH_FS)
        skyProgram = Shaders.program(Shaders.SKY_VS, Shaders.SKY_FS)
        u.clear(); du.clear()
        for (name in listOf("uModel", "uNormalMat", "uViewProj", "uLightVP", "uUvOffset", "uColor", "uOpacity", "uEmissive",
            "uRoughness", "uMetalness", "uFlags", "uMap", "uEmissiveMap", "uShadowMap", "uSunDir", "uSunColor", "uFillDir",
            "uFillColor", "uHemiSky", "uHemiGround", "uCameraPos", "uFogColor", "uFog", "uExposure")) {
            u[name] = GLES30.glGetUniformLocation(mainProgram, name)
        }
        for (name in listOf("uModel", "uLightVP")) du[name] = GLES30.glGetUniformLocation(depthProgram, name)
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        skyVao = ids[0]
        createShadowMap()
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        if (ext.contains("GL_EXT_texture_filter_anisotropic")) {
            val f = FloatArray(1)
            GLES30.glGetFloatv(0x84FF, f, 0) // MAX_TEXTURE_MAX_ANISOTROPY_EXT
            anisotropy = min(8f, max(1f, f[0]))
        }
    }

    private fun createShadowMap() {
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        shadowTex = t[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, shadowSize, shadowSize, 0,
            GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL)
        val f = IntArray(1)
        GLES30.glGenFramebuffers(1, f, 0)
        shadowFbo = f[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, shadowTex, 0)
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        val proj = FloatArray(16)
        val view = FloatArray(16)
        Matrix.orthoM(proj, 0, -19f, 19f, -19f, 19f, 5f, 90f)
        Matrix.setLookAtM(view, 0, sunPos.x, sunPos.y, sunPos.z, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(lightVP, 0, proj, 0, view, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) = setViewport(w, h)

    fun setViewport(w: Int, h: Int) {
        width = max(1, w); height = max(1, h)
        camera.aspect = width.toFloat() / height
        // màn hình dọc: mở rộng góc nhìn để thấy đủ bàn cờ (như bản web)
        camera.fov = if (width < height) 58f else 42f
    }

    private var readyFired = false

    override fun onDrawFrame(gl: GL10?) {
        update(System.nanoTime())
        draw()
    }

    /**
     * Phần cập nhật của một khung hình (lệnh chờ, tick, hoạt cảnh, camera, ma trận) — không gọi OpenGL,
     * nên kiểm thử JVM có thể tự "bơm" khung hình để chạy cả ván chơi.
     */
    fun update(nowNs: Long) {
        if (startNs == 0L) { startNs = nowNs; lastNs = nowNs }
        val dt = min((nowNs - lastNs) / 1e9f, 0.1f)
        lastNs = nowNs
        time = (nowNs - startNs) / 1e9f
        val ms = time * 1000f

        while (true) commands.poll()?.invoke() ?: break
        for (fn in tickers) fn(time, dt)
        ticking.clear()
        scene.traverseVisible { if (it.tick != null) ticking += it }
        for (n in ticking) n.tick?.invoke(time, dt)
        if (animators.isNotEmpty()) {
            val finished = ArrayList<Animator>()
            for (a in animators.toList()) {
                if (a.start < 0) a.start = ms
                val k = if (a.durationMs <= 0f) 1f else min(1f, (ms - a.start) / a.durationMs)
                a.update(k)
                if (k >= 1f) finished += a
            }
            animators.removeAll(finished.toSet())
            finished.forEach { it.done() }
        }
        camera.update()
        scene.updateWorld(null)
        Matrix.multiplyMM(viewProj, 0, camera.projection, 0, camera.view, 0)
        synchronized(snapshot) {
            System.arraycopy(viewProj, 0, snapshot, 0, 16)
            Matrix.invertM(invSnapshot, 0, viewProj, 0)
            snapW = width; snapH = height
        }
    }

    private fun draw() {
        opaque.clear(); transparent.clear()
        scene.traverseVisible { n -> if (n is Mesh) (if (n.materials[0].transparent) transparent else opaque) += n }

        renderShadows()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glDepthMask(true)
        GLES30.glClearColor(fogColor[0], fogColor[1], fogColor[2], 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // trời
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glUseProgram(skyProgram)
        GLES30.glBindVertexArray(skyVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glUseProgram(mainProgram)
        setFrameUniforms()

        GLES30.glDisable(GLES30.GL_BLEND)
        for (m in opaque) drawMesh(m)

        if (transparent.isNotEmpty()) {
            val cp = camera.position
            transparent.sortByDescending { val w = it.world; val dx = w[12] - cp.x; val dy = w[13] - cp.y; val dz = w[14] - cp.z; dx * dx + dy * dy + dz * dz }
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            for (m in transparent) drawMesh(m)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glDepthMask(true)
        }
        GLES30.glBindVertexArray(0)
        if (!readyFired) { readyFired = true; onReady?.invoke() }
    }

    private fun renderShadows() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glViewport(0, 0, shadowSize, shadowSize)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(1.5f, 3f)
        GLES30.glUseProgram(depthProgram)
        GLES30.glUniformMatrix4fv(du.getValue("uLightVP"), 1, false, lightVP, 0)
        for (m in opaque) {
            if (!m.castShadow) continue
            GLES30.glUniformMatrix4fv(du.getValue("uModel"), 1, false, m.world, 0)
            m.geometry.bind(generation)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, m.geometry.indices.size, GLES30.GL_UNSIGNED_INT, 0)
        }
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
    }

    private fun setFrameUniforms() {
        GLES30.glUniformMatrix4fv(u.getValue("uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniformMatrix4fv(u.getValue("uLightVP"), 1, false, lightVP, 0)
        val sd = Vec3(sunPos.x, sunPos.y, sunPos.z).normalize()
        GLES30.glUniform3f(u.getValue("uSunDir"), sd.x, sd.y, sd.z)
        GLES30.glUniform3fv(u.getValue("uSunColor"), 1, sunColor, 0)
        GLES30.glUniform3f(u.getValue("uFillDir"), fillDir.x, fillDir.y, fillDir.z)
        GLES30.glUniform3fv(u.getValue("uFillColor"), 1, fillColor, 0)
        GLES30.glUniform3fv(u.getValue("uHemiSky"), 1, hemiSky, 0)
        GLES30.glUniform3fv(u.getValue("uHemiGround"), 1, hemiGround, 0)
        val cp = camera.position
        GLES30.glUniform3f(u.getValue("uCameraPos"), cp.x, cp.y, cp.z)
        GLES30.glUniform3fv(u.getValue("uFogColor"), 1, fogColor, 0)
        GLES30.glUniform2fv(u.getValue("uFog"), 1, fogRange, 0)
        GLES30.glUniform1f(u.getValue("uExposure"), exposure)
        GLES30.glUniform1i(u.getValue("uMap"), 0)
        GLES30.glUniform1i(u.getValue("uEmissiveMap"), 1)
        GLES30.glUniform1i(u.getValue("uShadowMap"), 2)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex)
    }

    private fun drawMesh(m: Mesh) {
        GLES30.glUniformMatrix4fv(u.getValue("uModel"), 1, false, m.world, 0)
        Matrix.invertM(tmp16, 0, m.world, 0)
        Matrix.transposeM(modelNormal, 0, tmp16, 0)
        GLES30.glUniformMatrix4fv(u.getValue("uNormalMat"), 1, false, modelNormal, 0)
        val g = m.geometry
        g.bind(generation)
        if (m.materials.size == 1 || g.groups.isEmpty()) {
            applyMaterial(m.materials[0], m.receiveShadow)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, g.indices.size, GLES30.GL_UNSIGNED_INT, 0)
        } else {
            for (grp in g.groups) {
                applyMaterial(m.materials[grp.materialIndex % m.materials.size], m.receiveShadow)
                GLES30.glDrawElements(GLES30.GL_TRIANGLES, grp.count, GLES30.GL_UNSIGNED_INT, grp.start * 4)
            }
        }
    }

    private fun applyMaterial(mat: Material, receiveShadow: Boolean) {
        srgbToLinear(mat.color, tmp3)
        val cs = mat.colorScale
        GLES30.glUniform3f(u.getValue("uColor"), tmp3[0] * cs, tmp3[1] * cs, tmp3[2] * cs)
        GLES30.glUniform1f(u.getValue("uOpacity"), mat.opacity)
        srgbToLinear(mat.emissive, tmp3)
        val e = mat.emissiveIntensity
        GLES30.glUniform3f(u.getValue("uEmissive"), tmp3[0] * e, tmp3[1] * e, tmp3[2] * e)
        GLES30.glUniform1f(u.getValue("uRoughness"), mat.roughness)
        GLES30.glUniform1f(u.getValue("uMetalness"), mat.metalness)
        var flags = 0
        if (mat.flat) flags = flags or 1
        val map = mat.map
        if (map != null) {
            flags = flags or 2
            map.bind(0, generation, anisotropy)
            GLES30.glUniform2f(u.getValue("uUvOffset"), map.offsetX, map.offsetY)
        } else {
            GLES30.glUniform2f(u.getValue("uUvOffset"), 0f, 0f)
        }
        val em = mat.emissiveMap
        if (em != null) { flags = flags or 4; em.bind(1, generation, anisotropy) }
        if (mat.unlit) flags = flags or 8
        if (receiveShadow) flags = flags or 16
        if (mat.side == Side.DOUBLE) {
            flags = flags or 32
            GLES30.glDisable(GLES30.GL_CULL_FACE)
        } else {
            GLES30.glEnable(GLES30.GL_CULL_FACE)
            GLES30.glCullFace(GLES30.GL_BACK)
        }
        GLES30.glUniform1i(u.getValue("uFlags"), flags)
        GLES30.glDepthMask(mat.depthWrite)
    }
}
