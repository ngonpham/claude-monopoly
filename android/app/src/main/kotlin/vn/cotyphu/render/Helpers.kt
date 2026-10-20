package vn.cotyphu.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import vn.cotyphu.gl.Geo
import vn.cotyphu.gl.Geometry
import vn.cotyphu.gl.Material
import vn.cotyphu.gl.Mesh
import vn.cotyphu.gl.Node
import vn.cotyphu.gl.Side
import vn.cotyphu.gl.Texture
import kotlin.math.PI

// Tiện ích dựng hình low-poly — port từ vietnam-3d/js/render/helpers.js.

/** Font Be Vietnam Pro dùng cho chữ vẽ lên texture; MainActivity nạp từ res/font. */
object Fonts {
    var regular: Typeface = Typeface.DEFAULT
    var semibold: Typeface = Typeface.DEFAULT
    var bold: Typeface = Typeface.DEFAULT_BOLD
    var extrabold: Typeface = Typeface.DEFAULT_BOLD
    var black: Typeface = Typeface.DEFAULT_BOLD

    fun weight(w: Int): Typeface = when {
        w >= 900 -> black
        w >= 800 -> extrabold
        w >= 700 -> bold
        w >= 600 -> semibold
        else -> regular
    }
}

private val matCache = HashMap<String, Material>()

/** Vật liệu dùng chung, cache theo màu + tuỳ chọn (giống mat() bản web). */
fun mat(
    color: Int,
    roughness: Float = 0.75f,
    metalness: Float = 0f,
    flat: Boolean = false,
    opacity: Float = 1f,
    emissive: Int = 0,
    emissiveIntensity: Float = 1f,
    side: Side = Side.FRONT,
): Material {
    val key = "$color|$roughness|$metalness|$flat|$opacity|$emissive|$emissiveIntensity|$side"
    return synchronized(matCache) {
        matCache.getOrPut(key) {
            Material(color, roughness, metalness, flat, opacity, opacity < 1f, emissive, emissiveIntensity, side)
        }
    }
}

/** Vật liệu không chiếu sáng (MeshBasicMaterial). Mỗi lần gọi tạo mới — thường dùng cho đèn nhấp nháy. */
fun basic(color: Int, opacity: Float = 1f, depthWrite: Boolean = true) =
    Material(color, opacity = opacity, transparent = opacity < 1f, unlit = true, depthWrite = depthWrite)

private fun place(m: Mesh, x: Float, y: Float, z: Float): Mesh {
    m.position.set(x, y, z)
    m.castShadow = true
    m.receiveShadow = true
    return m
}

/** Hộp có đáy tại y (thay vì tâm). */
fun box(w: Float, h: Float, d: Float, m: Material, x: Float = 0f, y: Float = 0f, z: Float = 0f) =
    place(Mesh(Geo.box(w, h, d), m), x, y + h / 2, z)

fun cyl(rTop: Float, rBottom: Float, h: Float, m: Material, x: Float = 0f, y: Float = 0f, z: Float = 0f, seg: Int = 16) =
    place(Mesh(Geo.cylinder(rTop, rBottom, h, seg), m), x, y + h / 2, z)

fun cone(r: Float, h: Float, m: Material, x: Float = 0f, y: Float = 0f, z: Float = 0f, seg: Int = 16) =
    place(Mesh(Geo.cone(r, h, seg), m), x, y + h / 2, z)

fun sphere(r: Float, m: Material, x: Float = 0f, y: Float = 0f, z: Float = 0f, wSeg: Int = 16, hSeg: Int = 12) =
    place(Mesh(Geo.sphere(r, wSeg, hSeg), m), x, y, z)

fun mesh(g: Geometry, m: Material, x: Float = 0f, y: Float = 0f, z: Float = 0f) = place(Mesh(g, m), x, y, z)

/** Mái ngói chữ A (lăng trụ tam giác) chạy dọc trục x. */
fun gableRoof(w: Float, h: Float, d: Float, m: Material, x: Float = 0f, y: Float = 0f, z: Float = 0f): Mesh {
    val geo = Geo.extrude(listOf(-d / 2 to 0f, d / 2 to 0f, 0f to h), w)
    geo.translate(0f, 0f, -w / 2)
    geo.rotateY((PI / 2).toFloat())
    return place(Mesh(geo, m), x, y, z)
}

