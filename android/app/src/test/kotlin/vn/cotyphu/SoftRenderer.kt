package vn.cotyphu

import android.graphics.Bitmap
import android.opengl.Matrix
import vn.cotyphu.gl.Material
import vn.cotyphu.gl.Mesh
import vn.cotyphu.gl.Node
import vn.cotyphu.gl.Side
import vn.cotyphu.gl.Texture
import vn.cotyphu.gl.Vec3
import vn.cotyphu.gl.srgbToLinear
import java.io.File
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Bộ rasterize phần mềm tối giản để xem trước cây cảnh 3D trong kiểm thử JVM (không có GPU):
 * z-buffer, chiếu sáng giống shader chính (bán cầu + mặt trời, ACES, sRGB), texture lấy mẫu gần nhất.
 * Không có bóng đổ — đủ để soát hình học, hướng ô, vị trí camera và chữ trên texture.
 */
class SoftRenderer(private val w: Int, private val h: Int) {
    private val color = IntArray(w * h)
    private val depth = FloatArray(w * h)
    private val textures = HashMap<Texture, Triple<IntArray, Int, Int>>()
    private val vp = FloatArray(16)
    private lateinit var cam: Vec3

    private val sunDir = Vec3(22f, 34f, 14f).normalize()
    private val sun = srgbToLinear(0xFFF1D6).map { it * 2.6f }
    private val sky = srgbToLinear(0xDCEFFF).map { it * 1.25f }
    private val ground = srgbToLinear(0x8A6A45).map { it * 1.25f }
    var triangles = 0
        private set

