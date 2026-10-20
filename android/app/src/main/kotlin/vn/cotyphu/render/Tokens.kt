package vn.cotyphu.render

import vn.cotyphu.gl.Engine
import vn.cotyphu.gl.Geo
import vn.cotyphu.gl.Material
import vn.cotyphu.gl.Mesh
import vn.cotyphu.gl.Node
import vn.cotyphu.gl.PI_F
import vn.cotyphu.gl.Side
import vn.cotyphu.gl.Vec3
import vn.cotyphu.gl.easeInOutCubic
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Quân cờ mang biểu tượng văn hoá Việt — port từ js/render/tokens.js.

val TOKENS = linkedMapOf(
    "hat" to "Nón lá",
    "lantern" to "Đèn lồng",
    "drum" to "Trống đồng",
    "lotus" to "Hoa sen",
    "basket" to "Thuyền thúng",
)

private fun base(color: Int): Node = Node().add(cyl(0.22f, 0.25f, 0.06f, mat(color, roughness = 0.35f, metalness = 0.2f), seg = 24))

private fun hat(color: Int): Node {
    val g = base(color)
    g.add(cone(0.3f, 0.28f, mat(0xE8D4A0, roughness = 0.9f), 0f, 0.1f, 0f, 28))
    for (i in 1..3) {
        val ring = mesh(Geo.torus(0.3f - i * 0.07f, 0.006f, 4, 28), mat(0xB89A5A), 0f, 0.1f + i * 0.07f, 0f)
        ring.rotation.x = PI_F / 2
        g.add(ring)
    }
    val strap = mesh(Geo.torus(0.16f, 0.012f, 4, 20, PI_F), mat(color), 0f, 0.1f, 0f)
    strap.rotation.x = PI_F
    g.add(strap)
    return g
}

private fun lantern(color: Int): Node {
    val g = base(color)
    val pts = (0..10).map { i -> val t = i / 10f; (0.06f + sin(t * PI_F) * 0.15f) to t * 0.36f }
    val silk = Material(color, roughness = 0.6f, emissive = color, emissiveIntensity = 0.55f)
    g.add(mesh(Geo.lathe(pts, 16), silk, 0f, 0.14f, 0f))
    val gold = mat(0xD9A520, metalness = 0.7f, roughness = 0.3f)
    g.add(cyl(0.07f, 0.07f, 0.04f, gold, 0f, 0.11f, 0f, 12), cyl(0.07f, 0.07f, 0.04f, gold, 0f, 0.5f, 0f, 12))
    g.add(cyl(0.006f, 0.006f, 0.12f, gold, 0f, 0.54f, 0f, 4))
    for (k in 0 until 6) {
        val a = k / 6f * PI_F * 2
        g.add(cyl(0.004f, 0.004f, 0.1f, mat(0xD9A520), cos(a) * 0.05f, 0.02f, sin(a) * 0.05f, 3))
    }
    return g
}

private fun drumTexture() = canvasTexture(256, 256) { c, w, _ ->
    val cx = w / 2f
    c.drawColor(0xFFB8833A.toInt())
    val star = android.graphics.Path()
    for (i in 0 until 28) {
        val a = i * PI_F / 14
        val r = if (i % 2 == 0) 52f else 18f
        if (i == 0) star.moveTo(cx + cos(a) * r, cx + sin(a) * r) else star.lineTo(cx + cos(a) * r, cx + sin(a) * r)
    }
    star.close()
    c.drawPath(star, fill(0xF2CF7A))
    val p = stroke(0xF2CF7A, 4f)
    for (r in floatArrayOf(62f, 76f, 96f, 112f, 124f)) c.drawCircle(cx, cx, r, p)
}

private fun drum(color: Int): Node {
    val g = base(color)
    val bronze = mat(0xA8752F, metalness = 0.75f, roughness = 0.35f)
    val top = Material(map = drumTexture(), metalness = 0.6f, roughness = 0.4f)
    g.add(Mesh(Geo.cylinder(0.22f, 0.18f, 0.22f, 24), listOf(bronze, top, bronze)).also { it.position.y = 0.17f })
    g.add(cyl(0.2f, 0.22f, 0.08f, bronze, 0f, 0.04f, 0f, 24))
    val band = mesh(Geo.torus(0.205f, 0.015f, 6, 24), mat(color), 0f, 0.2f, 0f)
    band.rotation.x = PI_F / 2
    g.add(band)
    return g
}

