package vn.cotyphu.gl

import android.opengl.Matrix

/**
 * Gộp mọi lưới tĩnh dùng chung vật liệu dưới [root] thành một lưới, để vẽ vài chục lệnh thay vì
 * vài nghìn (mô hình low-poly của 40 ô có rất nhiều khối nhỏ). Nhánh đánh dấu [Node.dynamic],
 * lưới nhiều vật liệu và lưới có đỉnh động được giữ nguyên.
 */
object Batcher {
    private data class Key(val material: Material, val castShadow: Boolean, val receiveShadow: Boolean)

    fun mergeStatic(root: Node) {
        root.updateWorld(null)
        val inv = FloatArray(16)
        Matrix.invertM(inv, 0, root.world, 0)
        val buckets = LinkedHashMap<Key, MutableList<Pair<Geometry, FloatArray>>>()

        fun visit(n: Node) {
            if (n.dynamic || !n.visible) return
            for (c in n.children.toList()) {
                val mergeable = c is Mesh && !c.dynamic && c.visible && c.children.isEmpty() && c.tick == null &&
                    c.materials.size == 1 && !c.geometry.dynamic
                if (mergeable) {
                    c as Mesh
                    val rel = FloatArray(16)
                    Matrix.multiplyMM(rel, 0, inv, 0, c.world, 0)
                    buckets.getOrPut(Key(c.material, c.castShadow, c.receiveShadow)) { mutableListOf() } += c.geometry to rel
                    n.remove(c)
                } else {
                    visit(c)
                }
            }
        }
        visit(root)
        for ((key, parts) in buckets) {
            // chia nhỏ để chỉ số đỉnh vẫn gọn (tối đa ~60k đỉnh mỗi lưới)
            var chunk = ArrayList<Pair<Geometry, FloatArray>>()
            var verts = 0
            fun flush() {
                if (chunk.isEmpty()) return
                root.add(Mesh(Geo.merge(chunk), key.material).apply {
                    castShadow = key.castShadow
                    receiveShadow = key.receiveShadow
                })
                chunk = ArrayList(); verts = 0
            }
            for (p in parts) {
                if (verts + p.first.vertexCount > 60000) flush()
                chunk += p; verts += p.first.vertexCount
            }
            flush()
        }
    }
}
