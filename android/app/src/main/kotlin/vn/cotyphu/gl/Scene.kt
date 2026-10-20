package vn.cotyphu.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Texture sinh từ Bitmap (thường vẽ bằng Canvas). Giữ hàm [factory] để vẽ lại khi context OpenGL bị
 * tạo lại, và giải phóng Bitmap ngay sau khi tải lên để tiết kiệm bộ nhớ.
 */
class Texture(private val factory: () -> Bitmap, val repeat: Boolean = false, val srgb: Boolean = true) {
    /** Dịch UV mỗi khung hình (thác nước chảy). */
    var offsetX = 0f
    var offsetY = 0f

    private var id = 0
    private var uploadedGeneration = -1
    private var pending: Bitmap? = null

    /** Vẽ bitmap mới (dùng cho ảnh xem trước trong kiểm thử). */
    fun renderBitmap(): Bitmap = factory()

    /** Vẽ trước bitmap trên luồng nền để luồng OpenGL chỉ việc tải lên. */
    fun prepare() { if (pending == null && uploadedGeneration < 0) pending = factory() }

    fun bind(unit: Int, generation: Int, anisotropy: Float) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        if (uploadedGeneration != generation) {
            val bmp = pending ?: factory()
            pending = null
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            id = ids[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
            if (srgb) {
                val buf = ByteBuffer.allocateDirect(bmp.byteCount).order(ByteOrder.nativeOrder())
                val copy = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
                copy.copyPixelsToBuffer(buf)
                buf.position(0)
                GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_SRGB8_ALPHA8, bmp.width, bmp.height, 0,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
            } else {
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            }
            val wrap = if (repeat) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            if (anisotropy > 1f) GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, TEXTURE_MAX_ANISOTROPY_EXT, anisotropy)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            bmp.recycle()
            uploadedGeneration = generation
        } else {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        }
    }

    companion object {
        const val TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
    }
}

enum class Side { FRONT, DOUBLE }

/** Vật liệu kiểu MeshStandardMaterial / MeshBasicMaterial (unlit) của Three.js. Màu dạng 0xRRGGBB (sRGB). */
class Material(
    var color: Int = 0xFFFFFF,
    var roughness: Float = 0.75f,
    var metalness: Float = 0f,
    var flat: Boolean = false,
    var opacity: Float = 1f,
    var transparent: Boolean = opacity < 1f,
    var emissive: Int = 0x000000,
    var emissiveIntensity: Float = 1f,
    var side: Side = Side.FRONT,
    var map: Texture? = null,
    var emissiveMap: Texture? = null,
    var unlit: Boolean = false,
    var depthWrite: Boolean = true,
) {
    /** Hệ số nhân màu (đèn giao thông tắt/bật mà không tạo vật liệu mới). */
    var colorScale = 1f
}

/** Nút trong cây cảnh: vị trí, quay (Euler hoặc quaternion), tỉ lệ, con. */
open class Node {
    val position = Vec3()
    val rotation = Vec3()
    var rotationOrder = "XYZ"
    /** Nếu khác null thì dùng thay cho [rotation]. */
    var quaternion: Quat? = null
    val scale = Vec3(1f, 1f, 1f)
    var visible = true
    var name = ""
    /** Nút (hoặc con của nó) thay đổi theo thời gian — không gộp vào lưới tĩnh. */
    var dynamic = false
    var parent: Node? = null
        private set
    val children = ArrayList<Node>()
    var tick: ((t: Float, dt: Float) -> Unit)? = null

    val world = Mat4.identity()
    private val local = FloatArray(16)
    private val q = Quat()

    fun add(vararg nodes: Node): Node {
        for (n in nodes) {
            n.parent?.children?.remove(n)
            n.parent = this
            children += n
        }
        return this
    }

    fun remove(n: Node) {
        if (children.remove(n)) n.parent = null
    }

    fun clear() {
        children.forEach { it.parent = null }
        children.clear()
    }

    fun setPos(x: Float, y: Float, z: Float) = apply { position.set(x, y, z) }

    fun updateWorld(parentWorld: FloatArray?) {
        val rq = quaternion ?: eulerToQuat(rotation.x, rotation.y, rotation.z, rotationOrder, q)
        Mat4.compose(local, position, rq, scale)
        if (parentWorld == null) System.arraycopy(local, 0, world, 0, 16) else Mat4.mul(world, parentWorld, local)
        for (c in children) c.updateWorld(world)
    }

    /** Cập nhật ma trận từ gốc cây xuống nút này rồi đổi điểm cục bộ sang toạ độ thế giới. */
    fun localToWorld(v: Vec3): Vec3 {
        root().updateWorld(null)
        return Mat4.transformPoint(world, v)
    }

    fun worldPosition(): Vec3 = Vec3(world[12], world[13], world[14])

    fun root(): Node {
        var n: Node = this
        while (n.parent != null) n = n.parent!!
        return n
    }

    fun traverse(f: (Node) -> Unit) {
        f(this)
        for (c in children.toList()) c.traverse(f)
    }

    /** Duyệt các nút đang hiển thị (bỏ qua nhánh bị ẩn). */
    fun traverseVisible(f: (Node) -> Unit) {
        if (!visible) return
        f(this)
        for (c in children) c.traverseVisible(f)
    }
}

class Mesh(var geometry: Geometry, var materials: List<Material>) : Node() {
    constructor(geometry: Geometry, material: Material) : this(geometry, listOf(material))

    var castShadow = true
    var receiveShadow = true

    var material: Material
        get() = materials[0]
        set(value) { materials = listOf(value) }
}

/** Đánh dấu nút động (không gộp tĩnh) và trả lại chính nó để viết gọn. */
fun <T : Node> T.dyn(): T = apply { dynamic = true }