private fun lotus(color: Int): Node {
    val g = base(color)
    val petal = mat(0xF7A8C4, roughness = 0.5f)
    val inner = mat(0xFBD1E0, roughness = 0.5f)
    for (layer in 0 until 2) {
        val n = if (layer == 0) 8 else 6
        for (k in 0 until n) {
            val a = k.toFloat() / n * PI_F * 2 + layer * 0.3f
            val p = sphere(0.1f, if (layer == 1) inner else petal, 0f, 0.1f, 0.09f - layer * 0.03f, 10, 8)
            p.scale.set(0.45f, 1.1f, 0.25f)
            p.rotation.x = -0.5f + layer * 0.25f
            val pivot = Node().add(p)
            pivot.rotation.y = a
            pivot.position.y = 0.08f + layer * 0.04f
            g.add(pivot)
        }
    }
    g.add(cyl(0.05f, 0.06f, 0.05f, mat(0xF2D04A), 0f, 0.2f, 0f, 12))
    g.add(cyl(0.21f, 0.21f, 0.02f, mat(0x3F9A4A), 0f, 0.06f, 0f, 18))
    val ring = mesh(Geo.torus(0.21f, 0.012f, 4, 20), mat(color), 0f, 0.08f, 0f)
    ring.rotation.x = PI_F / 2
    g.add(ring)
    return g
}

private fun basket(color: Int): Node {
    val g = base(color)
    val weave = mat(0x8A6A3A, roughness = 0.95f, side = Side.DOUBLE)
    val bowl = mesh(Geo.sphere(0.24f, 20, 10, 0f, PI_F * 2, PI_F / 2, PI_F / 2), weave, 0f, 0.3f, 0f)
    bowl.scale.y = 0.7f
    g.add(bowl)
    val rim = mesh(Geo.torus(0.24f, 0.02f, 6, 24), mat(color), 0f, 0.3f, 0f)
    rim.rotation.x = PI_F / 2
    g.add(rim)
    val oar = cyl(0.01f, 0.01f, 0.45f, mat(0x6B4A2A), 0.1f, 0.2f, 0f, 5)
    oar.rotation.z = 0.5f
    g.add(oar)
    return g
}

/** Một quân cờ trên bàn. Mọi hàm chỉ đụng tới cây cảnh trên luồng OpenGL (qua engine). */
class Token(private val engine: Engine, token: String, color: Int) {
    val group: Node = when (token) {
        "lantern" -> lantern(color)
        "drum" -> drum(color)
        "lotus" -> lotus(color)
        "basket" -> basket(color)
        else -> hat(color)
    }.withShadows()

    private val ringMat = Material(color, opacity = 0f, transparent = true, unlit = true, depthWrite = false, side = Side.DOUBLE)
    private val ring = Mesh(Geo.ring(0.34f, 0.42f, 32), ringMat)

    @Volatile var active = false
    @Volatile private var moving = false

    init {
        group.scale.setScalar(1.35f)
        ring.rotation.x = -PI_F / 2
        ring.position.y = 0.01f
        ring.castShadow = false
        group.add(ring)
        group.tick = { t, _ ->
            val target = if (active) 0.85f else 0f
            ringMat.opacity += (target - ringMat.opacity) * 0.15f
            ring.scale.setScalar(1 + sin(t * 4) * 0.08f)
            if (active && !moving) group.rotation.y += 0.01f
        }
        group.dynamic = true
    }

    /** Vị trí hiện tại (đọc trên luồng OpenGL). */
    fun position(): Vec3 = group.position.copy()

    /** Nhảy lần lượt qua các điểm, mỗi bước một đường parabol. */
    suspend fun hopThrough(points: List<Vec3>, stepMs: Long = 210, height: Float = 0.9f, onStep: (Int) -> Unit = {}) {
        moving = true
        for ((i, to) in points.withIndex()) {
            val from = engine.run { group.position.copy() }
            val dir = to - from
            if (dir.lengthSq() > 0.0001f) engine.post { group.rotation.y = atan2(dir.x, dir.z) }
            engine.tween(stepMs) { k ->
                val e = easeInOutCubic(k)
                group.position.lerpVectors(from, to, e)
                group.position.y = from.y + (to.y - from.y) * e + sin(PI_F * k) * height
                val squash = 1 + sin(PI_F * k) * 0.12f
                group.scale.set(1.35f / sqrt(squash), 1.35f * squash, 1.35f / sqrt(squash))
            }
            onStep(i)
        }
        engine.post { group.scale.setScalar(1.35f) }
        moving = false
    }

    /** Bay vọt lên rồi đáp xuống (bị đưa vào tù). */
    suspend fun flyTo(to: Vec3, ms: Long = 900) {
        moving = true
        val from = engine.run { group.position.copy() }
        engine.tween(ms) { k ->
            val e = easeInOutCubic(k)
            group.position.lerpVectors(from, to, e)
            group.position.y += sin(PI_F * k) * 4
            group.rotation.y += 0.25f
        }
        moving = false
    }

    suspend fun slideTo(to: Vec3, ms: Long = 250) {
        val from = engine.run { group.position.copy() }
        engine.tween(ms) { k -> group.position.lerpVectors(from, to, easeInOutCubic(k)) }
    }
}
