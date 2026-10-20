package vn.cotyphu.gl

import android.opengl.Matrix
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Camera phối cảnh + điều khiển quỹ đạo kiểu OrbitControls (có giảm chấn), kèm chế độ "theo dõi"
 * quân cờ và bay về toàn cảnh như bản web. Mọi hàm chỉ gọi trên luồng OpenGL.
 */
class OrbitCamera {
    val position = Vec3(0f, 30f, 31f)
    val target = Vec3(0f, 0f, 0f)
    val home = Vec3(0f, 30f, 31f)
    var fov = 42f
    var aspect = 1f
    val view = FloatArray(16)
    val projection = FloatArray(16)

    var minDistance = 8f
    var maxDistance = 75f
    var maxPolarAngle = 1.38f
    private val damping = 0.08f

    private var dTheta = 0f
    private var dPhi = 0f
    private var zoomScale = 1f
    private val panOffset = Vec3()

    var userInteracting = false
    private var followTarget: Vec3? = null
    private var flyTo: Vec3? = null
    var viewportHeight = 1f

    fun follow(v: Vec3?) { followTarget = v?.copy() }

    fun resetView() {
        followTarget = Vec3()
        flyTo = home.copy()
    }

    // ---------- đầu vào cảm ứng (pixel) ----------

    fun rotate(dxPx: Float, dyPx: Float) {
        dTheta -= 2 * PI.toFloat() * dxPx / viewportHeight
        dPhi -= 2 * PI.toFloat() * dyPx / viewportHeight
    }

    fun zoom(scale: Float) { zoomScale *= scale }

    fun pan(dxPx: Float, dyPx: Float) {
        val offset = position - target
        val targetDistance = offset.length() * tan(Math.toRadians(fov / 2.0).toFloat())
        // trục x và y của camera trong toạ độ thế giới (lấy từ ma trận view)
        val right = Vec3(view[0], view[4], view[8])
        val up = Vec3(view[1], view[5], view[9])
        panOffset.add(right.scale(-2 * dxPx * targetDistance / viewportHeight))
        panOffset.add(up.scale(2 * dyPx * targetDistance / viewportHeight))
    }

    fun update() {
        followTarget?.let { ft ->
            if (!userInteracting) {
                val delta = (ft - target).scale(0.06f)
                target.add(delta)
                position.add(delta)
            }
        }
        flyTo?.let { to ->
            position.lerp(to, 0.06f)
            if (position.distanceTo(to) < 0.05f) flyTo = null
        }

        val offset = position - target
        var radius = offset.length()
        var theta = atan2(offset.x, offset.z)
        var phi = kotlin.math.acos((offset.y / max(radius, 1e-6f)).coerceIn(-1f, 1f))

        theta += dTheta * damping
        phi += dPhi * damping
        phi = phi.coerceIn(0.01f, maxPolarAngle)
        radius = (radius * zoomScale).coerceIn(minDistance, maxDistance)
        target.add(panOffset.copy().scale(damping))

        val sinPhi = sin(phi)
        position.set(target.x + radius * sinPhi * sin(theta), target.y + radius * cos(phi), target.z + radius * sinPhi * cos(theta))

        dTheta *= 1 - damping
        dPhi *= 1 - damping
        panOffset.scale(1 - damping)
        zoomScale = 1f

        Matrix.setLookAtM(view, 0, position.x, position.y, position.z, target.x, target.y, target.z, 0f, 1f, 0f)
        Matrix.perspectiveM(projection, 0, fov, aspect, 0.1f, 500f)
    }

    /** Khoảng cách tới đích (để giới hạn thu phóng). */
    fun distance() = sqrt((position - target).lengthSq())

    @Suppress("unused")
    private fun clampAngle(a: Float) = min(max(a, -PI.toFloat()), PI.toFloat())
}
