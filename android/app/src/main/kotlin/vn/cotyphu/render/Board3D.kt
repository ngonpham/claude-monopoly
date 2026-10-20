package vn.cotyphu.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import vn.cotyphu.core.Board
import vn.cotyphu.core.Deck
import vn.cotyphu.core.Tile
import vn.cotyphu.core.TileType
import vn.cotyphu.core.formatMoney
import vn.cotyphu.gl.Batcher
import vn.cotyphu.gl.Engine
import vn.cotyphu.gl.Geo
import vn.cotyphu.gl.Material
import vn.cotyphu.gl.Mesh
import vn.cotyphu.gl.Node
import vn.cotyphu.gl.PI_F
import vn.cotyphu.gl.Texture
import vn.cotyphu.gl.Vec3
import vn.cotyphu.gl.dyn
import vn.cotyphu.gl.easeInOutCubic
import vn.cotyphu.gl.easeOutBack
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// Bàn cờ 3D — port từ js/render/board3d.js: đế sơn mài, 40 ô texture canvas, mô hình công trình,
// mặt trống đồng Đông Sơn ở giữa, chồng thẻ, khung chủ sở hữu và nhà/khách sạn.

const val TILE_W = 2.2f
const val TILE_D = 3.6f
const val HALF = TILE_D + 4.5f * TILE_W // 13.5
const val TILE_TOP = 0.12f

data class TileFrame(val x: Float, val z: Float, val rotY: Float, val corner: Boolean)

/** Vị trí & hướng của ô i (trục +z cục bộ hướng ra ngoài mép bàn). */
fun tileFrame(i: Int): TileFrame {
    val side = i / 10
    val k = i % 10
    val z = HALF - TILE_D / 2
    val x = if (k == 0) HALF - TILE_D / 2 else HALF - TILE_D - TILE_W * (k - 1) - TILE_W / 2
    val theta = -side * (PI_F / 2)
    val c = cos(theta); val s = sin(theta)
    return TileFrame(x * c + z * s, -x * s + z * c, theta, k == 0)
}

/** Ô chứa điểm (x, z) trên mặt bàn, hoặc null. */
fun tileAt(x: Float, z: Float): Int? {
    for (i in 0 until Board.SIZE) {
        val f = tileFrame(i)
        val dx = x - f.x; val dz = z - f.z
        // đổi về hệ cục bộ của ô (quay ngược rotY)
        val c = cos(-f.rotY); val s = sin(-f.rotY)
        val lx = dx * c + dz * s
        val lz = -dx * s + dz * c
        val w = if (f.corner) TILE_D else TILE_W
        if (kotlin.math.abs(lx) <= w / 2 && kotlin.math.abs(lz) <= TILE_D / 2) return i
    }
    return null
}

private val TYPE_BAND = mapOf(TileType.TRANSPORT to (0xFF37474F.toInt() to "GIAO THÔNG"), TileType.UTILITY to (0xFF00695C.toInt() to "NĂNG LƯỢNG"))

private fun wrapText(p: Paint, text: String, maxWidth: Float): List<String> {
    val lines = ArrayList<String>()
    var line = ""
    for (w in text.split(" ")) {
        val test = if (line.isEmpty()) w else "$line $w"
        if (p.measureText(test) > maxWidth && line.isNotEmpty()) { lines += line; line = w } else line = test
    }
    if (line.isNotEmpty()) lines += line
    return lines
}

private fun fitLines(p: Paint, text: String, maxWidth: Float, maxLines: Int, size: Float): List<String> {
    var s = size
    var lines: List<String>
    do {
        p.textSize = s
        lines = wrapText(p, text, maxWidth)
        s -= 2
    } while ((lines.size > maxLines || lines.any { p.measureText(it) > maxWidth }) && s > 14)
    p.textSize = s + 2
    return lines
}

