package vn.cotyphu.gl

import android.opengl.Matrix
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Toán vector/ma trận tối giản, quy ước giống Three.js (tay phải, y hướng lên, ma trận cột).

class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    fun set(x: Float, y: Float, z: Float) = apply { this.x = x; this.y = y; this.z = z }
    fun set(v: Vec3) = set(v.x, v.y, v.z)
    fun copy() = Vec3(x, y, z)
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun add(o: Vec3) = apply { x += o.x; y += o.y; z += o.z }
    fun scale(s: Float) = apply { x *= s; y *= s; z *= s }
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(x * x + y * y + z * z)
    fun lengthSq() = x * x + y * y + z * z
    fun normalize() = apply { val l = length(); if (l > 1e-8f) { x /= l; y /= l; z /= l } }
    fun distanceTo(o: Vec3) = (this - o).length()
    fun lerp(to: Vec3, k: Float) = apply { x += (to.x - x) * k; y += (to.y - y) * k; z += (to.z - z) * k }
    fun lerpVectors(a: Vec3, b: Vec3, k: Float) = set(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k)
    fun setScalar(s: Float) = set(s, s, s)
    override fun toString() = "($x, $y, $z)"
}

class Quat(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var w: Float = 1f) {
    fun set(q: Quat) = apply { x = q.x; y = q.y; z = q.z; w = q.w }

    fun setFromAxisAngle(axis: Vec3, angle: Float) = apply {
        val h = angle / 2
        val s = sin(h)
        x = axis.x * s; y = axis.y * s; z = axis.z * s; w = cos(h)
    }

    /** Phép quay đưa vector đơn vị [from] về [to] (giống Quaternion.setFromUnitVectors). */
    fun setFromUnitVectors(from: Vec3, to: Vec3) = apply {
        var r = from.dot(to) + 1f
        if (r < 1e-6f) {
            r = 0f
            if (kotlin.math.abs(from.x) > kotlin.math.abs(from.z)) { x = -from.y; y = from.x; z = 0f } else { x = 0f; y = -from.z; z = from.y }
            w = r
        } else {
            x = from.y * to.z - from.z * to.y
            y = from.z * to.x - from.x * to.z
            z = from.x * to.y - from.y * to.x
            w = r
        }
        normalize()
    }

    fun normalize() = apply {
        val l = sqrt(x * x + y * y + z * z + w * w)
        if (l == 0f) { x = 0f; y = 0f; z = 0f; w = 1f } else { x /= l; y /= l; z /= l; w /= l }
    }

    /** this = this * b */
    fun multiply(b: Quat) = apply {
        val ax = x; val ay = y; val az = z; val aw = w
        x = ax * b.w + aw * b.x + ay * b.z - az * b.y
        y = ay * b.w + aw * b.y + az * b.x - ax * b.z
        z = az * b.w + aw * b.z + ax * b.y - ay * b.x
        w = aw * b.w - ax * b.x - ay * b.y - az * b.z
    }

    fun slerp(b: Quat, t: Float): Quat {
        var cosH = w * b.w + x * b.x + y * b.y + z * b.z
        val bb = if (cosH < 0) { cosH = -cosH; Quat(-b.x, -b.y, -b.z, -b.w) } else b
        if (cosH >= 1f) return this
        val h = acos(cosH.coerceIn(-1f, 1f))
        val s = sqrt(1f - cosH * cosH)
        if (kotlin.math.abs(s) < 0.001f) {
            x = 0.5f * (x + bb.x); y = 0.5f * (y + bb.y); z = 0.5f * (z + bb.z); w = 0.5f * (w + bb.w)
            return this
        }
        val ra = sin((1 - t) * h) / s
        val rb = sin(t * h) / s
        x = x * ra + bb.x * rb; y = y * ra + bb.y * rb; z = z * ra + bb.z * rb; w = w * ra + bb.w * rb
        return this
    }
}

