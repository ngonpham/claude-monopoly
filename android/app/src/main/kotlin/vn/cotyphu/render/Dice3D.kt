package vn.cotyphu.render

import vn.cotyphu.gl.Engine
import vn.cotyphu.gl.Geo
import vn.cotyphu.gl.Material
import vn.cotyphu.gl.Mesh
import vn.cotyphu.gl.Node
import vn.cotyphu.gl.Quat
import vn.cotyphu.gl.Vec3
import vn.cotyphu.gl.dyn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random

// Hai viên xúc xắc 3D — port từ js/render/dice3d.js: lăn, nảy và dừng đúng mặt engine trả về.

// Thứ tự mặt BoxGeometry: +x, -x, +y, -y, +z, -z (mặt đối diện cộng lại = 7).
private val FACE_VALUES = intArrayOf(3, 4, 1, 6, 2, 5)
private val FACE_NORMALS = arrayOf(
    Vec3(1f, 0f, 0f), Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, -1f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, 0f, -1f),
)
private val PIPS = mapOf(
    1 to listOf(0.5f to 0.5f),
    2 to listOf(0.27f to 0.27f, 0.73f to 0.73f),
    3 to listOf(0.25f to 0.25f, 0.5f to 0.5f, 0.75f to 0.75f),
    4 to listOf(0.27f to 0.27f, 0.73f to 0.27f, 0.27f to 0.73f, 0.73f to 0.73f),
    5 to listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.5f to 0.5f, 0.25f to 0.75f, 0.75f to 0.75f),
    6 to listOf(0.27f to 0.22f, 0.73f to 0.22f, 0.27f to 0.5f, 0.73f to 0.5f, 0.27f to 0.78f, 0.73f to 0.78f),
)

private fun faceMaterial(v: Int) = Material(roughness = 0.35f, map = canvasTexture(128, 128) { c, s, _ ->
    c.drawColor(0xFFFFFAF0.toInt())
    // kiểu xúc xắc Á Đông: mặt 1 và 4 chấm đỏ
    val p = fill(if (v == 1 || v == 4) 0xD01C1C else 0x1B1B1B)
    val r = if (v == 1) 20f else 11f
    for ((x, y) in PIPS.getValue(v)) c.drawCircle(x * s, y * s, r, p)
})

class Dice3D(private val engine: Engine) {
    val group = Node().dyn()
    private val dice: List<Mesh>

    init {
        val mats = FACE_VALUES.map { faceMaterial(it) }
        val geo = Geo.box(1f, 1f, 1f)
        dice = (0 until 2).map { i ->
            Mesh(geo, mats).dyn().also {
                it.position.set(-0.9f + i * 1.8f, 0.5f, 5.2f)
                group.add(it)
            }
        }
        show(5, 6)
    }

    companion object {
        /** Quaternion để mặt mang giá trị v hướng lên trên, xoay [yaw] quanh trục đứng. */
        fun orientationFor(v: Int, yaw: Float): Quat {
            val q = Quat().setFromUnitVectors(FACE_NORMALS[FACE_VALUES.indexOf(v)], Vec3(0f, 1f, 0f))
            return Quat().setFromAxisAngle(Vec3(0f, 1f, 0f), yaw).multiply(q)
        }
    }

    /** Đặt ngay hai mặt (khi khôi phục ván). Gọi trước khi thêm vào cảnh hoặc trên luồng OpenGL. */
    fun show(a: Int, b: Int) {
        intArrayOf(a, b).forEachIndexed { i, v ->
            dice[i].quaternion = orientationFor(v, 0.3f + i * 0.5f)
            dice[i].position.y = 0.5f
        }
    }

    suspend fun roll(a: Int, b: Int) {
        class Start(val pos: Vec3, val end: Vec3, val axis: Vec3, val spins: Float, val final: Quat)
        val starts = intArrayOf(a, b).mapIndexed { i, v ->
            Start(
                Vec3(-4f + i * 1.2f, 4.5f, 8.5f),
                Vec3(-0.9f + i * 1.8f + (Random.nextFloat() - 0.5f) * 0.6f, 0.5f, 5.2f + (Random.nextFloat() - 0.5f) * 0.8f),
                Vec3(Random.nextFloat() - 0.5f, Random.nextFloat() - 0.5f, Random.nextFloat() - 0.5f).normalize(),
                3 + Random.nextFloat() * 2,
                orientationFor(v, Random.nextFloat() * PI.toFloat() * 2),
            )
        }
        engine.tween(1250) { t ->
            dice.forEachIndexed { i, d ->
                val s = starts[i]
                val e = 1 - (1 - t) * (1 - t)
                d.position.lerpVectors(s.pos, s.end, e)
                val bounce = abs(cos(t * PI.toFloat() * 3.2f)) * Math.pow((1 - t).toDouble(), 1.5).toFloat() * 3.2f
                d.position.y = 0.5f + bounce
                val remain = (1 - e) * s.spins * PI.toFloat() * 2
                val q = Quat().setFromAxisAngle(s.axis, remain).multiply(s.final)
                d.quaternion = q
            }
        }
        engine.post { dice.forEachIndexed { i, d -> d.quaternion = Quat().set(starts[i].final) } }
    }
}
