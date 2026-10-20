package vn.cotyphu.render

import android.graphics.Path
import vn.cotyphu.gl.Geo
import vn.cotyphu.gl.Material
import vn.cotyphu.gl.Mesh
import vn.cotyphu.gl.Node
import vn.cotyphu.gl.PI_F
import vn.cotyphu.gl.Quat
import vn.cotyphu.gl.Side
import vn.cotyphu.gl.Texture
import vn.cotyphu.gl.Vec3
import vn.cotyphu.gl.dyn
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// Mô hình 3D low-poly dựng bằng code cho từng công trình / thắng cảnh — port từ js/render/landmarks.js.
// Mỗi hàm trả về Node có đáy tại y = 0, chiếm khoảng x ∈ [-0.9, 0.9], z ∈ [-0.8, 0.8].
// Phần chuyển động được đánh dấu dyn() và cập nhật trong Node.tick của nút gốc.

private object C {
    const val darkWood = 0x4A2A15
    const val stone = 0x9D988C
    const val grass = 0x6CAD4F
    const val darkGrass = 0x3F7F3A
    const val sand = 0xE9C88A
    const val gold = 0xD9A520
    const val red = 0xD7262E
    const val white = 0xF3EFE6
    const val rock = 0x6E6A60
    const val black = 0x222222
}

private fun ground(color: Int, w: Float = 1.7f, d: Float = 1.5f) = box(w, 0.04f, d, mat(color))

// ---------------------------------------------------------------- Tây Nguyên

private fun woodenChurch(): Node {
    val g = Node()
    g.add(ground(C.grass))
    val wood = mat(0x5A3218, roughness = 0.9f)
    g.add(box(0.55f, 0.4f, 0.85f, wood, 0f, 0.04f, -0.1f))
    val roof = gableRoof(0.95f, 0.42f, 0.72f, mat(0x2E1D12), 0f, 0.44f, -0.1f)
    roof.rotation.y = PI_F / 2
    g.add(roof)
    g.add(box(0.28f, 0.78f, 0.28f, wood, 0f, 0.04f, 0.42f))
    val spire = cone(0.21f, 0.5f, mat(0x2E1D12), 0f, 0.82f, 0.42f, 4)
    spire.rotation.y = PI_F / 4
    g.add(spire)
    g.add(box(0.02f, 0.16f, 0.02f, mat(C.white), 0f, 1.3f, 0.42f))
    g.add(box(0.1f, 0.02f, 0.02f, mat(C.white), 0f, 1.4f, 0.42f))
    g.add(box(0.12f, 0.2f, 0.02f, mat(0x1A0F08), 0f, 0.04f, 0.565f))
    g.add(sphere(0.05f, mat(0xE8D48A, emissive = 0x6B5A20), 0f, 0.55f, 0.565f, 10, 8))
    for (z in floatArrayOf(-0.35f, -0.1f, 0.15f)) {
        g.add(box(0.02f, 0.14f, 0.08f, mat(0xE8D48A, emissive = 0x3A3010), 0.285f, 0.18f, z))
        g.add(box(0.02f, 0.14f, 0.08f, mat(0xE8D48A, emissive = 0x3A3010), -0.285f, 0.18f, z))
    }
    g.add(tree(-0.65f, 0.4f, 1.3f), tree(0.65f, -0.45f, 1.5f), tree(0.7f, 0.45f, 1.1f))
    return g
}

private fun waterfall(): Node {
    val g = Node()
    val rock = mat(0x5F6F55, flat = true)
    g.add(box(1.7f, 0.85f, 0.55f, rock, 0f, 0f, -0.48f))
    g.add(box(1.7f, 0.08f, 0.55f, mat(C.darkGrass), 0f, 0.85f, -0.48f))
    g.add(water(1.7f, 0.95f, 0f, 0.27f, 0.03f, 0x4FB1C9))
    val rnd = Random(7)
    val tex = canvasTexture(64, 128, repeat = true) { c, w, h ->
        c.drawColor(0xFFBFE9F5.toInt())
        repeat(40) {
            val p = fill(0xFFFFFF, 0.4f + rnd.nextFloat() * 0.6f)
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
            c.drawRect(x, y, x + 2 + rnd.nextFloat() * 3, y + 10 + rnd.nextFloat() * 30, p)
        }
    }
    val fall = Mesh(Geo.plane(1.2f, 0.88f), Material(map = tex, opacity = 0.92f, roughness = 0.2f))
    fall.position.set(0f, 0.47f, -0.2f)
    g.add(fall)
    val mist = sphere(0.55f, mat(0xFFFFFF, opacity = 0.35f), 0f, 0.05f, -0.05f, 16, 8).dyn()
    mist.scale.set(1.3f, 0.25f, 0.5f)
    g.add(mist)
    val r = rand(7)
    repeat(6) {
        val s = Mesh(Geo.dodecahedron(0.06f + r() * 0.06f), mat(C.rock, flat = true))
        s.position.set(-0.75f + r() * 1.5f, 0.05f, 0.25f + r() * 0.45f)
        g.add(s)
    }
    g.add(tree(-0.7f, -0.6f, 1.2f, C.darkGrass, 0.9f), tree(0.65f, -0.55f, 1.4f, C.darkGrass, 0.9f))
    g.tick = { t, dt -> tex.offsetY += dt * 0.8f; mist.scale.x = 1.3f + sin(t * 2) * 0.05f }
    return g
}

// ---------------------------------------------------------------- Miền núi phía Bắc

