package vn.cotyphu.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Một đoạn chỉ số dùng vật liệu riêng (giống geometry.groups của Three.js). */
data class DrawGroup(val start: Int, val count: Int, val materialIndex: Int)

/**
 * Lưới tam giác: position/normal/uv + chỉ số. Tải lên GPU một lần (hoặc mỗi khi [markDirty]),
 * tự tải lại nếu context OpenGL bị tạo lại.
 */
class Geometry(
    var positions: FloatArray,
    var normals: FloatArray,
    var uvs: FloatArray,
    var indices: IntArray,
    val groups: List<DrawGroup> = emptyList(),
) {
    val vertexCount get() = positions.size / 3

    /** Đỉnh thay đổi mỗi khung hình (cờ bay, mặt biển...). */
    var dynamic = false

    private var vao = 0
    private val vbos = IntArray(4)
    private var uploadedGeneration = -1
    private var dirty = true

    fun markDirty() { dirty = true }

    fun translate(x: Float, y: Float, z: Float) = apply {
        for (i in positions.indices step 3) { positions[i] += x; positions[i + 1] += y; positions[i + 2] += z }
    }

    fun rotateX(a: Float) = applyRotation { y, z -> floatArrayOf(y * cos(a) - z * sin(a), y * sin(a) + z * cos(a)) }.let { this }
    private inline fun applyRotation(f: (Float, Float) -> FloatArray) {
        for (arr in arrayOf(positions, normals)) for (i in arr.indices step 3) {
            val r = f(arr[i + 1], arr[i + 2]); arr[i + 1] = r[0]; arr[i + 2] = r[1]
        }
    }

    fun rotateY(a: Float) = apply {
        val c = cos(a); val s = sin(a)
        for (arr in arrayOf(positions, normals)) for (i in arr.indices step 3) {
            val x = arr[i]; val z = arr[i + 2]
            arr[i] = x * c + z * s; arr[i + 2] = -x * s + z * c
        }
    }

    fun rotateZ(a: Float) = apply {
        val c = cos(a); val s = sin(a)
        for (arr in arrayOf(positions, normals)) for (i in arr.indices step 3) {
            val x = arr[i]; val y = arr[i + 1]
            arr[i] = x * c - y * s; arr[i + 1] = x * s + y * c
        }
    }

    fun scale(sx: Float, sy: Float, sz: Float) = apply {
        for (i in positions.indices step 3) { positions[i] *= sx; positions[i + 1] *= sy; positions[i + 2] *= sz }
        for (i in normals.indices step 3) {
            val nx = normals[i] / sx; val ny = normals[i + 1] / sy; val nz = normals[i + 2] / sz
            val l = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 0 } ?: 1f
            normals[i] = nx / l; normals[i + 1] = ny / l; normals[i + 2] = nz / l
        }
    }

    /** Tính lại pháp tuyến mượt theo tam giác (dùng sau khi biến dạng đỉnh). */
    fun computeVertexNormals() = apply {
        val n = FloatArray(positions.size)
        for (t in indices.indices step 3) {
            val a = indices[t] * 3; val b = indices[t + 1] * 3; val c = indices[t + 2] * 3
            val e1x = positions[b] - positions[a]; val e1y = positions[b + 1] - positions[a + 1]; val e1z = positions[b + 2] - positions[a + 2]
            val e2x = positions[c] - positions[a]; val e2y = positions[c + 1] - positions[a + 1]; val e2z = positions[c + 2] - positions[a + 2]
            val nx = e1y * e2z - e1z * e2y; val ny = e1z * e2x - e1x * e2z; val nz = e1x * e2y - e1y * e2x
            for (v in intArrayOf(a, b, c)) { n[v] += nx; n[v + 1] += ny; n[v + 2] += nz }
        }
        for (i in n.indices step 3) {
            val l = sqrt(n[i] * n[i] + n[i + 1] * n[i + 1] + n[i + 2] * n[i + 2]).takeIf { it > 0 } ?: 1f
            n[i] /= l; n[i + 1] /= l; n[i + 2] /= l
        }
        normals = n
    }

    // ---------- GPU ----------

    /** Gắn VAO để vẽ; tải dữ liệu nếu cần. Chỉ gọi trên luồng OpenGL. */
    fun bind(generation: Int) {
        if (uploadedGeneration != generation) {
            val ids = IntArray(1)
            GLES30.glGenVertexArrays(1, ids, 0)
            vao = ids[0]
            GLES30.glGenBuffers(4, vbos, 0)
            GLES30.glBindVertexArray(vao)
            upload(0, positions, 3)
            upload(1, normals, 3)
            upload(2, uvs.takeIf { it.isNotEmpty() } ?: FloatArray(vertexCount * 2), 2)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vbos[3])
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, intBuffer(indices), GLES30.GL_STATIC_DRAW)
            uploadedGeneration = generation
            dirty = false
        } else {
            GLES30.glBindVertexArray(vao)
            if (dirty) {
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[0])
                GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, positions.size * 4, floatBuffer(positions))
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[1])
                GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, normals.size * 4, floatBuffer(normals))
                dirty = false
            }
        }
    }

    private fun upload(slot: Int, data: FloatArray, size: Int) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[slot])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, floatBuffer(data),
            if (dynamic) GLES30.GL_DYNAMIC_DRAW else GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(slot)
        GLES30.glVertexAttribPointer(slot, size, GLES30.GL_FLOAT, false, 0, 0)
    }

    companion object {
        fun floatBuffer(a: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

        fun intBuffer(a: IntArray): IntBuffer =
            ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply { put(a); position(0) }
    }
}