object Mat4 {
    fun identity() = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    /** out = a * b */
    fun mul(out: FloatArray, a: FloatArray, b: FloatArray) = Matrix.multiplyMM(out, 0, a, 0, b, 0)

    /** Ma trận T·R·S từ vị trí, quaternion và tỉ lệ (giống Matrix4.compose). */
    fun compose(out: FloatArray, p: Vec3, q: Quat, s: Vec3) {
        val x2 = q.x + q.x; val y2 = q.y + q.y; val z2 = q.z + q.z
        val xx = q.x * x2; val xy = q.x * y2; val xz = q.x * z2
        val yy = q.y * y2; val yz = q.y * z2; val zz = q.z * z2
        val wx = q.w * x2; val wy = q.w * y2; val wz = q.w * z2
        out[0] = (1 - (yy + zz)) * s.x; out[1] = (xy + wz) * s.x; out[2] = (xz - wy) * s.x; out[3] = 0f
        out[4] = (xy - wz) * s.y; out[5] = (1 - (xx + zz)) * s.y; out[6] = (yz + wx) * s.y; out[7] = 0f
        out[8] = (xz + wy) * s.z; out[9] = (yz - wx) * s.z; out[10] = (1 - (xx + yy)) * s.z; out[11] = 0f
        out[12] = p.x; out[13] = p.y; out[14] = p.z; out[15] = 1f
    }

    fun transformPoint(m: FloatArray, v: Vec3): Vec3 {
        val x = v.x; val y = v.y; val z = v.z
        return Vec3(m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14])
    }

    fun transformDir(m: FloatArray, v: Vec3): Vec3 {
        val x = v.x; val y = v.y; val z = v.z
        return Vec3(m[0] * x + m[4] * y + m[8] * z, m[1] * x + m[5] * y + m[9] * z, m[2] * x + m[6] * y + m[10] * z)
    }
}

/** Quaternion từ góc Euler thứ tự "XYZ" hoặc "YZX" (hai thứ tự bản web dùng). */
fun eulerToQuat(ex: Float, ey: Float, ez: Float, order: String = "XYZ", out: Quat = Quat()): Quat {
    val c1 = cos(ex / 2); val c2 = cos(ey / 2); val c3 = cos(ez / 2)
    val s1 = sin(ex / 2); val s2 = sin(ey / 2); val s3 = sin(ez / 2)
    when (order) {
        "YZX" -> {
            out.x = s1 * c2 * c3 + c1 * s2 * s3
            out.y = c1 * s2 * c3 + s1 * c2 * s3
            out.z = c1 * c2 * s3 - s1 * s2 * c3
            out.w = c1 * c2 * c3 - s1 * s2 * s3
        }
        else -> {
            out.x = s1 * c2 * c3 + c1 * s2 * s3
            out.y = c1 * s2 * c3 - s1 * c2 * s3
            out.z = c1 * c2 * s3 + s1 * s2 * c3
            out.w = c1 * c2 * c3 - s1 * s2 * s3
        }
    }
    return out
}

const val PI_F = Math.PI.toFloat()

fun easeOutCubic(t: Float) = 1 - (1 - t) * (1 - t) * (1 - t)
fun easeInOutCubic(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - Math.pow((-2 * t + 2).toDouble(), 3.0).toFloat() / 2
fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1
    val u = t - 1
    return 1 + c3 * u * u * u + c1 * u * u
}

/** Màu sRGB 0xRRGGBB → mảng RGB tuyến tính (cho shader). */
fun srgbToLinear(rgb: Int, out: FloatArray = FloatArray(3)): FloatArray {
    fun ch(c: Int): Float {
        val v = c / 255f
        return if (v <= 0.04045f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    out[0] = ch((rgb shr 16) and 0xFF); out[1] = ch((rgb shr 8) and 0xFF); out[2] = ch(rgb and 0xFF)
    return out
}