private fun drawCentered(c: Canvas, lines: List<String>, cx: Float, cy: Float, p: Paint) {
    val lh = p.textSize * 1.12f
    val start = cy - (lines.size - 1) * lh / 2
    lines.forEachIndexed { i, l -> c.drawTextMiddle(l, cx, start + i * lh, p) }
}

private fun drawPaper(c: Canvas, w: Int, h: Int) {
    val p = Paint()
    p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFFFBF4E2.toInt(), 0xFFF1E4C4.toInt(), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    c.drawRect(3f, 3f, w - 3f, h - 3f, stroke(0x7A4A1A, 6f, 0.55f))
}

internal fun tileTexture(tile: Tile): Texture = canvasTexture(312, 512) { c, w, h ->
    drawPaper(c, w, h)
    val band = tile.group?.let { (Board.GROUPS.getValue(it).color.toInt()) to Board.GROUPS.getValue(it).name } ?: TYPE_BAND[tile.type]
    if (band != null) {
        c.drawRect(6f, 6f, w - 6f, 80f, fill(band.first))
        val p = textPaint(0xFFFFFF, 24f, 700, 0.95f)
        drawCentered(c, fitLines(p, band.second.uppercase(), w - 40f, 2, 24f), w / 2f, 43f, p)
    }
    val ring = stroke(0xA06E32, 2f, 0.12f)
    var r = 30f
    while (r < 120f) { c.drawCircle(w / 2f, 200f, r, ring); r += 18f }
    var title = tile.name
    var color = 0x3B2412
    if (tile.type == TileType.CHANCE) { title = "CƠ HỘI"; color = 0xE06A00 }
    if (tile.type == TileType.COMMUNITY) { title = "KHÍ VẬN"; color = 0xC8102E }
    val tp = textPaint(color, 40f, 800)
    drawCentered(c, fitLines(tp, title, w - 34f, 3, 40f), w / 2f, 380f, tp)
    val footer = when {
        tile.price > 0 -> formatMoney(tile.price)
        tile.type == TileType.TAX -> "Nộp ${formatMoney(tile.amount)}"
        else -> ""
    }
    if (footer.isNotEmpty()) c.drawText(footer, w / 2f, 476f, textPaint(0x7A4A1A, 30f, 700))
}

internal fun cornerTexture(tile: Tile): Texture = canvasTexture(512, 512) { c, w, _ ->
    val s = w.toFloat()
    drawPaper(c, w, w)
    val colors = mapOf(TileType.GO to 0xC62828, TileType.JAIL to 0x455A64, TileType.PARKING to 0x2E7D32, TileType.GOTOJAIL to 0x1565C0)
    val subs = mapOf(
        TileType.GO to "Nhận 200 tr₫ mỗi vòng", TileType.JAIL to "Chỉ ghé thăm",
        TileType.PARKING to "Nhận Quỹ chung", TileType.GOTOJAIL to "Đi thẳng vào Nhà tạm giữ",
    )
    val tp = textPaint(colors.getValue(tile.type), 54f, 900)
    fitLines(tp, tile.name.uppercase(), s - 30f, 1, 54f)
    c.drawTextMiddle(tile.name.uppercase(), s / 2, s - 92, tp)
    c.drawTextMiddle(subs.getValue(tile.type), s / 2, s - 44, textPaint(0x5A3A1A, 28f, 600))
    if (tile.type == TileType.GO) {
        val arrow = Path().apply {
            moveTo(40f, 120f); lineTo(160f, 50f); lineTo(160f, 90f); lineTo(470f, 90f)
            lineTo(470f, 150f); lineTo(160f, 150f); lineTo(160f, 190f); close()
        }
        c.drawPath(arrow, fill(0xC62828, 0.18f))
    }
}