/** Bộ gom dữ liệu khi sinh hình. */
private class Builder {
    val pos = ArrayList<Float>()
    val nor = ArrayList<Float>()
    val uv = ArrayList<Float>()
    val idx = ArrayList<Int>()
    val groups = ArrayList<DrawGroup>()
    val count get() = pos.size / 3

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float): Int {
        pos += x; pos += y; pos += z; nor += nx; nor += ny; nor += nz; uv += u; uv += v
        return count - 1
    }

    fun tri(a: Int, b: Int, c: Int) { idx += a; idx += b; idx += c }

    fun build() = Geometry(pos.toFloatArray(), nor.toFloatArray(), uv.toFloatArray(), idx.toIntArray(), groups.toList())
}

private const val TAU = (2 * PI).toFloat()

/** Các hàm sinh hình — port thuật toán từ Three.js để UV và hướng mặt khớp bản web. */
object Geo {

    fun box(width: Float, height: Float, depth: Float): Geometry {
        val b = Builder()
        fun plane(u: Int, v: Int, w: Int, udir: Float, vdir: Float, pw: Float, ph: Float, pd: Float, material: Int) {
            val start = b.idx.size
            val base = b.count
            for (iy in 0..1) {
                val y = iy * ph - ph / 2
                for (ix in 0..1) {
                    val x = ix * pw - pw / 2
                    val vec = FloatArray(3); val n = FloatArray(3)
                    vec[u] = x * udir; vec[v] = y * vdir; vec[w] = pd / 2
                    n[w] = if (pd > 0) 1f else -1f
                    b.vertex(vec[0], vec[1], vec[2], n[0], n[1], n[2], ix.toFloat(), 1f - iy)
                }
            }
            val a = base; val bb = base + 2; val c = base + 3; val d = base + 1
            b.tri(a, bb, d); b.tri(bb, c, d)
            b.groups += DrawGroup(start, 6, material)
        }
        // x=0, y=1, z=2 — thứ tự mặt: +x, -x, +y, -y, +z, -z
        plane(2, 1, 0, -1f, -1f, depth, height, width, 0)
        plane(2, 1, 0, 1f, -1f, depth, height, -width, 1)
        plane(0, 2, 1, 1f, 1f, width, depth, height, 2)
        plane(0, 2, 1, 1f, -1f, width, depth, -height, 3)
        plane(0, 1, 2, 1f, -1f, width, height, depth, 4)
        plane(0, 1, 2, -1f, -1f, width, height, -depth, 5)
        return b.build()
    }

    fun plane(width: Float, height: Float, wSeg: Int = 1, hSeg: Int = 1): Geometry {
        val b = Builder()
        for (iy in 0..hSeg) {
            val y = iy * height / hSeg - height / 2
            for (ix in 0..wSeg) {
                val x = ix * width / wSeg - width / 2
                b.vertex(x, -y, 0f, 0f, 0f, 1f, ix.toFloat() / wSeg, 1f - iy.toFloat() / hSeg)
            }
        }
        val gx = wSeg + 1
        for (iy in 0 until hSeg) for (ix in 0 until wSeg) {
            val a = ix + gx * iy; val bb = ix + gx * (iy + 1); val c = ix + 1 + gx * (iy + 1); val d = ix + 1 + gx * iy
            b.tri(a, bb, d); b.tri(bb, c, d)
        }
        return b.build()
    }