/** Mái chùa cong: chóp 4 mặt + 4 đầu đao vểnh. */
fun pagodaRoof(size: Float, h: Float, m: Material, x: Float = 0f, y: Float = 0f, z: Float = 0f): Node {
    val g = Node()
    val roof = Mesh(Geo.cone(size * 0.75f, h, 4), m)
    roof.rotation.y = (PI / 4).toFloat()
    roof.position.y = h / 2
    roof.castShadow = true
    g.add(roof)
    for (k in 0 until 4) {
        val a = (PI / 4 + k * PI / 2).toFloat()
        val tip = Mesh(Geo.cone(0.035f, size * 0.28f, 5), m)
        tip.position.set(kotlin.math.cos(a) * size * 0.56f, h * 0.12f, kotlin.math.sin(a) * size * 0.56f)
        tip.rotation.z = -kotlin.math.cos(a) * 1.0f
        tip.rotation.x = kotlin.math.sin(a) * 1.0f
        g.add(tip)
    }
    g.position.set(x, y, z)
    return g
}

/** Mái hông: chóp 4 mặt kéo dãn thành hình chữ nhật. */
fun hipRoof(w: Float, h: Float, d: Float, m: Material, x: Float, y: Float, z: Float): Node {
    val geo = Geo.cone(0.5f, h, 4).rotateY((PI / 4).toFloat())
    val r = Mesh(geo, m)
    r.scale.set(w / 0.707f, 1f, d / 0.707f)
    r.position.y = h / 2
    r.castShadow = true
    return Node().add(r).also { it.position.set(x, y, z) }
}

/** Tấm nước phẳng. */
fun water(w: Float, d: Float, x: Float = 0f, z: Float = 0f, y: Float = 0.02f, color: Int = 0x3AA0C8): Mesh {
    val m = Mesh(Geo.box(w, 0.04f, d), mat(color, roughness = 0.15f, metalness = 0.2f, opacity = 0.88f))
    m.position.set(x, y, z)
    m.receiveShadow = true
    m.castShadow = false
    return m
}

/** Cây tán tròn. */
fun tree(x: Float, z: Float, s: Float = 1f, color: Int = 0x2F8F46, y: Float = 0f): Node {
    val g = Node()
    g.add(cyl(0.025f * s, 0.035f * s, 0.18f * s, mat(0x6B4423), seg = 6))
    val crown = Mesh(Geo.icosahedron(0.13f * s), mat(color, flat = true))
    crown.position.y = 0.26f * s
    g.add(crown)
    g.position.set(x, y, z)
    return g
}

/** Cây dừa. */
fun palm(x: Float, z: Float, s: Float = 1f, y: Float = 0f): Node {
    val g = Node()
    val trunk = cyl(0.02f * s, 0.035f * s, 0.5f * s, mat(0x8A6A3D), seg = 6)
    trunk.rotation.z = 0.12f
    g.add(trunk)
    for (k in 0 until 6) {
        val leaf = Mesh(Geo.cone(0.04f * s, 0.3f * s, 4), mat(0x3C9A45, flat = true))
        leaf.position.set(0.03f * s, 0.5f * s, 0f)
        leaf.rotation.z = (PI / 2.4).toFloat()
        val pivot = Node().add(leaf)
        pivot.rotation.y = (k * PI * 2 / 6).toFloat()
        g.add(pivot)
    }
    g.position.set(x, y, z)
    return g
}

/** Texture vẽ bằng Canvas (vẽ lười — chỉ khi cần tải lên GPU). */
fun canvasTexture(w: Int, h: Int, repeat: Boolean = false, draw: (Canvas, Int, Int) -> Unit): Texture =
    Texture({
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), w, h)
        bmp
    }, repeat = repeat)

fun fill(color: Int, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color or 0xFF000000.toInt()
    this.alpha = (alpha * 255).toInt()
    style = Paint.Style.FILL
}

fun stroke(color: Int, width: Float, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color or 0xFF000000.toInt()
    this.alpha = (alpha * 255).toInt()
    style = Paint.Style.STROKE
    strokeWidth = width
}

fun textPaint(color: Int, size: Float, weight: Int, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color or 0xFF000000.toInt()
    this.alpha = (alpha * 255).toInt()
    textSize = size
    typeface = Fonts.weight(weight)
    textAlign = Paint.Align.CENTER
}

/** Vẽ chữ căn giữa theo chiều dọc tại (cx, cy) như textBaseline = 'middle'. */
fun Canvas.drawTextMiddle(text: String, cx: Float, cy: Float, p: Paint) {
    val fm = p.fontMetrics
    drawText(text, cx, cy - (fm.ascent + fm.descent) / 2, p)
}

/** Đặt cast/receive shadow cho cả cây; vật trong suốt không đổ bóng. */
fun <T : Node> T.withShadows(): T = apply {
    traverse { n -> if (n is Mesh) { n.castShadow = !n.materials[0].transparent; n.receiveShadow = true } }
}

/** Bộ sinh số giả ngẫu nhiên giống rand() trong landmarks.js. */
fun rand(seed: Int): () -> Float {
    var s = seed.toLong()
    return {
        s = (s * 9301 + 49297) % 233280
        s / 233280f
    }
}