/** Mặt trống đồng Đông Sơn + tên game ở giữa bàn. */
internal fun centerTexture(): Texture = canvasTexture(2048, 2048) { c, size, _ ->
    val s = size.toFloat()
    val cx = s / 2
    val bg = Paint().apply { shader = RadialGradient(cx, cx, s * 0.72f, intArrayOf(0xFF7A1C14.toInt(), 0xFF2A0907.toInt()), floatArrayOf(100f / (s * 0.72f), 1f), Shader.TileMode.CLAMP) }
    c.drawRect(0f, 0f, s, s, bg)
    val bronze = 0xE8B25C
    // ngôi sao 14 cánh
    val star = Path()
    for (i in 0 until 28) {
        val a = i * PI_F / 14
        val r = if (i % 2 == 0) 210f else 70f
        val x = cx + cos(a) * r; val y = cx + sin(a) * r
        if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
    }
    star.close()
    c.drawPath(star, fill(bronze, 0.55f))
    val ringP = stroke(bronze, 6f, 0.55f)
    for (r in floatArrayOf(240f, 270f, 330f, 360f, 470f, 500f, 640f, 670f, 800f, 830f, 940f, 970f)) c.drawCircle(cx, cx, r, ringP)
    val dot = fill(bronze, 0.28f)
    for ((r, n) in listOf(300f to 48, 815f to 140, 955f to 160)) for (i in 0 until n) {
        val a = i.toFloat() / n * PI_F * 2
        c.drawCircle(cx + cos(a) * r, cx + sin(a) * r, 7f, dot)
    }
    val tick = stroke(bronze, 5f, 0.28f)
    for (i in 0 until 90) {
        val a = i / 90f * PI_F * 2
        c.drawLine(cx + cos(a) * 365, cx + sin(a) * 365, cx + cos(a + 0.05f) * 465, cx + sin(a + 0.05f) * 465, tick)
    }
    // chim Lạc cách điệu
    val birdP = fill(bronze, 0.55f)
    for (i in 0 until 18) {
        val a = i / 18f * PI_F * 2
        c.save()
        c.translate(cx + cos(a) * 735, cx + sin(a) * 735)
        c.rotate(Math.toDegrees((a + PI_F / 2).toDouble()).toFloat())
        val body = Path().apply {
            moveTo(-50f, 8f); quadTo(-10f, -30f, 40f, -8f); lineTo(70f, -26f); lineTo(48f, 4f); quadTo(0f, 24f, -50f, 8f); close()
        }
        c.drawPath(body, birdP)
        val wing = Path().apply { moveTo(-10f, -4f); lineTo(-40f, -50f); lineTo(10f, -10f); close() }
        c.drawPath(wing, birdP)
        c.restore()
    }
    // tiêu đề vàng
    fun title(text: String, size: Float, y: Float) {
        val p = textPaint(0xF2C14E, size, 900)
        p.shader = LinearGradient(0f, cx - 200, 0f, cx + 200, intArrayOf(0xFFFFF1B8.toInt(), 0xFFF2C14E.toInt(), 0xFFC98A1B.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        p.setShadowLayer(24f, 0f, 0f, 0x99000000.toInt())
        c.drawTextMiddle(text, cx, y, p)
    }
    title("CỜ TỶ PHÚ", 190f, cx - 80)
    title("VIỆT NAM", 150f, cx + 110)
    c.drawTextMiddle("Hành trình qua di sản & thắng cảnh", cx, cx + 250, textPaint(0xFFECBE, 52f, 600, 0.85f))
}

private fun deckTexture(label: String, color: Int): Texture = canvasTexture(256, 360) { c, w, h ->
    c.drawColor(color or 0xFF000000.toInt())
    c.drawRect(14f, 14f, w - 14f, h - 14f, stroke(0xFFE08A, 10f))
    c.drawTextMiddle(label, w / 2f, h / 2f, textPaint(0xFFE08A, 46f, 900))
}

private fun houseMesh(ownerColor: Int): Node {
    val g = Node()
    g.add(box(0.36f, 0.4f, 0.34f, mat(0xF4EAD2)))
    g.add(gableRoof(0.42f, 0.2f, 0.4f, mat(ownerColor, roughness = 0.5f), 0f, 0.4f, 0f))
    g.add(box(0.1f, 0.16f, 0.02f, mat(0x5A3A22), 0f, 0f, 0.175f))
    g.add(box(0.12f, 0.08f, 0.02f, mat(0x8FC4E8, emissive = 0x223344), 0f, 0.24f, 0.175f))
    return g.withShadows()
}

private fun hotelMesh(ownerColor: Int): Node {
    val g = Node()
    g.add(box(1.4f, 0.75f, 0.44f, mat(0xF7F3EA)))
    var y = 0.12f
    while (y < 0.7f) { g.add(box(1.41f, 0.04f, 0.45f, mat(ownerColor, roughness = 0.4f), 0f, y, 0f)); y += 0.14f }
    g.add(box(1.5f, 0.06f, 0.5f, mat(0x9A7A3A, metalness = 0.5f), 0f, 0.75f, 0f))
    g.add(box(0.6f, 0.16f, 0.05f, mat(0xF2C14E, metalness = 0.7f, roughness = 0.25f, emissive = 0x4A3200), 0f, 0.81f, 0.18f))
    for (i in 0 until 5) g.add(mesh(Geo.octahedron(0.035f), mat(0xFFE08A, emissive = 0x806000), -0.2f + i * 0.1f, 0.89f, 0.21f))
    return g.withShadows()
}

class Board3D {
    val group = Node()
    private class TileView(val group: Node, val topMat: Material, val buildings: Node, val corner: Boolean) {
        var frame: Node? = null
        var level = 0
    }
    private val tiles = ArrayList<TileView>()
    private val decks = HashMap<Deck, Pair<Mesh, Texture>>()
    private var hovered: Int? = null

    init {
        buildBase()
        buildTiles()
        buildCenter()
        // gộp mọi khối tĩnh dùng chung vật liệu → vài chục lệnh vẽ thay vì vài nghìn
        Batcher.mergeStatic(group)
        group.updateWorld(null)
    }

    private fun buildBase() {
        val size = HALF * 2
        val lacquer = Material(0x5A1410, roughness = 0.35f, metalness = 0.1f)
        group.add(mesh(Geo.box(size + 1.2f, 0.8f, size + 1.2f), lacquer, 0f, -0.4f, 0f))
        val trim = mat(0xD4A24A, metalness = 0.8f, roughness = 0.3f)
        val e = (size + 1.2f) / 2
        for ((dims, pos) in listOf(
            floatArrayOf(size + 1.3f, 0.18f) to floatArrayOf(0f, e), floatArrayOf(size + 1.3f, 0.18f) to floatArrayOf(0f, -e),
            floatArrayOf(0.18f, size + 1.3f) to floatArrayOf(e, 0f), floatArrayOf(0.18f, size + 1.3f) to floatArrayOf(-e, 0f),
        )) group.add(mesh(Geo.box(dims[0], 0.12f, dims[1]), trim, pos[0], -0.02f, pos[1]))
        val pedestal = mesh(Geo.cylinder(size * 0.56f, size * 0.64f, 2.4f, 8), mat(0x4A4038, flat = true, roughness = 1f), 0f, -2.0f, 0f)
        pedestal.rotation.y = PI_F / 8
        group.add(pedestal)
    }

    private fun buildTiles() {
        val sideMat = mat(0xE9DCBC, roughness = 0.8f)
        Board.TILES.forEachIndexed { i, tile ->
            val f = tileFrame(i)
            val w = if (f.corner) TILE_D else TILE_W
            val g = Node()
            g.position.set(f.x, 0f, f.z)
            g.rotation.y = f.rotY
            g.add(mesh(Geo.box(w - 0.06f, TILE_TOP, TILE_D - 0.06f), sideMat, 0f, TILE_TOP / 2, 0f).also { it.castShadow = false })
            val topMat = Material(map = if (f.corner) cornerTexture(tile) else tileTexture(tile), roughness = 0.85f)
            val top = mesh(Geo.plane(w - 0.06f, TILE_D - 0.06f), topMat, 0f, TILE_TOP + 0.002f, 0f)
            top.rotation.x = -PI_F / 2
            top.castShadow = false
            g.add(top)
            val model = buildLandmark(tile.model)
            model.position.set(0f, TILE_TOP, if (f.corner) 0f else -0.38f)
            g.add(model)
            val buildings = Node().dyn()
            buildings.position.set(0f, TILE_TOP, -TILE_D / 2 + 0.3f)
            g.add(buildings)
            group.add(g)
            tiles += TileView(g, topMat, buildings, f.corner)
        }
    }

    private fun buildCenter() {
        val inner = (HALF - TILE_D) * 2
        val center = mesh(Geo.plane(inner - 0.1f, inner - 0.1f), Material(map = centerTexture(), roughness = 0.4f, metalness = 0.15f), 0f, 0.01f, 0f)
        center.rotation.x = -PI_F / 2
        center.castShadow = false
        group.add(center)
        fun makeDeck(deck: Deck, label: String, color: Int, x: Float, z: Float) {
            val tex = deckTexture(label, color)
            val side = mat(0xFFF6E0)
            val top = Material(map = tex, roughness = 0.5f)
            val m = Mesh(Geo.box(2.0f, 0.36f, 2.8f), listOf(side, side, top, side, side, side))
            m.position.set(x, 0.19f, z)
            m.rotation.y = PI_F / 4
            group.add(m)
            decks[deck] = m to tex
        }
        makeDeck(Deck.CHANCE, "CƠ HỘI", 0xE06A00, -5.6f, 4.4f)
        makeDeck(Deck.COMMUNITY, "KHÍ VẬN", 0xB0102A, 5.6f, -4.4f)
    }

    /** Vẽ trước các texture trên luồng nền (tránh giật khi tải lên GPU). */
    fun prepareTextures() {
        group.traverse { n -> if (n is Mesh) n.materials.forEach { it.map?.prepare(); it.emissiveMap?.prepare() } }
    }

    /** Toạ độ thế giới cho quân của người chơi [slot] trên ô [index]. Gọi trên luồng OpenGL. */
    fun tokenSlot(index: Int, slot: Int, jailed: Boolean = false): Vec3 {
        val t = tiles[index]
        val local = if (t.corner) {
            val visit = arrayOf(floatArrayOf(0.85f, 0.85f), floatArrayOf(1.4f, 0.85f), floatArrayOf(0.85f, 1.4f), floatArrayOf(1.4f, 1.4f))
            val inJail = arrayOf(floatArrayOf(0.05f, 0.05f), floatArrayOf(0.05f, -0.75f), floatArrayOf(-0.75f, 0.05f), floatArrayOf(-0.33f, 0.05f))
            (if (jailed) inJail else visit)[slot % 4]
        } else {
            arrayOf(floatArrayOf(-0.5f, 0.85f), floatArrayOf(0.5f, 0.85f), floatArrayOf(-0.5f, 1.4f), floatArrayOf(0.5f, 1.4f))[slot % 4]
        }
        return t.group.localToWorld(Vec3(local[0], TILE_TOP, local[1]))
    }

    fun tileCenter(index: Int): Vec3 = tiles[index].group.localToWorld(Vec3(0f, TILE_TOP, 0f))

    fun setHover(index: Int?) {
        hovered?.let { tiles[it].topMat.emissive = 0 }
        hovered = index
        index?.let { tiles[it].topMat.emissive = 0x3A2A10 }
    }

    /** Khung màu chủ sở hữu quanh ô (null = bỏ). Gọi trên luồng OpenGL; [engine] dùng để chạy hiệu ứng mọc lên. */
    fun setOwner(index: Int, color: Int?, engine: Engine? = null) {
        val t = tiles[index]
        t.frame?.let { t.group.remove(it) }
        t.frame = null
        if (color == null) return
        val w = (if (t.corner) TILE_D else TILE_W) - 0.1f
        val d = TILE_D - 0.1f
        val m = mat(color, emissive = color, emissiveIntensity = 0.35f, roughness = 0.4f)
        val th = 0.12f
        val frame = Node().dyn()
        frame.add(box(w, 0.05f, th, m, 0f, TILE_TOP, d / 2 - th / 2), box(w, 0.05f, th, m, 0f, TILE_TOP, -d / 2 + th / 2))
        frame.add(box(th, 0.05f, d, m, w / 2 - th / 2, TILE_TOP, 0f), box(th, 0.05f, d, m, -w / 2 + th / 2, TILE_TOP, 0f))
        frame.add(box(0.02f, 0.5f, 0.02f, mat(0xDDDDDD), w / 2 - 0.2f, TILE_TOP, d / 2 - 0.25f))
        val pennant = mesh(Geo.cone(0.1f, 0.3f, 3), m, w / 2 - 0.35f, TILE_TOP + 0.52f, d / 2 - 0.25f)
        pennant.rotation.z = PI_F / 2
        frame.add(pennant)
        frame.withShadows()
        t.group.add(frame)
        t.frame = frame
        if (engine != null) {
            frame.scale.set(1f, 0.01f, 1f)
            engine.animate(400) { k -> frame.scale.set(1f, max(0.01f, easeOutBack(k)), 1f) }
        }
    }

    /** Cập nhật nhà/khách sạn trên ô; có [engine] thì công trình mới "mọc" lên. Gọi trên luồng OpenGL. */
    fun setLevel(index: Int, level: Int, ownerColor: Int, engine: Engine? = null) {
        val t = tiles[index]
        val prev = t.level
        t.level = level
        t.buildings.clear()
        if (level <= 0) return
        val fresh = ArrayList<Node>()
        if (level >= 4) {
            val h = hotelMesh(ownerColor)
            t.buildings.add(h)
            if (prev < 4) fresh += h
        } else {
            floatArrayOf(-0.66f, 0f, 0.66f).take(level).forEachIndexed { k, x ->
                val h = houseMesh(ownerColor)
                h.position.x = x
                t.buildings.add(h)
                if (k >= prev) fresh += h
            }
        }
        if (engine != null && fresh.isNotEmpty()) {
            fresh.forEach { it.scale.setScalar(0.01f) }
            engine.animate(550) { k -> fresh.forEach { it.scale.setScalar(max(0.01f, easeOutBack(k))) } }
        }
    }

    /** Lá thẻ bay lên từ chồng thẻ rồi lật về phía camera. */
    suspend fun drawCard(engine: Engine, deck: Deck) {
        val (deckMesh, tex) = decks.getValue(deck)
        val side = mat(0xFFF6E0)
        val card = Mesh(Geo.box(2.0f, 0.03f, 2.8f), listOf(side, side, Material(map = tex), side, side, side)).dyn()
        val start = Vec3()
        val target = Vec3()
        engine.run {
            card.position.set(deckMesh.position.x, 0.4f, deckMesh.position.z)
            card.rotation.y = deckMesh.rotation.y
            group.add(card)
            start.set(card.position)
            target.lerpVectors(start, engine.camera.position, 0.25f)
            target.y = max(target.y, 4f)
        }
        engine.tween(700) { k ->
            val e = easeInOutCubic(k)
            card.position.lerpVectors(start, target, e)
            card.rotation.x = e * PI_F
            card.rotation.z = e * 0.4f
        }
        engine.tween(300) { k -> card.scale.setScalar(1 - k) }
        engine.run { group.remove(card) }
    }
}