    fun cylinder(
        radiusTop: Float, radiusBottom: Float, height: Float, radialSegments: Int = 32, heightSegments: Int = 1,
        openEnded: Boolean = false, thetaStart: Float = 0f, thetaLength: Float = TAU,
    ): Geometry {
        val b = Builder()
        val halfHeight = height / 2
        val grid = ArrayList<IntArray>()
        val slope = (radiusBottom - radiusTop) / height
        var groupStart = b.idx.size
        for (y in 0..heightSegments) {
            val row = IntArray(radialSegments + 1)
            val v = y.toFloat() / heightSegments
            val radius = v * (radiusBottom - radiusTop) + radiusTop
            for (x in 0..radialSegments) {
                val u = x.toFloat() / radialSegments
                val theta = u * thetaLength + thetaStart
                val st = sin(theta); val ct = cos(theta)
                val nl = sqrt(st * st + slope * slope + ct * ct)
                row[x] = b.vertex(radius * st, -v * height + halfHeight, radius * ct, st / nl, slope / nl, ct / nl, u, 1 - v)
            }
            grid += row
        }
        for (x in 0 until radialSegments) for (y in 0 until heightSegments) {
            val a = grid[y][x]; val bb = grid[y + 1][x]; val c = grid[y + 1][x + 1]; val d = grid[y][x + 1]
            if (radiusTop > 0 || y != 0) b.tri(a, bb, d)
            if (radiusBottom > 0 || y != heightSegments - 1) b.tri(bb, c, d)
        }
        b.groups += DrawGroup(groupStart, b.idx.size - groupStart, 0)

        fun cap(top: Boolean) {
            groupStart = b.idx.size
            val radius = if (top) radiusTop else radiusBottom
            val sign = if (top) 1f else -1f
            val centerStart = b.count
            for (x in 1..radialSegments) b.vertex(0f, halfHeight * sign, 0f, 0f, sign, 0f, 0.5f, 0.5f)
            val centerEnd = b.count
            for (x in 0..radialSegments) {
                val u = x.toFloat() / radialSegments
                val theta = u * thetaLength + thetaStart
                val ct = cos(theta); val st = sin(theta)
                b.vertex(radius * st, halfHeight * sign, radius * ct, 0f, sign, 0f, ct * 0.5f + 0.5f, st * 0.5f * sign + 0.5f)
            }
            for (x in 0 until radialSegments) {
                val c = centerStart + x; val i = centerEnd + x
                if (top) b.tri(i, i + 1, c) else b.tri(i + 1, i, c)
            }
            b.groups += DrawGroup(groupStart, b.idx.size - groupStart, if (top) 1 else 2)
        }
        if (!openEnded) {
            if (radiusTop > 0) cap(true)
            if (radiusBottom > 0) cap(false)
        }
        return b.build()
    }

    fun cone(radius: Float, height: Float, radialSegments: Int = 32, openEnded: Boolean = false) =
        cylinder(0f, radius, height, radialSegments, 1, openEnded)

    fun sphere(
        radius: Float, widthSegments: Int = 32, heightSegments: Int = 16,
        phiStart: Float = 0f, phiLength: Float = TAU, thetaStart: Float = 0f, thetaLength: Float = PI.toFloat(),
    ): Geometry {
        val b = Builder()
        val thetaEnd = min(thetaStart + thetaLength, PI.toFloat())
        val grid = ArrayList<IntArray>()
        for (iy in 0..heightSegments) {
            val row = IntArray(widthSegments + 1)
            val v = iy.toFloat() / heightSegments
            val uOffset = when {
                iy == 0 && thetaStart == 0f -> 0.5f / widthSegments
                iy == heightSegments && thetaEnd == PI.toFloat() -> -0.5f / widthSegments
                else -> 0f
            }
            for (ix in 0..widthSegments) {
                val u = ix.toFloat() / widthSegments
                val x = -radius * cos(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)
                val y = radius * cos(thetaStart + v * thetaLength)
                val z = radius * sin(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)
                val l = sqrt(x * x + y * y + z * z).takeIf { it > 0 } ?: 1f
                row[ix] = b.vertex(x, y, z, x / l, y / l, z / l, u + uOffset, 1 - v)
            }
            grid += row
        }
        for (iy in 0 until heightSegments) for (ix in 0 until widthSegments) {
            val a = grid[iy][ix + 1]; val bb = grid[iy][ix]; val c = grid[iy + 1][ix]; val d = grid[iy + 1][ix + 1]
            if (iy != 0 || thetaStart > 0) b.tri(a, bb, d)
            if (iy != heightSegments - 1 || thetaEnd < PI.toFloat()) b.tri(bb, c, d)
        }
        return b.build()
    }