    fun render(root: Node, eye: Vec3, target: Vec3, fovDeg: Float = 42f) {
        cam = eye
        val view = FloatArray(16); val proj = FloatArray(16)
        Matrix.setLookAtM(view, 0, eye.x, eye.y, eye.z, target.x, target.y, target.z, 0f, 1f, 0f)
        Matrix.perspectiveM(proj, 0, fovDeg, w.toFloat() / h, 0.1f, 500f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        for (y in 0 until h) {
            val t = y.toFloat() / h
            val c = if (t < 0.55f) lerpColor(0x3D7FC4, 0xA9D3EC, t / 0.55f) else lerpColor(0xA9D3EC, 0xF6DCB4, min(1f, (t - 0.55f) / 0.2f))
            for (x in 0 until w) color[y * w + x] = c
        }
        depth.fill(Float.MAX_VALUE)
        root.updateWorld(null)
        val opaque = ArrayList<Mesh>(); val transparent = ArrayList<Mesh>()
        root.traverseVisible { if (it is Mesh) (if (it.materials[0].transparent) transparent else opaque) += it }
        opaque.forEach { drawMesh(it, blend = false) }
        transparent.sortedByDescending { val m = it.world; (Vec3(m[12], m[13], m[14]) - cam).lengthSq() }.forEach { drawMesh(it, blend = true) }
    }

    fun save(file: File) {
        val bmp = Bitmap.createBitmap(color.map { it or 0xFF000000.toInt() }.toIntArray(), w, h, Bitmap.Config.ARGB_8888)
        file.parentFile.mkdirs()
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun lerpColor(a: Int, b: Int, k: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - k) + ((b shr s) and 0xFF) * k).toInt()
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun tex(t: Texture): Triple<IntArray, Int, Int> = textures.getOrPut(t) {
        val bmp: Bitmap = t.renderBitmap()
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        Triple(px, bmp.width, bmp.height)
    }

    private fun drawMesh(m: Mesh, blend: Boolean) {
        val g = m.geometry
        val world = m.world
        val n = g.vertexCount
        val wx = FloatArray(n); val wy = FloatArray(n); val wz = FloatArray(n)
        val cx = FloatArray(n); val cy = FloatArray(n); val cz = FloatArray(n); val cw = FloatArray(n)
        for (i in 0 until n) {
            val x = g.positions[i * 3]; val y = g.positions[i * 3 + 1]; val z = g.positions[i * 3 + 2]
            wx[i] = world[0] * x + world[4] * y + world[8] * z + world[12]
            wy[i] = world[1] * x + world[5] * y + world[9] * z + world[13]
            wz[i] = world[2] * x + world[6] * y + world[10] * z + world[14]
            cx[i] = vp[0] * wx[i] + vp[4] * wy[i] + vp[8] * wz[i] + vp[12]
            cy[i] = vp[1] * wx[i] + vp[5] * wy[i] + vp[9] * wz[i] + vp[13]
            cz[i] = vp[2] * wx[i] + vp[6] * wy[i] + vp[10] * wz[i] + vp[14]
            cw[i] = vp[3] * wx[i] + vp[7] * wy[i] + vp[11] * wz[i] + vp[15]
        }
        val ranges = if (m.materials.size > 1 && g.groups.isNotEmpty()) g.groups.map { Triple(it.start, it.count, m.materials[it.materialIndex % m.materials.size]) }
        else listOf(Triple(0, g.indices.size, m.materials[0]))
        for ((start, count, mat) in ranges) {
            var t = start
            while (t < start + count) {
                triangle(g.indices[t], g.indices[t + 1], g.indices[t + 2], wx, wy, wz, cx, cy, cz, cw, g.uvs, mat, blend)
                t += 3
            }
        }
    }

    private fun triangle(a: Int, b: Int, c: Int, wx: FloatArray, wy: FloatArray, wz: FloatArray,
                         cx: FloatArray, cy: FloatArray, cz: FloatArray, cw: FloatArray, uvs: FloatArray, mat: Material, blend: Boolean) {
        if (cw[a] < 0.05f || cw[b] < 0.05f || cw[c] < 0.05f) return
        val ax = cx[a] / cw[a]; val ay = cy[a] / cw[a]
        val bx = cx[b] / cw[b]; val by = cy[b] / cw[b]
        val qx = cx[c] / cw[c]; val qy = cy[c] / cw[c]
        val areaNdc = (bx - ax) * (qy - ay) - (by - ay) * (qx - ax)
        if (mat.side == Side.FRONT && areaNdc <= 0f) return
        if (areaNdc == 0f) return
        triangles++
        // pháp tuyến mặt (thế giới), quay về phía camera nếu là mặt hai phía
        val e1 = Vec3(wx[b] - wx[a], wy[b] - wy[a], wz[b] - wz[a])
        val e2 = Vec3(wx[c] - wx[a], wy[c] - wy[a], wz[c] - wz[a])
        val nrm = e1.cross(e2).normalize()
        if (areaNdc < 0) nrm.scale(-1f)
        val albedo = srgbToLinear(mat.color).map { it * mat.colorScale }
        val ndl = max(0f, nrm.dot(sunDir))
        val k = 0.5f * nrm.y + 0.5f
        val emissive = srgbToLinear(mat.emissive).map { it * mat.emissiveIntensity }
        val shade = FloatArray(3) { i ->
            if (mat.unlit) 1f else ((ground[i] + (sky[i] - ground[i]) * k) + sun[i] * ndl) / PI.toFloat() * (1 - mat.metalness * 0.5f)
        }
        val map = mat.map?.let { tex(it) }
        val emap = mat.emissiveMap?.let { tex(it) }

        val sx = floatArrayOf((ax * 0.5f + 0.5f) * w, (bx * 0.5f + 0.5f) * w, (qx * 0.5f + 0.5f) * w)
        val sy = floatArrayOf((1 - (ay * 0.5f + 0.5f)) * h, (1 - (by * 0.5f + 0.5f)) * h, (1 - (qy * 0.5f + 0.5f)) * h)
        val sz = floatArrayOf(cz[a] / cw[a], cz[b] / cw[b], cz[c] / cw[c])
        val iw = floatArrayOf(1 / cw[a], 1 / cw[b], 1 / cw[c])
        val idx = intArrayOf(a, b, c)
        val minX = max(0, floor(sx.min()).toInt()); val maxX = min(w - 1, ceil(sx.max()).toInt())
        val minY = max(0, floor(sy.min()).toInt()); val maxY = min(h - 1, ceil(sy.max()).toInt())
        if (minX > maxX || minY > maxY) return
        val area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sy[1] - sy[0]) * (sx[2] - sx[0])
        if (area == 0f) return
        for (py in minY..maxY) for (px in minX..maxX) {
            val fx = px + 0.5f; val fy = py + 0.5f
            val w0 = ((sx[1] - fx) * (sy[2] - fy) - (sy[1] - fy) * (sx[2] - fx)) / area
            val w1 = ((sx[2] - fx) * (sy[0] - fy) - (sy[2] - fy) * (sx[0] - fx)) / area
            val w2 = 1 - w0 - w1
            if (w0 < 0 || w1 < 0 || w2 < 0) continue
            val z = w0 * sz[0] + w1 * sz[1] + w2 * sz[2]
            if (z < -1 || z > 1) continue
            val o = py * w + px
            if (z >= depth[o]) continue
            var r = albedo[0]; var gg = albedo[1]; var bb = albedo[2]
            var alpha = mat.opacity
            var er = emissive[0]; var eg = emissive[1]; var eb = emissive[2]
            if ((map != null || emap != null) && uvs.isNotEmpty()) {
                val q = w0 * iw[0] + w1 * iw[1] + w2 * iw[2]
                var u = (w0 * uvs[idx[0] * 2] * iw[0] + w1 * uvs[idx[1] * 2] * iw[1] + w2 * uvs[idx[2] * 2] * iw[2]) / q
                var v = (w0 * uvs[idx[0] * 2 + 1] * iw[0] + w1 * uvs[idx[1] * 2 + 1] * iw[1] + w2 * uvs[idx[2] * 2 + 1] * iw[2]) / q
                fun sample(t: Triple<IntArray, Int, Int>): Int {
                    val tu = (u - floor(u)); val tv = (v - floor(v))
                    val x = (tu * (t.second - 1)).toInt().coerceIn(0, t.second - 1)
                    val y = ((1 - tv) * (t.third - 1)).toInt().coerceIn(0, t.third - 1)
                    return t.first[y * t.second + x]
                }
                if (map != null) {
                    val p = sample(map)
                    val lin = srgbToLinear(p and 0xFFFFFF)
                    r *= lin[0]; gg *= lin[1]; bb *= lin[2]
                    alpha *= ((p ushr 24) and 0xFF) / 255f
                }
                if (emap != null) {
                    val lin = srgbToLinear(sample(emap) and 0xFFFFFF)
                    er *= lin[0]; eg *= lin[1]; eb *= lin[2]
                }
                u = 0f; v = 0f
            }
            val out = intArrayOf(tone(r * shade[0] + er), tone(gg * shade[1] + eg), tone(bb * shade[2] + eb))
            if (blend) {
                val prev = color[o]
                val pr = (prev shr 16) and 0xFF; val pg = (prev shr 8) and 0xFF; val pb = prev and 0xFF
                color[o] = ((out[0] * alpha + pr * (1 - alpha)).toInt() shl 16) or ((out[1] * alpha + pg * (1 - alpha)).toInt() shl 8) or (out[2] * alpha + pb * (1 - alpha)).toInt()
                if (mat.depthWrite) depth[o] = z
            } else {
                color[o] = (out[0] shl 16) or (out[1] shl 8) or out[2]
                depth[o] = z
            }
        }
    }

    /** Tone mapping gần đúng ACES + mã hoá sRGB → 0..255. */
    private fun tone(v: Float): Int {
        val x = v * 1.05f / 0.6f * 0.6f
        val a = x * (2.51f * x + 0.03f); val b = x * (2.43f * x + 0.59f) + 0.14f
        val t = (a / b).coerceIn(0f, 1f)
        val s = if (t <= 0.0031308f) t * 12.92f else 1.055f * t.pow(1 / 2.4f) - 0.055f
        return (s * 255).toInt().coerceIn(0, 255)
    }
}