fun flagTexture(): Texture = canvasTexture(180, 120) { c, w, h ->
    c.drawColor(0xFFDA251D.toInt())
    val path = Path()
    val cx = w / 2f; val cy = h / 2f; val big = h * 0.3f; val small = big * 0.38f
    for (i in 0 until 10) {
        val a = -PI_F / 2 + i * PI_F / 5
        val rr = if (i % 2 == 0) big else small
        val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    c.drawPath(path, fill(0xFFDE00))
}

/** Lá cờ phấp phới (mép trái gắn vào cột). Trả về lưới động có tick riêng. */
fun wavingFlag(width: Float, height: Float, texture: Texture = flagTexture()): Mesh {
    val geo = Geo.plane(width, height, 12, 2).translate(width / 2, 0f, 0f)
    geo.dynamic = true
    val base = geo.positions.copyOf()
    val m = Mesh(geo, Material(map = texture, side = Side.DOUBLE, roughness = 0.8f)).dyn()
    m.tick = { t, _ ->
        val p = geo.positions
        for (i in p.indices step 3) {
            val x = base[i]
            p[i + 2] = sin(x * 9 - t * 5) * 0.06f * (x / width)
        }
        geo.computeVertexNormals()
        geo.markDirty()
    }
    return m
}

private fun flagpole(): Node {
    val g = Node()
    g.add(cone(0.85f, 0.45f, mat(0x5E9A4A, flat = true), seg = 7))
    g.add(cyl(0.2f, 0.24f, 0.42f, mat(0xC9C1AD), 0f, 0.4f, 0f, 8))
    g.add(cyl(0.22f, 0.22f, 0.04f, mat(0xB8B09A), 0f, 0.82f, 0f, 8))
    g.add(cyl(0.012f, 0.016f, 0.85f, mat(0xEEEEEE, metalness = 0.6f, roughness = 0.3f), 0f, 0.85f, 0f, 8))
    val flag = wavingFlag(0.5f, 0.33f)
    flag.position.set(0.01f, 1.52f, 0f)
    g.add(flag)
    for (i in 0 until 4) g.add(box(0.16f, 0.04f, 0.08f, mat(0xB8B09A), 0f, 0.1f + i * 0.08f, 0.62f - i * 0.1f))
    return g
}

private fun terraces(): Node {
    val g = Node()
    for (i in 0 until 7) {
        val color = if (i % 2 == 0) 0xC8B232 else 0x9FC23A
        val layer = cyl(0.9f - i * 0.12f, 0.92f - i * 0.12f, 0.08f, mat(color, roughness = 0.9f), 0f, i * 0.08f, 0f, 28)
        layer.scale.z = 0.82f
        g.add(layer)
    }
    g.add(box(0.14f, 0.1f, 0.12f, mat(0x8A6A3D), 0f, 0.56f, 0f))
    g.add(gableRoof(0.18f, 0.1f, 0.16f, mat(0xCAA860), 0f, 0.66f, 0f))
    g.add(tree(0.75f, -0.6f, 1.2f), tree(-0.8f, 0.55f, 1f))
    return g
}

private fun mountain(): Node {
    val g = Node()
    g.add(cone(0.8f, 1.35f, mat(0x587A55, flat = true), 0f, 0f, -0.1f, 6))
    g.add(cone(0.55f, 0.85f, mat(0x6B8F5F, flat = true), -0.45f, 0f, 0.3f, 6))
    g.add(cone(0.45f, 0.6f, mat(0x7AA06B, flat = true), 0.5f, 0f, 0.35f, 5))
    g.add(cone(0.07f, 0.16f, mat(0xDDDDDD, metalness = 0.9f, roughness = 0.2f), 0f, 1.33f, -0.1f, 3))
    val a = Vec3(0.12f, 1.2f, -0.1f)
    val b = Vec3(0.85f, 0.15f, 0.6f)
    val cable = Mesh(Geo.cylinder(0.006f, 0.006f, a.distanceTo(b), 4), mat(0x333333))
    cable.position.set(a).lerp(b, 0.5f)
    cable.quaternion = Quat().setFromUnitVectors(Vec3(0f, 1f, 0f), (b - a).normalize())
    g.add(cable)
    val cabinG = Node().dyn()
    cabinG.add(box(0.08f, 0.07f, 0.06f, mat(C.red), 0f, -0.07f, 0f))
    g.add(cabinG)
    val clouds = (0 until 3).map { i ->
        sphere(0.18f, mat(0xFFFFFF, opacity = 0.7f), -0.5f + i * 0.5f, 0.7f + i * 0.12f, 0.2f - i * 0.2f, 12, 8).dyn().also {
            it.scale.set(1.6f, 0.5f, 0.9f)
            g.add(it)
        }
    }
    g.tick = { t, _ ->
        val k = (sin(t * 0.35f) + 1) / 2
        cabinG.position.lerpVectors(a, b, k)
        clouds.forEachIndexed { i, c -> c.position.x = sin(t * 0.2f + i * 2) * 0.55f }
    }
    return g
}

// ---------------------------------------------------------------- Miền Tây

fun boat(hullColor: Int = C.darkWood, length: Float = 0.55f): Node {
    val g = Node()
    val geo = Geo.extrude(listOf(-length / 2 to 0.1f, length / 2 to 0.12f, length * 0.36f to 0f, -length * 0.38f to 0f), 0.16f)
    geo.translate(0f, 0f, -0.08f)
    g.add(mesh(geo, mat(hullColor)))
    g.add(sphere(0.015f, mat(0xFFFFFF), length * 0.38f, 0.08f, 0.081f, 6, 4))
    g.add(sphere(0.015f, mat(0xFFFFFF), length * 0.38f, 0.08f, -0.081f, 6, 4))
    return g
}

private fun floatingMarket(): Node {
    val g = Node()
    g.add(water(1.75f, 1.55f, 0f, 0f, 0.02f, 0x8A7A4A))
    val fruit = intArrayOf(0xFF8C1A, 0x6CBF3A, 0x8E3FA0, 0xFFD23A, 0xD2302A)
    val r = rand(3)
    val configs = listOf(Triple(-0.35f, -0.4f, 0.3f), Triple(0.3f, 0.05f, -0.2f), Triple(-0.25f, 0.5f, 0.1f))
    val boats = configs.mapIndexed { k, (x, z, rot) ->
        val b = boat(if (k == 1) 0x2D5C7A else C.darkWood, 0.7f).dyn()
        repeat(9) {
            b.add(sphere(0.045f, mat(fruit[floor(r() * fruit.size).toInt()]), -0.15f + r() * 0.3f, 0.13f + r() * 0.05f, -0.05f + r() * 0.1f, 8, 6))
        }
        val roof = Mesh(Geo.cylinder(0.1f, 0.1f, 0.18f, 10, 1, true, 0f, PI_F), mat(0x5A3D22, side = Side.DOUBLE))
        roof.rotationOrder = "YZX"
        roof.rotation.z = PI_F / 2
        roof.rotation.y = PI_F / 2
        roof.position.set(-0.2f, 0.12f, 0f)
        b.add(roof)
        b.add(cyl(0.008f, 0.008f, 0.6f, mat(0xB08A5A), 0.12f, 0.1f, 0f, 5))
        b.add(sphere(0.05f, mat(fruit[k]), 0.12f, 0.65f, 0f, 8, 6))
        b.position.set(x, 0.02f, z)
        b.rotation.y = rot
        g.add(b)
        b
    }
    g.add(palm(0.75f, -0.6f, 1.1f), palm(-0.8f, -0.65f, 0.9f))
    g.tick = { t, _ ->
        boats.forEachIndexed { i, b ->
            b.position.y = 0.02f + sin(t * 1.6f + i) * 0.012f
            b.rotation.x = sin(t * 1.3f + i * 2) * 0.04f
        }
    }
    return g
}

private fun mangrove(): Node {
    val g = Node()
    g.add(water(1.75f, 1.55f, 0f, 0f, 0.02f, 0x7FB04A))
    val r = rand(11)
    repeat(11) {
        val x = -0.75f + r() * 1.5f
        val z = -0.7f + r() * 1.0f
        if (abs(z - 0.15f) < 0.12f) return@repeat
        val h = 0.45f + r() * 0.35f
        g.add(cyl(0.015f, 0.025f, h, mat(0xD9D2C3), x, 0f, z, 5))
        val crown = Mesh(Geo.icosahedron(0.12f + r() * 0.06f), mat(0x3D8B3D, flat = true))
        crown.position.set(x, h + 0.06f, z)
        crown.scale.y = 1.3f
        g.add(crown)
    }
    g.add(box(1.6f, 0.03f, 0.12f, mat(0x9A7A4A), 0f, 0.12f, 0.15f))
    var px = -0.7f
    while (px <= 0.7f + 1e-4f) { g.add(cyl(0.012f, 0.012f, 0.12f, mat(0x6B5030), px, 0f, 0.15f, 5)); px += 0.35f }
    val b = boat(0x6B4B2A, 0.4f)
    b.position.set(0.35f, 0.02f, 0.5f)
    g.add(b)
    val bird = Node().dyn()
    val body = sphere(0.035f, mat(0xFFFFFF), seg8 = true)
    body.scale.set(1.8f, 0.8f, 0.8f)
    val wing = box(0.03f, 0.005f, 0.16f, mat(0xFFFFFF)).dyn()
    bird.add(body, wing)
    g.add(bird)
    g.tick = { t, _ ->
        bird.position.set(cos(t * 0.6f) * 0.55f, 0.95f + sin(t * 1.2f) * 0.05f, sin(t * 0.6f) * 0.4f - 0.2f)
        bird.rotation.y = -t * 0.6f
        wing.scale.z = 0.6f + abs(sin(t * 8)) * 0.6f
    }
    return g
}

private fun sphere(r: Float, m: Material, seg8: Boolean) = sphere(r, m, 0f, 0f, 0f, if (seg8) 8 else 16, if (seg8) 6 else 12)

private fun shipMonument(): Node {
    val g = Node()
    g.add(box(1.7f, 0.04f, 0.7f, mat(0x6B7A3A), 0f, 0f, -0.4f))
    g.add(water(1.7f, 0.85f, 0f, 0.35f, 0.02f, 0x4F9FB8))
    val geo = Geo.extrude(listOf(-0.55f to 0f, 0.35f to 0f, 0.62f to 0.45f, 0.3f to 0.32f, -0.55f to 0.3f), 0.26f)
    geo.translate(0f, 0f, -0.13f)
    val ship = mesh(geo, mat(0xF2F0EA, roughness = 0.5f), -0.05f, 0.04f, 0f)
    ship.rotation.y = -0.5f
    g.add(ship)
    g.add(box(0.18f, 0.14f, 0.14f, mat(0xC9C4B8), -0.3f, 0.33f, 0.12f))
    g.add(cyl(0.01f, 0.01f, 0.55f, mat(0xDDDDDD), -0.35f, 0.47f, 0.15f, 6))
    val flag = wavingFlag(0.24f, 0.16f)
    flag.position.set(-0.34f, 0.95f, 0.15f)
    g.add(flag)
    for ((x, z) in listOf(-0.75f to -0.55f, -0.45f to -0.65f, 0.55f to -0.6f, 0.78f to -0.45f, 0.2f to -0.68f)) {
        g.add(tree(x, z, 1.1f, 0x2D6B34))
    }
    return g
}

// ---------------------------------------------------------------- Nam Trung Bộ

private fun chamTower(s: Float, brick: Int): Node {
    val g = Node()
    val m = mat(brick, flat = true, roughness = 0.95f)
    g.add(box(0.34f * s, 0.5f * s, 0.34f * s, m))
    g.add(box(0.29f * s, 0.18f * s, 0.29f * s, m, 0f, 0.5f * s, 0f))
    g.add(box(0.23f * s, 0.14f * s, 0.23f * s, m, 0f, 0.68f * s, 0f))
    g.add(box(0.17f * s, 0.12f * s, 0.17f * s, m, 0f, 0.82f * s, 0f))
    val top = cone(0.1f * s, 0.2f * s, m, 0f, 0.94f * s, 0f, 4)
    top.rotation.y = PI_F / 4
    top.name = "top"
    g.add(top)
    g.add(box(0.1f * s, 0.22f * s, 0.02f, mat(0x2A1A12), 0f, 0f, 0.17f * s + 0.005f))
    for (x in floatArrayOf(-0.12f, 0.12f)) g.add(box(0.04f * s, 0.5f * s, 0.02f, m, x * s, 0f, 0.17f * s + 0.01f))
    return g
}

private fun chamTowers(): Node {
    val g = Node()
    g.add(box(1.7f, 0.1f, 1.2f, mat(C.stone, flat = true), 0f, 0f, -0.15f))
    g.add(box(0.5f, 0.06f, 0.3f, mat(C.stone), 0f, 0f, 0.6f))
    g.add(chamTower(1.45f, 0xB0573A).setPos(0f, 0.1f, -0.25f))
    g.add(chamTower(1.0f, 0xA24E34).setPos(-0.55f, 0.1f, -0.1f))
    g.add(chamTower(0.9f, 0xA85438).setPos(0.55f, 0.1f, -0.05f))
    g.add(palm(0.75f, 0.55f, 0.9f), palm(-0.78f, 0.5f, 1f))
    return g
}

private fun dunes(): Node {
    val g = Node()
    g.add(box(1.75f, 0.04f, 1.55f, mat(C.sand)))
    for ((pos, col) in listOf(
        floatArrayOf(-0.4f, -0.35f, 0.6f) to 0xE39A52, floatArrayOf(0.35f, -0.3f, 0.55f) to 0xF1D6A8,
        floatArrayOf(0f, 0.25f, 0.45f) to 0xE8A85E, floatArrayOf(-0.6f, 0.45f, 0.3f) to 0xF4E0B8,
    )) {
        val d = sphere(pos[2], mat(col, roughness = 1f), pos[0], 0.02f, pos[1], 24, 12)
        d.scale.set(1.3f, 0.42f, 1f)
        g.add(d)
    }
    val atv = Node().dyn()
    atv.add(box(0.16f, 0.06f, 0.1f, mat(0xD63A2A), 0f, 0.04f, 0f))
    for ((x, z) in listOf(-0.06f to 0.06f, 0.06f to 0.06f, -0.06f to -0.06f, 0.06f to -0.06f)) {
        val w = cyl(0.03f, 0.03f, 0.03f, mat(C.black), x, 0f, z, 8)
        w.rotation.x = PI_F / 2
        w.position.y = 0.03f
        atv.add(w)
    }
    g.add(atv)
    g.add(palm(0.75f, 0.5f, 1.1f))
    g.tick = { t, _ ->
        val a = t * 0.4f
        atv.position.set(cos(a) * 0.55f, 0.05f + max(0f, sin(a * 2)) * 0.05f, 0.25f + sin(a) * 0.4f)
        atv.rotation.y = -a - PI_F / 2
    }
    return g
}

private fun basalt(): Node {
    val g = Node()
    g.add(water(1.75f, 1.55f, 0f, 0f, 0.02f, 0x2F8FB0))
    val r = rand(5)
    for (row in 0 until 7) for (col in 0 until 8) {
        val x = -0.72f + col * 0.19f + (row % 2) * 0.095f
        val z = -0.6f + row * 0.165f
        val dist = hypot(x * 0.8f, z + 0.2f)
        val h = max(0.06f, 0.65f - dist * 0.55f + r() * 0.12f)
        val shade = 0x34 + floor(r() * 0x18).toInt()
        val color = (shade shl 16) or (shade shl 8) or (shade + 6)
        g.add(mesh(Geo.cylinder(0.1f, 0.1f, h, 6), mat(color, flat = true, roughness = 0.6f), x, h / 2, z))
    }
    return g
}

// ---------------------------------------------------------------- Di sản miền Trung

private fun mySon(): Node {
    val g = Node()
    g.add(ground(0x4F8A3C))
    val t1 = chamTower(1.25f, 0x8F4A30).setPos(-0.2f, 0.04f, -0.2f)
    t1.children.first { it.name == "top" }.visible = false // chóp đã đổ
    g.add(t1, chamTower(0.85f, 0x7F4430).setPos(0.45f, 0.04f, -0.35f))
    g.add(box(0.4f, 0.12f, 0.08f, mat(0x8A4A33, flat = true), 0.35f, 0.04f, 0.25f))
    g.add(box(0.2f, 0.07f, 0.08f, mat(0x8A4A33, flat = true), -0.45f, 0.04f, 0.35f))
    g.add(box(0.12f, 0.03f, 0.12f, mat(0x5F8F3A), -0.2f, 1.06f, -0.2f))
    val r = rand(19)
    for (i in 0 until 8) {
        val a = r() * PI_F * 2
        val rx = 0.6f + r() * 0.2f
        val rz = 0.55f + r() * 0.1f
        g.add(tree(cos(a) * rx, sin(a) * rz, 1.2f + r() * 0.8f, if (i % 2 == 1) C.darkGrass else 0x2E6B2E))
    }
    return g
}

/** Đèn lồng phát sáng — mỗi chiếc có vật liệu riêng để nhấp nháy độc lập. */
fun lanternMesh(color: Int): Mesh {
    val geo = Geo.sphere(0.045f, 10, 8).scale(1f, 1.25f, 1f)
    return Mesh(geo, Material(color, roughness = 0.6f, emissive = color, emissiveIntensity = 0.9f))
}

private fun hoiAnHouse(x: Float, z: Float, wall: Int): Node {
    val g = Node()
    g.add(box(0.46f, 0.34f, 0.38f, mat(wall, roughness = 0.95f)))
    g.add(gableRoof(0.54f, 0.18f, 0.46f, mat(0x6B3A26), 0f, 0.34f, 0f))
    g.add(box(0.12f, 0.2f, 0.02f, mat(0x4A2A15), 0f, 0f, 0.195f))
    g.add(box(0.1f, 0.08f, 0.02f, mat(0x3A5A3A), -0.15f, 0.17f, 0.195f))
    g.add(box(0.1f, 0.08f, 0.02f, mat(0x3A5A3A), 0.15f, 0.17f, 0.195f))
    g.position.set(x, 0.04f, z)
    return g
}

private fun hoiAn(): Node {
    val g = Node()
    g.add(box(1.75f, 0.04f, 0.9f, mat(0xC9B48A), 0f, 0f, -0.32f))
    g.add(water(1.75f, 0.65f, 0f, 0.45f, 0.02f, 0x4D8A78))
    g.add(hoiAnHouse(-0.5f, -0.45f, 0xF2C94C), hoiAnHouse(0.02f, -0.5f, 0xE8B93A), hoiAnHouse(0.55f, -0.42f, 0xF0CF6A))
    val bridge = Node()
    bridge.add(box(0.5f, 0.06f, 0.2f, mat(0x9A5A3A), 0f, 0.08f, 0f))
    bridge.add(box(0.46f, 0.14f, 0.18f, mat(0xC0603F), 0f, 0.14f, 0f))
    bridge.add(gableRoof(0.56f, 0.12f, 0.26f, mat(0x6B3A26), 0f, 0.28f, 0f))
    bridge.position.set(0.5f, 0f, 0.25f)
    bridge.rotation.y = PI_F / 2
    g.add(bridge)
    val colors = intArrayOf(0xFF3B2F, 0xFFB21A, 0xFF6A1A, 0xD61F5A, 0xFFD23A)
    val lanterns = (0 until 9).map { i ->
        lanternMesh(colors[i % colors.size]).dyn().also {
            it.position.set(-0.75f + i * 0.16f, 0.5f - sin(i / 8f * PI_F) * 0.08f, -0.18f)
            g.add(it)
        }
    }
    val b = boat(0x3A2A1A, 0.42f).dyn()
    b.add(lanternMesh(0xFF3B2F).setPos(0f, 0.2f, 0f))
    b.position.set(-0.35f, 0.02f, 0.45f)
    g.add(b)
    g.tick = { t, _ ->
        lanterns.forEachIndexed { i, l -> l.material.emissiveIntensity = 0.7f + sin(t * 3 + i) * 0.3f }
        b.position.x = -0.35f + sin(t * 0.3f) * 0.35f
    }
    return g
}

private fun hueCitadel(): Node {
    val g = Node()
    g.add(water(1.75f, 0.35f, 0f, 0.6f, 0.02f, 0x4D8A78))
    g.add(box(1.75f, 0.04f, 1.2f, mat(C.grass), 0f, 0f, -0.18f))
    val stone = mat(0x77736A, roughness = 0.95f)
    g.add(box(1.4f, 0.36f, 0.5f, stone, 0f, 0.04f, -0.2f))
    g.add(box(0.3f, 0.36f, 0.4f, stone, -0.55f, 0.04f, 0.2f))
    g.add(box(0.3f, 0.36f, 0.4f, stone, 0.55f, 0.04f, 0.2f))
    for (x in floatArrayOf(-0.25f, 0f, 0.25f)) g.add(box(0.13f, 0.22f, 0.02f, mat(0x221A14), x, 0.04f, 0.055f))
    g.add(box(1.0f, 0.04f, 0.4f, mat(0xB8A98A), 0f, 0.4f, -0.2f))
    for (x in floatArrayOf(-0.4f, -0.2f, 0f, 0.2f, 0.4f)) {
        g.add(cyl(0.018f, 0.018f, 0.2f, mat(C.red), x, 0.44f, -0.05f, 6))
        g.add(cyl(0.018f, 0.018f, 0.2f, mat(C.red), x, 0.44f, -0.35f, 6))
    }
    val yellowTile = mat(0xE8B82A, roughness = 0.5f)
    g.add(hipRoof(1.1f, 0.16f, 0.5f, yellowTile, 0f, 0.62f, -0.2f))
    g.add(box(0.5f, 0.12f, 0.25f, mat(0xA0302A), 0f, 0.7f, -0.2f))
    g.add(hipRoof(0.65f, 0.16f, 0.36f, yellowTile, 0f, 0.82f, -0.2f))
    for (x in floatArrayOf(-0.55f, 0.55f)) g.add(hipRoof(0.28f, 0.12f, 0.28f, yellowTile, x, 0.4f, 0.2f))
    return g
}

// ---------------------------------------------------------------- Đà Nẵng – Quảng Bình

private fun dragonBridge(): Node {
    val g = Node()
    g.add(water(1.75f, 1.55f, 0f, 0f, 0.02f, 0x3A8FB8))
    g.add(box(1.75f, 0.05f, 0.28f, mat(0x9A9A9A), 0f, 0.24f, 0f))
    for (x in floatArrayOf(-0.6f, 0f, 0.6f)) g.add(box(0.08f, 0.24f, 0.2f, mat(0x8A8A8A), x, 0f, 0f))
    val pts = (0..24).map { i -> Vec3(-0.85f + i / 24f * 1.5f, 0.32f + abs(sin(i / 24f * PI_F * 2.5f)) * 0.3f, 0f) }
    val gold = mat(0xF0B72A, metalness = 0.6f, roughness = 0.35f)
    g.add(mesh(Geo.tube(pts, 80, 0.045f, 8), gold))
    val end = pts.last()
    val head = Node()
    head.add(box(0.16f, 0.1f, 0.1f, gold, 0f, -0.05f, 0f))
    val snout = cone(0.05f, 0.1f, gold, 0.11f, -0.05f, 0f, 6)
    snout.rotation.z = -PI_F / 2
    head.add(snout)
    head.add(sphere(0.015f, mat(C.red, emissive = 0xFF0000), 0.05f, 0.03f, 0.05f, 6, 4))
    head.add(sphere(0.015f, mat(C.red, emissive = 0xFF0000), 0.05f, 0.03f, -0.05f, 6, 4))
    for (z in floatArrayOf(-0.035f, 0.035f)) {
        val horn = cone(0.015f, 0.09f, gold, -0.04f, 0.03f, z, 5)
        horn.rotation.z = 0.7f
        head.add(horn)
    }
    head.position.set(end.x + 0.06f, end.y + 0.02f, end.z)
    g.add(head)
    val fire = Mesh(Geo.cone(0.06f, 0.3f, 8), Material(0xFF7A1A, emissive = 0xFF5A00, emissiveIntensity = 1.5f, opacity = 0.85f)).dyn()
    fire.rotation.z = PI_F / 2
    fire.position.set(0.3f, -0.05f, 0f)
    fire.visible = false
    head.add(fire)
    g.tick = { t, _ ->
        val on = t % 7f > 5.5f
        fire.visible = on
        if (on) fire.scale.set(1f, 0.8f + Random.nextFloat() * 0.5f, 1f)
    }
    return g
}

private fun hand(side: Float): Node {
    val g = Node()
    val stoneMoss = mat(0x7D8073, flat = true, roughness = 1f)
    g.add(cyl(0.07f, 0.1f, 0.55f, stoneMoss, seg = 7))
    g.add(box(0.2f, 0.06f, 0.24f, stoneMoss, 0f, 0.55f, 0f))
    for (i in 0 until 4) {
        val f = cyl(0.022f, 0.026f, 0.2f, stoneMoss, -0.075f + i * 0.05f, 0.58f, 0.1f, 6)
        f.rotation.x = 0.5f
        g.add(f)
    }
    val thumb = cyl(0.025f, 0.03f, 0.14f, stoneMoss, side * 0.12f, 0.57f, -0.02f, 6)
    thumb.rotation.z = -side * 0.8f
    g.add(thumb)
    return g
}

private fun goldenBridge(): Node {
    val g = Node()
    val hill = sphere(0.9f, mat(0x3F7F3A, flat = true), 0f, -0.2f, -0.45f, 10, 8)
    hill.scale.set(1f, 1f, 0.55f)
    g.add(hill)
    g.add(ground(0x4F8F3F))
    val deck = mesh(Geo.torus(0.62f, 0.05f, 6, 32, PI_F), mat(0xE8B52A, metalness = 0.7f, roughness = 0.3f), 0f, 0.74f, -0.1f)
    deck.rotation.x = PI_F / 2
    deck.scale.set(1f, 1f, 0.45f)
    g.add(deck)
    val rail = mesh(Geo.torus(0.66f, 0.008f, 4, 32, PI_F), mat(0xD4A020, metalness = 0.7f), 0f, 0.84f, -0.1f)
    rail.rotation.x = PI_F / 2
    g.add(rail)
    g.add(hand(-1f).setPos(-0.2f, 0f, 0.42f).also { it.rotation.y = 0.3f })
    g.add(hand(1f).setPos(0.2f, 0f, 0.42f).also { it.rotation.y = -0.3f })
    g.add(tree(-0.75f, 0.55f, 1.3f, 0x2E6B2E), tree(0.78f, 0.5f, 1.1f, 0x2E6B2E))
    return g
}

private fun cave(): Node {
    val g = Node()
    g.add(ground(0x4A6A3A))
    val rock = mat(0x5D5A52, flat = true, roughness = 1f)
    val dome = mesh(Geo.sphere(0.85f, 9, 6, 0f, PI_F * 2, 0f, PI_F / 2), rock, 0f, 0.02f, -0.25f)
    dome.scale.set(1f, 0.95f, 0.7f)
    g.add(dome)
    g.add(mesh(Geo.torus(0.42f, 0.13f, 5, 12, PI_F), rock, 0f, 0.02f, 0.28f))
    g.add(mesh(Geo.circle(0.42f, 16, 0f, PI_F), mat(0x15120F), 0f, 0.03f, 0.22f))
    g.add(tree(-0.15f, 0.35f, 1.0f, 0x3F9A3F), tree(0.15f, 0.4f, 0.8f, 0x3F9A3F))
    val beamMat = basic(0xFFF2B0, opacity = 0.22f, depthWrite = false)
    val beam = Mesh(Geo.cylinder(0.06f, 0.22f, 0.9f, 12, 1, true), beamMat).dyn()
    beamMat.side = Side.DOUBLE
    beam.position.set(0.1f, 0.5f, 0.15f)
    g.add(beam)
    g.add(cone(0.07f, 0.08f, mat(0xFF7A1A), 0.32f, 0.03f, 0.52f, 3))
    g.tick = { t, _ -> beamMat.opacity = 0.16f + sin(t * 1.5f) * 0.06f }
    return g
}

// ---------------------------------------------------------------- Thăng Long

private fun vanMieu(): Node {
    val g = Node()
    g.add(ground(0x6AA04C))
    g.add(box(0.9f, 0.05f, 0.42f, mat(C.stone), 0f, 0.02f, 0.48f))
    g.add(water(0.8f, 0.34f, 0f, 0.48f, 0.06f, 0x4D8A9A))
    val plaster = mat(0xE9E2D0)
    for ((x, z) in listOf(-0.2f to -0.38f, 0.2f to -0.38f, -0.2f to 0.02f, 0.2f to 0.02f)) g.add(box(0.12f, 0.38f, 0.12f, plaster, x, 0.04f, z))
    g.add(box(0.6f, 0.04f, 0.6f, plaster, 0f, 0.42f, -0.18f))
    g.add(pagodaRoof(0.95f, 0.14f, mat(0x5A3A2A), 0f, 0.4f, -0.18f))
    g.add(box(0.36f, 0.26f, 0.36f, mat(0x9A2F20), 0f, 0.5f, -0.18f))
    val sunMat = mat(0xF2E3B0, emissive = 0x3A3010)
    for (front in listOf(true, false)) {
        val sun = mesh(Geo.circle(0.09f, 16), sunMat, 0f, 0.63f, -0.18f + if (front) 0.185f else -0.185f)
        if (!front) sun.rotation.y = PI_F
        g.add(sun)
    }
    g.add(pagodaRoof(0.7f, 0.28f, mat(0x4A3028), 0f, 0.76f, -0.18f))
    g.add(cone(0.025f, 0.1f, mat(C.gold, metalness = 0.6f), 0f, 1.03f, -0.18f, 6))
    g.add(tree(-0.7f, -0.4f, 1.4f), tree(0.7f, -0.4f, 1.4f), tree(-0.7f, 0.2f, 1.1f), tree(0.7f, 0.2f, 1.1f))
    return g
}

private fun onePillar(): Node {
    val g = Node()
    g.add(ground(0x6AA04C))
    g.add(box(1.3f, 0.06f, 1.1f, mat(0xB8B0A0), 0f, 0.02f, 0f))
    g.add(water(1.16f, 0.96f, 0f, 0f, 0.06f, 0x3F8F8A))
    g.add(cyl(0.065f, 0.075f, 0.5f, mat(0xB0A898), 0f, 0.06f, 0f, 10))
    g.add(box(0.5f, 0.04f, 0.5f, mat(0x6B3A22), 0f, 0.54f, 0f))
    for (k in 0 until 4) {
        val a = k * PI_F / 2
        val brace = box(0.03f, 0.2f, 0.03f, mat(0x6B3A22), 0f, 0.38f, 0f)
        brace.position.x = cos(a) * 0.1f
        brace.position.z = sin(a) * 0.1f
        brace.rotation.z = -cos(a) * 0.6f
        brace.rotation.x = sin(a) * 0.6f
        g.add(brace)
    }
    g.add(box(0.34f, 0.22f, 0.34f, mat(0x8A2F1F), 0f, 0.58f, 0f))
    g.add(pagodaRoof(0.7f, 0.26f, mat(0x3E3430), 0f, 0.8f, 0f))
    g.add(sphere(0.03f, mat(C.gold, metalness = 0.6f), 0f, 1.08f, 0f, 8, 6))
    val stairs = box(0.16f, 0.03f, 0.55f, mat(0x8A7A6A), 0f, 0.3f, 0.42f)
    stairs.rotation.x = 0.85f
    g.add(stairs)
    val r = rand(13)
    for (i in 0 until 7) {
        val a = r() * PI_F * 2
        val rr = 0.3f + r() * 0.2f
        g.add(cyl(0.06f, 0.06f, 0.012f, mat(0x3F8F3A), cos(a) * rr, 0.08f, sin(a) * rr * 0.8f, 10))
        if (i % 2 == 0) g.add(cone(0.025f, 0.05f, mat(0xF28CB0), cos(a) * rr, 0.09f, sin(a) * rr * 0.8f, 6))
    }
    return g
}

private fun turtleTower(): Node {
    val g = Node()
    g.add(water(1.75f, 1.55f, 0f, 0f, 0.02f, 0x4F9A86))
    val islet = cyl(0.42f, 0.46f, 0.06f, mat(0x5A9A46), 0f, 0.02f, -0.1f, 18)
    islet.scale.z = 0.75f
    g.add(islet)
    val stone = mat(0xD9D2BF, roughness = 0.9f)
    val dark = mat(0x3A3530)
    g.add(box(0.34f, 0.22f, 0.26f, stone, 0f, 0.08f, -0.1f))
    g.add(box(0.26f, 0.15f, 0.2f, stone, 0f, 0.3f, -0.1f))
    g.add(box(0.18f, 0.12f, 0.14f, stone, 0f, 0.45f, -0.1f))
    g.add(sphere(0.06f, stone, 0f, 0.6f, -0.1f, 10, 8))
    for (x in floatArrayOf(-0.09f, 0f, 0.09f)) g.add(box(0.05f, 0.1f, 0.01f, dark, x, 0.1f, 0.035f))
    for (x in floatArrayOf(-0.06f, 0.06f)) g.add(box(0.04f, 0.07f, 0.01f, dark, x, 0.33f, 0.005f))
    g.add(box(0.04f, 0.06f, 0.01f, dark, 0f, 0.47f, -0.025f))
    g.add(tree(0.28f, -0.25f, 0.9f, 0x3F8F3A, 0.06f), tree(-0.3f, -0.2f, 0.8f, 0x3F8F3A, 0.06f))
    val bridge = mesh(Geo.torus(0.45f, 0.04f, 6, 24, PI_F), mat(0xC8202A), -0.62f, 0.02f, 0.15f)
    bridge.scale.set(1f, 0.35f, 1.5f)
    bridge.rotation.y = PI_F / 2
    g.add(bridge)
    g.add(box(0.15f, 0.12f, 0.15f, mat(0xC8202A), -0.62f, 0.02f, -0.55f))
    g.add(hipRoof(0.22f, 0.08f, 0.22f, mat(0x3E3430), -0.62f, 0.14f, -0.55f))
    return g
}

// ---------------------------------------------------------------- Biểu tượng quốc gia

private fun skyscraper(): Node {
    val g = Node()
    g.add(box(1.75f, 0.04f, 0.6f, mat(0x3A8FB8, roughness = 0.2f), 0f, 0f, 0.47f))
    g.add(box(1.75f, 0.05f, 1.0f, mat(0xA0A4A8), 0f, 0f, -0.3f))
    val rnd = Random(81)
    val winTex = canvasTexture(64, 256) { c, w, h ->
        c.drawColor(0xFF1D3550.toInt())
        val lit = fill(0xFFE7A8); val off = fill(0x3D6688)
        var y = 2
        while (y < h) {
            var x = 2
            while (x < w) { c.drawRect(x.toFloat(), y.toFloat(), x + 6f, y + 3f, if (rnd.nextFloat() > 0.55f) lit else off); x += 8 }
            y += 6
        }
    }
    val glass = Material(0x9CC4E4, metalness = 0.55f, roughness = 0.15f, emissive = 0xFFFFFF, emissiveIntensity = 0.35f, emissiveMap = winTex)
    val parts = listOf(
        floatArrayOf(0f, 0f, 2.6f), floatArrayOf(0.17f, 0f, 2.25f), floatArrayOf(-0.17f, 0f, 2.05f), floatArrayOf(0f, 0.17f, 1.85f),
        floatArrayOf(0f, -0.17f, 2.4f), floatArrayOf(0.17f, 0.17f, 1.55f), floatArrayOf(-0.17f, -0.17f, 1.7f),
        floatArrayOf(0.17f, -0.17f, 1.95f), floatArrayOf(-0.17f, 0.17f, 1.35f),
    )
    for ((x, z, h) in parts.map { Triple(it[0], it[1], it[2]) }) g.add(mesh(Geo.box(0.17f, h, 0.17f), glass, x, 0.05f + h / 2, z - 0.3f))
    g.add(cyl(0.006f, 0.012f, 0.25f, mat(0xDDDDDD, metalness = 0.8f), 0f, 2.65f, -0.3f, 6))
    val beacon = sphere(0.02f, basic(0xFF2020), 0f, 2.92f, -0.3f, 6, 4).dyn()
    g.add(beacon)
    g.add(box(0.5f, 0.18f, 0.4f, mat(0xCFD6DC), -0.6f, 0.05f, -0.45f), box(0.4f, 0.28f, 0.35f, mat(0xBFC6CC), 0.6f, 0.05f, -0.5f))
    g.add(palm(-0.55f, 0.05f, 0.8f), palm(0.55f, 0.05f, 0.8f))
    g.tick = { t, _ -> beacon.visible = sin(t * 4) > 0 }
    return g
}

/** Núi đá vôi kiểu Hạ Long (dùng cả cho quần đảo quanh bàn cờ). */
fun karst(x: Float, z: Float, h: Float, r: Float, seed: Int): Node {
    val rr = rand(seed)
    val geo = Geo.cylinder(r * 0.55f, r, h, 7, 3)
    for (i in 0 until geo.vertexCount) {
        geo.positions[i * 3] *= 0.85f + rr() * 0.3f
        geo.positions[i * 3 + 2] *= 0.85f + rr() * 0.3f
    }
    geo.computeVertexNormals()
    val g = Node()
    g.add(mesh(geo, mat(0x5F7F4A, flat = true, roughness = 1f), x, h / 2, z))
    val top = sphere(r * 0.6f, mat(0x4F7A3A, flat = true), x, h, z, 7, 4)
    top.scale.y = 0.5f
    g.add(top)
    g.add(cyl(r * 1.05f, r * 1.1f, 0.06f, mat(0x8A8670, flat = true), x, 0f, z, 7))
    return g
}

fun junkBoat(): Node {
    val b = boat(0x5A3418, 0.5f)
    val sailMat = mat(0xC8552A, side = Side.DOUBLE, roughness = 0.9f)
    for ((x, h) in listOf(-0.14f to 0.32f, 0f to 0.4f, 0.14f to 0.28f)) {
        b.add(cyl(0.007f, 0.007f, h + 0.05f, mat(0x3A2410), x, 0.1f, 0f, 4))
        b.add(mesh(Geo.plane(0.13f, h), sailMat, x + 0.06f, 0.12f + h / 2, 0f))
    }
    return b
}

private fun halong(): Node {
    val g = Node()
    g.add(water(1.75f, 1.55f, 0f, 0f, 0.02f, 0x2F9A9A))
    g.add(karst(-0.62f, -0.45f, 0.85f, 0.22f, 1), karst(0.55f, -0.5f, 1.05f, 0.25f, 2), karst(-0.05f, -0.62f, 0.6f, 0.18f, 3),
        karst(0.72f, 0.35f, 0.5f, 0.15f, 4), karst(-0.72f, 0.38f, 0.65f, 0.16f, 5))
    val b = junkBoat().dyn()
    g.add(b)
    g.tick = { t, _ ->
        val a = t * 0.25f
        b.position.set(cos(a) * 0.38f, 0.03f + sin(t * 1.4f) * 0.01f, 0.05f + sin(a) * 0.28f)
        b.rotation.y = -a - PI_F / 2
    }
    return g
}

// ---------------------------------------------------------------- Ô đặc biệt

private fun goGate(): Node {
    val g = Node()
    val red = mat(0xC62828)
    g.add(box(1.5f, 0.04f, 1.5f, mat(0xE8D9B5)))
    for (x in floatArrayOf(-0.5f, 0.5f)) {
        g.add(cyl(0.07f, 0.08f, 0.9f, red, x, 0.04f, 0f, 10))
        g.add(box(0.2f, 0.06f, 0.2f, mat(C.gold, metalness = 0.6f), x, 0.04f, 0f))
    }
    g.add(box(1.3f, 0.1f, 0.12f, red, 0f, 0.92f, 0f))
    g.add(box(1.0f, 0.18f, 0.04f, mat(C.gold, metalness = 0.5f, roughness = 0.3f), 0f, 0.72f, 0.03f))
    g.add(hipRoof(1.4f, 0.15f, 0.3f, mat(0x3E3430), 0f, 1.02f, 0f))
    for (x in floatArrayOf(-0.5f, 0.5f)) g.add(lanternMesh(0xFF3B2F).setPos(x * 0.6f, 0.6f, 0.08f))
    for (i in 0 until 5) g.add(cyl(0.12f, 0.12f, 0.04f, mat(C.gold, metalness = 0.7f, roughness = 0.3f), 0.45f, 0.04f + i * 0.045f, 0.5f, 16))
    return g
}

private fun envelope(): Node {
    val g = Node()
    val tex = canvasTexture(128, 180) { c, w, h ->
        c.drawColor(0xFFC8102E.toInt())
        c.drawRect(8f, 8f, w - 8f, h - 8f, stroke(0xF5C542, 6f))
        c.drawCircle(w / 2f, h * 0.55f, 34f, fill(0xF5C542))
        c.drawTextMiddle("LỘC", w / 2f, h * 0.56f, textPaint(0xC8102E, 30f, 700))
    }
    val red = mat(0xC8102E)
    val face = Material(map = tex, roughness = 0.5f)
    val env = Mesh(Geo.box(0.42f, 0.6f, 0.04f), listOf(red, red, red, red, face, face)).dyn()
    env.position.y = 0.45f
    g.add(env)
    g.tick = { t, _ ->
        env.rotation.y = sin(t * 0.8f) * 0.6f
        env.position.y = 0.45f + sin(t * 1.6f) * 0.04f
    }
    return g
}

private fun question(): Node {
    val g = Node()
    val m = mat(0xFF8F1F, roughness = 0.3f, metalness = 0.2f)
    val q = Node().dyn()
    val hook = Mesh(Geo.torus(0.16f, 0.05f, 10, 20, PI_F * 1.4f), m)
    hook.rotation.z = -PI_F * 0.4f
    hook.position.y = 0.32f
    val stem = Mesh(Geo.cylinder(0.05f, 0.05f, 0.14f, 10), m).setPos(0f, 0.12f, 0f)
    val dot = Mesh(Geo.sphere(0.06f, 12, 10), m).setPos(0f, -0.06f, 0f)
    q.add(hook, stem, dot)
    q.position.y = 0.42f
    g.add(q)
    g.tick = { t, _ ->
        q.rotation.y = t * 1.2f
        q.position.y = 0.42f + sin(t * 2) * 0.04f
    }
    return g
}

private fun coins(): Node {
    val g = Node()
    val gold = mat(C.gold, metalness = 0.75f, roughness = 0.3f)
    for ((x, z, n) in listOf(Triple(-0.25f, 0f, 7), Triple(0.1f, -0.15f, 5), Triple(0.3f, 0.2f, 3))) {
        for (i in 0 until n) g.add(cyl(0.13f, 0.13f, 0.045f, gold, x, i * 0.05f, z, 18))
    }
    val bill = box(0.42f, 0.02f, 0.22f, mat(0x7FBF7F), -0.1f, 0f, 0.35f)
    bill.rotation.y = 0.3f
    g.add(bill)
    return g
}

private fun rails(len: Float): Node {
    val g = Node()
    var x = -len / 2
    while (x <= len / 2 + 1e-4f) { g.add(box(0.05f, 0.02f, 0.3f, mat(0x6B5030), x, 0.04f, 0f)); x += 0.12f }
    for (z in floatArrayOf(-0.09f, 0.09f)) g.add(box(len, 0.025f, 0.02f, mat(0x888888, metalness = 0.7f), 0f, 0.06f, z))
    return g
}

private fun train(): Node {
    val g = Node()
    g.add(box(1.75f, 0.04f, 1.55f, mat(0xB8B0A0)))
    g.add(rails(1.7f).also { it.position.z = 0.15f })
    val loco = Node().dyn()
    loco.add(box(0.55f, 0.24f, 0.24f, mat(0x1D4E89), 0f, 0.12f, 0f))
    loco.add(box(0.55f, 0.04f, 0.245f, mat(0xF2C94C), 0f, 0.2f, 0f))
    loco.add(box(0.18f, 0.1f, 0.22f, mat(0x1D4E89), -0.15f, 0.36f, 0f))
    loco.add(box(0.06f, 0.08f, 0.2f, mat(0xBFE3FF, emissive = 0x203040), 0.28f, 0.25f, 0f))
    for (x in floatArrayOf(-0.18f, 0f, 0.18f)) for (z in floatArrayOf(-0.11f, 0.11f)) {
        val w = cyl(0.05f, 0.05f, 0.03f, mat(C.black), x, 0f, z, 10)
        w.rotation.x = PI_F / 2
        w.position.y = 0.11f
        loco.add(w)
    }
    val car = Node()
    car.add(box(0.6f, 0.26f, 0.24f, mat(0x2D6FB5), 0f, 0.12f, 0f))
    for (i in 0 until 4) car.add(box(0.08f, 0.06f, 0.25f, mat(0xBFE3FF, emissive = 0x203040), -0.21f + i * 0.14f, 0.25f, 0f))
    car.position.x = -0.62f
    loco.add(car)
    loco.position.set(0.25f, 0.04f, 0.15f)
    g.add(loco)
    g.add(box(1.2f, 0.35f, 0.35f, mat(0xE9D8B0), 0f, 0.04f, -0.5f))
    g.add(hipRoof(1.3f, 0.14f, 0.42f, mat(0x8A3B24), 0f, 0.39f, -0.5f))
    g.add(box(0.2f, 0.1f, 0.02f, mat(0x355F8A), 0f, 0.28f, -0.32f))
    g.tick = { t, _ -> loco.position.x = 0.25f + sin(t * 0.3f) * 0.2f }
    return g
}

private fun containerShip(): Node {
    val g = Node()
    g.add(water(1.75f, 0.9f, 0f, 0.3f, 0.02f, 0x2F7FA8))
    g.add(box(1.75f, 0.18f, 0.6f, mat(0x8A8A8A), 0f, 0f, -0.47f))
    val hull = Node().dyn()
    hull.add(box(1.2f, 0.16f, 0.3f, mat(0x7A1F1F)))
    val bow = cone(0.15f, 0.25f, mat(0x7A1F1F), 0.72f, 0.08f, 0f, 4)
    bow.rotation.z = -PI_F / 2
    bow.position.y = 0.08f
    bow.scale.set(1f, 1f, 0.75f)
    hull.add(bow)
    hull.add(box(0.18f, 0.22f, 0.24f, mat(0xF2F2F2), -0.5f, 0.16f, 0f))
    val cc = intArrayOf(0xD63A2A, 0x2A6FD6, 0x2A9A4A, 0xF2A21A, 0x8A4AD6, 0xE8E8E8)
    var k = 0
    var x = -0.3f
    while (x <= 0.5f + 1e-4f) {
        for (y in 0 until 2) for (z in floatArrayOf(-0.07f, 0.07f)) hull.add(box(0.15f, 0.08f, 0.12f, mat(cc[k++ % cc.size]), x, 0.16f + y * 0.085f, z))
        x += 0.17f
    }
    hull.position.set(-0.05f, 0.03f, 0.3f)
    g.add(hull)
    val yellow = mat(0xF2B51A)
    for (cx in floatArrayOf(-0.4f, 0.4f)) {
        g.add(box(0.05f, 0.75f, 0.05f, yellow, cx, 0.18f, -0.55f), box(0.05f, 0.75f, 0.05f, yellow, cx, 0.18f, -0.35f))
        g.add(box(0.06f, 0.06f, 0.9f, yellow, cx, 0.93f, -0.2f))
    }
    g.tick = { t, _ ->
        hull.position.y = 0.03f + sin(t * 1.2f) * 0.01f
        hull.rotation.x = sin(t) * 0.02f
    }
    return g
}

private fun airplane(tailColor: Int, stripe: Int): Node {
    val g = Node()
    val white = mat(0xF4F4F4, roughness = 0.4f)
    val body = Mesh(Geo.capsule(0.075f, 0.85f, 6, 12), white)
    body.rotation.z = PI_F / 2
    g.add(body)
    g.add(box(0.95f, 0.025f, 0.155f, mat(stripe)))
    g.add(box(0.24f, 0.015f, 1.05f, white, 0f, -0.02f, 0f))
    g.add(box(0.12f, 0.012f, 0.36f, white, -0.42f, 0.02f, 0f))
    g.add(box(0.16f, 0.2f, 0.015f, mat(tailColor), -0.44f, 0.07f, 0f))
    for (z in floatArrayOf(-0.22f, 0.22f)) {
        val eng = cyl(0.035f, 0.035f, 0.12f, mat(0xCCCCCC), 0.04f, -0.08f, z, 10)
        eng.rotation.z = PI_F / 2
        eng.position.y = -0.06f
        g.add(eng)
    }
    return g
}

private fun plane(tail: Int, stripe: Int): Node {
    val g = Node()
    val runway = canvasTexture(256, 128) { c, w, h ->
        c.drawColor(0xFF4A4D52.toInt())
        val p = fill(0xFFFFFF)
        var x = 10
        while (x < w) { c.drawRect(x.toFloat(), h / 2f - 3, x + 22f, h / 2f + 3, p); x += 40 }
    }
    g.add(box(1.75f, 0.04f, 1.55f, mat(0x7AAA5A)))
    val rw = Mesh(Geo.box(1.75f, 0.02f, 0.55f), Material(map = runway, roughness = 0.9f))
    rw.position.set(0f, 0.05f, 0.25f)
    rw.castShadow = false
    g.add(rw)
    val ap = airplane(tail, stripe).dyn()
    ap.position.set(0f, 0.22f, 0.25f)
    ap.rotation.y = 0.15f
    g.add(ap)
    g.add(cyl(0.07f, 0.09f, 0.7f, mat(0xDDDDDD), -0.65f, 0.04f, -0.5f, 10))
    g.add(cyl(0.14f, 0.1f, 0.14f, mat(0x6FA8D8, metalness = 0.4f, roughness = 0.2f), -0.65f, 0.74f, -0.5f, 10))
    g.add(box(0.7f, 0.2f, 0.3f, mat(0xD8DDE2), 0.35f, 0.04f, -0.5f))
    g.tick = { t, _ ->
        val lift = max(0f, sin(t * 0.5f))
        ap.position.y = 0.22f + lift * 0.25f
        ap.rotation.z = lift * 0.12f
    }
    return g
}

private fun dam(): Node {
    val g = Node()
    g.add(box(1.75f, 0.45f, 0.6f, mat(0x5F7F4A, flat = true), 0f, 0f, -0.48f))
    g.add(water(1.75f, 0.6f, 0f, -0.48f, 0.45f, 0x2F7FA8))
    g.add(box(1.75f, 0.5f, 0.14f, mat(0xB8B4AA), 0f, 0f, -0.12f))
    g.add(water(1.75f, 0.95f, 0f, 0.32f, 0.02f, 0x3A8FB8))
    val streams = floatArrayOf(-0.45f, -0.15f, 0.15f, 0.45f).map { x ->
        g.add(box(0.14f, 0.12f, 0.02f, mat(0x333333), x, 0.28f, -0.04f))
        val s = Mesh(Geo.plane(0.12f, 0.32f), Material(0xE8F6FF, opacity = 0.8f)).dyn()
        s.position.set(x, 0.18f, 0f)
        s.rotation.x = -0.35f
        g.add(s)
        s
    }
    val steel = mat(0x9A9A9A, metalness = 0.6f)
    for (x in floatArrayOf(-0.65f, 0.65f)) {
        for (dx in floatArrayOf(-0.05f, 0.05f)) {
            val leg = box(0.018f, 0.85f, 0.018f, steel, x + dx, 0.04f, 0.6f)
            leg.rotation.z = -dx * 1.2f
            g.add(leg)
        }
        g.add(box(0.34f, 0.02f, 0.02f, steel, x, 0.72f, 0.6f), box(0.24f, 0.02f, 0.02f, steel, x, 0.55f, 0.6f))
    }
    g.add(box(1.3f, 0.004f, 0.004f, mat(0x333333), 0f, 0.73f, 0.6f))
    g.tick = { t, _ -> streams.forEachIndexed { i, s -> s.material.opacity = 0.6f + sin(t * 8 + i) * 0.2f } }
    return g
}

private fun turbine(): Pair<Node, Node> {
    val g = Node()
    val white = mat(0xF4F4F4, roughness = 0.4f)
    g.add(cyl(0.018f, 0.035f, 1.1f, white, seg = 10))
    g.add(box(0.08f, 0.07f, 0.16f, white, 0f, 1.08f, -0.02f))
    val rotor = Node().dyn()
    rotor.add(sphere(0.035f, white, 0f, 0f, 0f, 8, 6))
    for (k in 0 until 3) {
        val pivot = Node()
        pivot.add(box(0.035f, 0.42f, 0.012f, white))
        pivot.rotation.z = k * PI_F * 2 / 3
        rotor.add(pivot)
    }
    rotor.position.set(0f, 1.115f, 0.08f)
    g.add(rotor)
    return g to rotor
}

private fun windTurbine(): Node {
    val g = Node()
    g.add(water(1.75f, 1.55f, 0f, 0f, 0.02f, 0x5A9AB0))
    val rotors = listOf(Triple(-0.4f, -0.35f, 1f), Triple(0.35f, -0.1f, 0.9f), Triple(-0.05f, 0.4f, 0.75f)).map { (x, z, s) ->
        val (t, rotor) = turbine()
        t.position.set(x, 0f, z)
        t.scale.setScalar(s)
        g.add(t)
        rotor
    }
    g.add(box(1.2f, 0.03f, 0.06f, mat(0x8A8A8A), 0f, 0.12f, 0.6f))
    g.tick = { _, dt -> rotors.forEachIndexed { i, r -> r.rotation.z -= dt * (1.6f + i * 0.3f) } }
    return g
}

private fun jail(): Node {
    val g = Node()
    g.add(box(2.4f, 0.04f, 2.4f, mat(0xD8D0BF)))
    val wall = mat(0x8F9AA3)
    g.add(box(0.8f, 0.6f, 0.8f, wall, -0.75f, 0.04f, -0.75f))
    g.add(box(0.9f, 0.06f, 0.9f, mat(0x5A646C), -0.75f, 0.64f, -0.75f))
    g.add(box(0.14f, 0.24f, 0.02f, mat(0x333A40), -0.75f, 0.04f, -0.34f))
    g.add(box(1.5f, 0.01f, 1.5f, mat(0xA8A090), -0.4f, 0.04f, -0.4f))
    val steel = mat(0x333333, metalness = 0.8f, roughness = 0.4f)
    for (i in 0..10) {
        val v = -1.15f + i * 0.135f
        g.add(cyl(0.015f, 0.015f, 0.45f, steel, v, 0.05f, 0.35f, 6))
        g.add(cyl(0.015f, 0.015f, 0.45f, steel, 0.35f, 0.05f, v, 6))
    }
    g.add(box(1.5f, 0.03f, 0.03f, steel, -0.4f, 0.5f, 0.35f), box(0.03f, 0.03f, 1.5f, steel, 0.35f, 0.5f, -0.4f))
    val red = sphere(0.05f, basic(0xFF2A2A), -0.9f, 0.75f, -0.75f, 8, 6).dyn()
    val blue = sphere(0.05f, basic(0x2A6AFF), -0.6f, 0.75f, -0.75f, 8, 6).dyn()
    g.add(red, blue)
    g.tick = { t, _ ->
        val on = sin(t * 6) > 0
        red.visible = on
        blue.visible = !on
    }
    return g
}

private fun pier(): Node {
    val g = Node()
    g.add(water(2.4f, 2.4f, 0f, 0f, 0.02f, 0x7A8A5A))
    g.add(box(2.4f, 0.1f, 0.9f, mat(0xC9BFA8), 0f, 0f, -0.75f))
    g.add(box(0.5f, 0.05f, 1.3f, mat(0x9A7A4A), 0.3f, 0.14f, 0.3f))
    for (z in floatArrayOf(-0.2f, 0.3f, 0.8f)) for (x in floatArrayOf(0.1f, 0.5f)) g.add(cyl(0.025f, 0.025f, 0.16f, mat(0x5A4020), x, 0f, z, 6))
    for (x in floatArrayOf(-0.8f, -0.2f, 0.6f)) {
        g.add(cyl(0.012f, 0.015f, 0.55f, mat(0x2A2A2A), x, 0.1f, -0.4f, 6))
        g.add(sphere(0.045f, mat(0xFFF0B0, emissive = 0xFFD060), x, 0.67f, -0.4f, 10, 8))
    }
    val b = boat(0x2A5A7A, 0.6f).dyn()
    b.add(box(0.22f, 0.08f, 0.14f, mat(0xC0402A), -0.05f, 0.12f, 0f))
    b.position.set(-0.4f, 0.02f, 0.5f)
    g.add(b)
    g.add(palm(-0.9f, -0.85f, 1.3f), palm(0.95f, -0.8f, 1.1f))
    g.tick = { t, _ -> b.position.y = 0.02f + sin(t * 1.5f) * 0.015f }
    return g
}

private fun whistle(): Node {
    val g = Node()
    g.add(box(2.4f, 0.04f, 2.4f, mat(0xD8D0BF)))
    g.add(cyl(0.035f, 0.04f, 1.0f, mat(0x333333), 0f, 0.04f, -0.2f, 8))
    g.add(box(0.2f, 0.5f, 0.16f, mat(0x222222), 0f, 0.85f, -0.2f))
    val lights = intArrayOf(0xFF2A2A, 0xFFC21A, 0x2AD14A).mapIndexed { i, c ->
        sphere(0.055f, basic(c), 0f, 1.25f - i * 0.15f, -0.115f, 10, 8).dyn().also { g.add(it) }
    }
    for (i in 0 until 6) g.add(box(0.2f, 0.08f, 0.08f, mat(if (i % 2 == 1) 0xFFFFFF else 0xD62828), -0.65f + i * 0.2f, 0.4f, 0.45f))
    g.add(cyl(0.04f, 0.05f, 0.45f, mat(0x555555), -0.8f, 0.04f, 0.45f, 8), cyl(0.04f, 0.05f, 0.45f, mat(0x555555), 0.45f, 0.04f, 0.45f, 8))
    g.add(cone(0.1f, 0.25f, mat(0xFF6A1A), 0.6f, 0.04f, -0.4f, 12), cone(0.1f, 0.25f, mat(0xFF6A1A), -0.6f, 0.04f, -0.5f, 12))
    g.tick = { t, _ ->
        val phase = (floor(t / 1.5f).toInt()) % 3
        lights.forEachIndexed { i, l -> l.material.colorScale = if (i == phase) 1f else 0.15f }
    }
    return g
}

private val BUILDERS: Map<String, () -> Node> = mapOf(
    "woodenChurch" to ::woodenChurch, "waterfall" to ::waterfall, "flagpole" to ::flagpole, "terraces" to ::terraces,
    "mountain" to ::mountain, "floatingMarket" to ::floatingMarket, "mangrove" to ::mangrove, "shipMonument" to ::shipMonument,
    "chamTowers" to ::chamTowers, "dunes" to ::dunes, "basalt" to ::basalt, "mySon" to ::mySon, "hoiAn" to ::hoiAn,
    "hueCitadel" to ::hueCitadel, "dragonBridge" to ::dragonBridge, "goldenBridge" to ::goldenBridge, "cave" to ::cave,
    "vanMieu" to ::vanMieu, "onePillar" to ::onePillar, "turtleTower" to ::turtleTower, "skyscraper" to ::skyscraper,
    "halong" to ::halong, "go" to ::goGate, "envelope" to ::envelope, "question" to ::question, "coins" to ::coins,
    "train" to ::train, "ship" to ::containerShip, "plane" to { plane(0x1A5FA8, 0xF2C94C) },
    "plane2" to { plane(0xC8102E, 0x1A5FA8) }, "dam" to ::dam, "windTurbine" to ::windTurbine, "jail" to ::jail,
    "pier" to ::pier, "whistle" to ::whistle,
)

val LANDMARK_COUNT get() = BUILDERS.size

fun buildLandmark(name: String): Node = (BUILDERS[name]?.invoke() ?: Node()).withShadows()