    fun torus(radius: Float, tube: Float, radialSegments: Int = 12, tubularSegments: Int = 48, arc: Float = TAU): Geometry {
        val b = Builder()
        for (j in 0..radialSegments) for (i in 0..tubularSegments) {
            val u = i.toFloat() / tubularSegments * arc
            val v = j.toFloat() / radialSegments * TAU
            val x = (radius + tube * cos(v)) * cos(u)
            val y = (radius + tube * cos(v)) * sin(u)
            val z = tube * sin(v)
            var nx = x - radius * cos(u); var ny = y - radius * sin(u); var nz = z
            val l = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 0 } ?: 1f
            nx /= l; ny /= l; nz /= l
            b.vertex(x, y, z, nx, ny, nz, i.toFloat() / tubularSegments, j.toFloat() / radialSegments)
        }
        for (j in 1..radialSegments) for (i in 1..tubularSegments) {
            val a = (tubularSegments + 1) * j + i - 1
            val bb = (tubularSegments + 1) * (j - 1) + i - 1
            val c = (tubularSegments + 1) * (j - 1) + i
            val d = (tubularSegments + 1) * j + i
            b.tri(a, bb, d); b.tri(bb, c, d)
        }
        return b.build()
    }

    fun circle(radius: Float, segments: Int = 32, thetaStart: Float = 0f, thetaLength: Float = TAU): Geometry {
        val b = Builder()
        b.vertex(0f, 0f, 0f, 0f, 0f, 1f, 0.5f, 0.5f)
        for (s in 0..segments) {
            val seg = thetaStart + s.toFloat() / segments * thetaLength
            val x = radius * cos(seg); val y = radius * sin(seg)
            b.vertex(x, y, 0f, 0f, 0f, 1f, (x / radius + 1) / 2, (y / radius + 1) / 2)
        }
        for (i in 1..segments) b.tri(i, i + 1, 0)
        return b.build()
    }

    fun ring(inner: Float, outer: Float, thetaSegments: Int = 32): Geometry {
        val b = Builder()
        var radius = inner
        for (j in 0..1) {
            for (i in 0..thetaSegments) {
                val seg = i.toFloat() / thetaSegments * TAU
                val x = radius * cos(seg); val y = radius * sin(seg)
                b.vertex(x, y, 0f, 0f, 0f, 1f, (x / outer + 1) / 2, (y / outer + 1) / 2)
            }
            radius += outer - inner
        }
        for (i in 0 until thetaSegments) {
            val a = i; val bb = i + thetaSegments + 1; val c = i + thetaSegments + 2; val d = i + 1
            b.tri(a, bb, d); b.tri(bb, c, d)
        }
        return b.build()
    }

    /** Xoay biên dạng (x, y) quanh trục y — LatheGeometry. */
    fun lathe(points: List<Pair<Float, Float>>, segments: Int = 12): Geometry {
        val b = Builder()
        for (i in 0..segments) {
            val phi = i.toFloat() / segments * TAU
            val s = sin(phi); val c = cos(phi)
            points.forEachIndexed { j, (px, py) ->
                b.vertex(px * s, py, px * c, 0f, 1f, 0f, i.toFloat() / segments, j.toFloat() / (points.size - 1))
            }
        }
        for (i in 0 until segments) for (j in 0 until points.size - 1) {
            val base = j + i * points.size
            val a = base; val bb = base + points.size; val c = base + points.size + 1; val d = base + 1
            b.tri(a, bb, d); b.tri(c, d, bb)
        }
        return b.build().computeVertexNormals()
    }

    fun capsule(radius: Float, length: Float, capSegments: Int = 4, radialSegments: Int = 8): Geometry {
        val pts = ArrayList<Pair<Float, Float>>()
        for (k in 0..capSegments) {
            val a = -PI.toFloat() / 2 + k.toFloat() / capSegments * (PI.toFloat() / 2)
            pts += (radius * cos(a)) to (-length / 2 + radius * sin(a))
        }
        for (k in 0..capSegments) {
            val a = k.toFloat() / capSegments * (PI.toFloat() / 2)
            pts += (radius * cos(a)) to (length / 2 + radius * sin(a))
        }
        pts[0] = 0f to pts[0].second
        pts[pts.size - 1] = 0f to pts.last().second
        return lathe(pts, radialSegments)
    }

    private fun polyhedron(vertices: FloatArray, faces: IntArray, radius: Float): Geometry {
        val b = Builder()
        fun p(i: Int): Vec3 = Vec3(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2]).normalize().scale(radius)
        for (f in faces.indices step 3) {
            val a = p(faces[f]); val bb = p(faces[f + 1]); val c = p(faces[f + 2])
            val n = (bb - a).cross(c - a).normalize()
            b.tri(b.vertex(a.x, a.y, a.z, n.x, n.y, n.z, 0f, 0f), b.vertex(bb.x, bb.y, bb.z, n.x, n.y, n.z, 0f, 0f), b.vertex(c.x, c.y, c.z, n.x, n.y, n.z, 0f, 0f))
        }
        return b.build()
    }

    fun octahedron(radius: Float) = polyhedron(
        floatArrayOf(1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f),
        intArrayOf(0, 2, 4, 0, 4, 3, 0, 3, 5, 0, 5, 2, 1, 2, 5, 1, 5, 3, 1, 3, 4, 1, 4, 2), radius,
    )

    fun icosahedron(radius: Float): Geometry {
        val t = ((1 + sqrt(5.0)) / 2).toFloat()
        return polyhedron(
            floatArrayOf(-1f, t, 0f, 1f, t, 0f, -1f, -t, 0f, 1f, -t, 0f, 0f, -1f, t, 0f, 1f, t, 0f, -1f, -t, 0f, 1f, -t, t, 0f, -1f, t, 0f, 1f, -t, 0f, -1f, -t, 0f, 1f),
            intArrayOf(0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
                3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1), radius,
        )
    }

    fun dodecahedron(radius: Float): Geometry {
        val t = ((1 + sqrt(5.0)) / 2).toFloat()
        val r = 1 / t
        return polyhedron(
            floatArrayOf(
                -1f, -1f, -1f, -1f, -1f, 1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f, -1f, -1f, 1f, -1f, 1f, 1f, 1f, -1f, 1f, 1f, 1f,
                0f, -r, -t, 0f, -r, t, 0f, r, -t, 0f, r, t, -r, -t, 0f, -r, t, 0f, r, -t, 0f, r, t, 0f,
                -t, 0f, -r, t, 0f, -r, -t, 0f, r, t, 0f, r,
            ),
            intArrayOf(
                3, 11, 7, 3, 7, 15, 3, 15, 13, 7, 19, 17, 7, 17, 6, 7, 6, 15, 17, 4, 8, 17, 8, 10, 17, 10, 6,
                8, 0, 16, 8, 16, 2, 8, 2, 10, 0, 12, 1, 0, 1, 18, 0, 18, 16, 6, 10, 2, 6, 2, 13, 6, 13, 15,
                2, 16, 18, 2, 18, 3, 2, 3, 13, 18, 1, 9, 18, 9, 11, 18, 11, 3, 4, 14, 12, 4, 12, 0, 4, 0, 8,
                11, 9, 5, 11, 5, 19, 11, 19, 7, 19, 5, 14, 19, 14, 4, 19, 4, 17, 1, 12, 14, 1, 14, 5, 1, 5, 9,
            ), radius,
        )
    }

    /** Đùn đa giác phẳng (trong mặt XY) theo trục +z — ExtrudeGeometry không vát cạnh. */
    fun extrude(shape: List<Pair<Float, Float>>, depth: Float): Geometry {
        var pts = shape
        val area = pts.indices.sumOf { i -> val (x1, y1) = pts[i]; val (x2, y2) = pts[(i + 1) % pts.size]; (x1 * y2 - x2 * y1).toDouble() }
        if (area < 0) pts = pts.reversed()
        val b = Builder()
        val tris = triangulate(pts)
        // mặt trước (z = depth) và mặt sau (z = 0)
        val front = pts.map { (x, y) -> b.vertex(x, y, depth, 0f, 0f, 1f, x, y) }
        for (t in tris.indices step 3) b.tri(front[tris[t]], front[tris[t + 1]], front[tris[t + 2]])
        val back = pts.map { (x, y) -> b.vertex(x, y, 0f, 0f, 0f, -1f, x, y) }
        for (t in tris.indices step 3) b.tri(back[tris[t + 2]], back[tris[t + 1]], back[tris[t]])
        // thành bên
        for (i in pts.indices) {
            val (x1, y1) = pts[i]; val (x2, y2) = pts[(i + 1) % pts.size]
            val ex = x2 - x1; val ey = y2 - y1
            val l = sqrt(ex * ex + ey * ey).takeIf { it > 0 } ?: 1f
            val nx = ey / l; val ny = -ex / l
            val a = b.vertex(x1, y1, 0f, nx, ny, 0f, 0f, 0f)
            val bb = b.vertex(x2, y2, 0f, nx, ny, 0f, 1f, 0f)
            val c = b.vertex(x2, y2, depth, nx, ny, 0f, 1f, 1f)
            val d = b.vertex(x1, y1, depth, nx, ny, 0f, 0f, 1f)
            b.tri(a, bb, c); b.tri(a, c, d)
        }
        return b.build()
    }

    /** Tam giác hoá đa giác đơn (ngược chiều kim đồng hồ) bằng cắt tai. */
    private fun triangulate(pts: List<Pair<Float, Float>>): IntArray {
        val remaining = pts.indices.toMutableList()
        val out = ArrayList<Int>()
        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        fun inside(p: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>) =
            cross(a, b, p) >= 0 && cross(b, c, p) >= 0 && cross(c, a, p) >= 0
        var guard = 0
        while (remaining.size > 3 && guard++ < 1000) {
            var clipped = false
            for (k in remaining.indices) {
                val i0 = remaining[(k - 1 + remaining.size) % remaining.size]
                val i1 = remaining[k]
                val i2 = remaining[(k + 1) % remaining.size]
                if (cross(pts[i0], pts[i1], pts[i2]) <= 0) continue
                if (remaining.any { it != i0 && it != i1 && it != i2 && inside(pts[it], pts[i0], pts[i1], pts[i2]) }) continue
                out += i0; out += i1; out += i2
                remaining.removeAt(k)
                clipped = true
                break
            }
            if (!clipped) break
        }
        if (remaining.size == 3) { out += remaining[0]; out += remaining[1]; out += remaining[2] }
        return out.toIntArray()
    }

    /** Ống chạy dọc đường cong Catmull-Rom qua các điểm — TubeGeometry. */
    fun tube(points: List<Vec3>, tubularSegments: Int, radius: Float, radialSegments: Int): Geometry {
        fun catmull(t: Float): Vec3 {
            val p = (points.size - 1) * t
            val i = p.toInt().coerceAtMost(points.size - 2)
            val w = p - i
            val p0 = points[(i - 1).coerceAtLeast(0)]; val p1 = points[i]; val p2 = points[i + 1]; val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
            fun cr(a: Float, b: Float, c: Float, d: Float): Float {
                val v0 = (c - a) * 0.5f; val v1 = (d - b) * 0.5f
                val t2 = w * w; val t3 = w * t2
                return (2 * b - 2 * c + v0 + v1) * t3 + (-3 * b + 3 * c - 2 * v0 - v1) * t2 + v0 * w + b
            }
            return Vec3(cr(p0.x, p1.x, p2.x, p3.x), cr(p0.y, p1.y, p2.y, p3.y), cr(p0.z, p1.z, p2.z, p3.z))
        }
        val pts = (0..tubularSegments).map { catmull(it.toFloat() / tubularSegments) }
        val tangents = pts.indices.map { i ->
            val a = pts[(i - 1).coerceAtLeast(0)]; val c = pts[(i + 1).coerceAtMost(pts.size - 1)]
            (c - a).normalize()
        }
        // khung Frenet vận chuyển song song
        val t0 = tangents[0]
        val axis = when (minOf(abs(t0.x), abs(t0.y), abs(t0.z))) { abs(t0.x) -> Vec3(1f, 0f, 0f); abs(t0.y) -> Vec3(0f, 1f, 0f); else -> Vec3(0f, 0f, 1f) }
        val normals = ArrayList<Vec3>()
        val binormals = ArrayList<Vec3>()
        var n = t0.cross(axis).normalize()
        n = t0.cross(n).normalize()
        normals += n; binormals += t0.cross(n)
        for (i in 1 until pts.size) {
            var ni = normals[i - 1].copy()
            val v = tangents[i - 1].cross(tangents[i])
            if (v.length() > 1e-6f) {
                v.normalize()
                val theta = kotlin.math.acos(tangents[i - 1].dot(tangents[i]).coerceIn(-1f, 1f))
                val q = Quat().setFromAxisAngle(v, theta)
                val m = FloatArray(16)
                Mat4.compose(m, Vec3(), q, Vec3(1f, 1f, 1f))
                ni = Mat4.transformDir(m, ni)
            }
            normals += ni
            binormals += tangents[i].cross(ni)
        }
        val b = Builder()
        for (i in 0..tubularSegments) for (j in 0..radialSegments) {
            val v = j.toFloat() / radialSegments * TAU
            val s = sin(v); val c = -cos(v)
            val nx = c * normals[i].x + s * binormals[i].x
            val ny = c * normals[i].y + s * binormals[i].y
            val nz = c * normals[i].z + s * binormals[i].z
            b.vertex(pts[i].x + radius * nx, pts[i].y + radius * ny, pts[i].z + radius * nz, nx, ny, nz,
                i.toFloat() / tubularSegments, j.toFloat() / radialSegments)
        }
        for (j in 1..tubularSegments) for (i in 1..radialSegments) {
            val a = (radialSegments + 1) * (j - 1) + (i - 1)
            val bb = (radialSegments + 1) * j + (i - 1)
            val c = (radialSegments + 1) * j + i
            val d = (radialSegments + 1) * (j - 1) + i
            b.tri(a, bb, d); b.tri(bb, c, d)
        }
        return b.build()
    }

    /** Gộp nhiều geometry (đã biến đổi sẵn) thành một — dùng cho gộp vẽ tĩnh. */
    fun merge(parts: List<Pair<Geometry, FloatArray>>): Geometry {
        val total = parts.sumOf { it.first.vertexCount }
        val pos = FloatArray(total * 3); val nor = FloatArray(total * 3); val uv = FloatArray(total * 2)
        val idx = IntArray(parts.sumOf { it.first.indices.size })
        var vOff = 0; var iOff = 0
        val normalMat = FloatArray(16); val tmp = FloatArray(16)
        for ((g, m) in parts) {
            android.opengl.Matrix.invertM(tmp, 0, m, 0)
            android.opengl.Matrix.transposeM(normalMat, 0, tmp, 0)
            for (v in 0 until g.vertexCount) {
                val p = Mat4.transformPoint(m, Vec3(g.positions[v * 3], g.positions[v * 3 + 1], g.positions[v * 3 + 2]))
                val n = Mat4.transformDir(normalMat, Vec3(g.normals[v * 3], g.normals[v * 3 + 1], g.normals[v * 3 + 2])).normalize()
                val o = (vOff + v) * 3
                pos[o] = p.x; pos[o + 1] = p.y; pos[o + 2] = p.z
                nor[o] = n.x; nor[o + 1] = n.y; nor[o + 2] = n.z
                if (g.uvs.isNotEmpty()) { uv[(vOff + v) * 2] = g.uvs[v * 2]; uv[(vOff + v) * 2 + 1] = g.uvs[v * 2 + 1] }
            }
            // phép biến đổi lật (định thức âm) phải đảo chiều tam giác
            val flip = det3(m) < 0
            for (t in g.indices.indices step 3) {
                idx[iOff + t] = g.indices[t] + vOff
                idx[iOff + t + 1] = g.indices[if (flip) t + 2 else t + 1] + vOff
                idx[iOff + t + 2] = g.indices[if (flip) t + 1 else t + 2] + vOff
            }
            vOff += g.vertexCount; iOff += g.indices.size
        }
        return Geometry(pos, nor, uv, idx)
    }

    private fun det3(m: FloatArray) =
        m[0] * (m[5] * m[10] - m[9] * m[6]) - m[4] * (m[1] * m[10] - m[9] * m[2]) + m[8] * (m[1] * m[6] - m[5] * m[2])
}
