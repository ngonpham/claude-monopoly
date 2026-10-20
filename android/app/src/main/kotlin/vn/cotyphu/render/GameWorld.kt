package vn.cotyphu.render

import vn.cotyphu.core.JAIL_INDEX
import vn.cotyphu.core.PlayerState
import vn.cotyphu.gl.Batcher
import vn.cotyphu.gl.Engine
import vn.cotyphu.gl.Geo
import vn.cotyphu.gl.Material
import vn.cotyphu.gl.Mesh
import vn.cotyphu.gl.Node
import vn.cotyphu.gl.PI_F
import vn.cotyphu.gl.Vec3
import vn.cotyphu.gl.dyn
import kotlin.math.cos
import kotlin.math.sin

/** Biển có sóng + quần đảo đá vôi + thuyền buồm quanh bàn cờ (scene.js của bản web). */
private fun scenery(): Node {
    val root = Node()
    val geo = Geo.plane(420f, 420f, 64, 64).rotateX(-PI_F / 2)
    geo.dynamic = true
    val base = geo.positions.copyOf()
    val ocean = Mesh(geo, Material(0x2A86A8, roughness = 0.28f, metalness = 0.15f, flat = true)).dyn()
    ocean.position.y = -1.6f
    ocean.castShadow = false
    ocean.tick = { t, _ ->
        val p = geo.positions
        for (i in p.indices step 3) {
            p[i + 1] = sin(base[i] * 0.08f + t * 0.9f) * 0.25f + cos(base[i + 2] * 0.07f + t * 0.7f) * 0.25f
        }
        geo.markDirty()
    }
    root.add(ocean)

    val islands = Node()
    var seed = 30
    for (i in 0 until 26) {
        val a = i / 26f * PI_F * 2 + sin(i * 7.3f) * 0.12f
        val r = 34f + (i * 37) % 23
        val s = 7f + (i * 13) % 9
        val isl = karst(0f, 0f, 1f, 0.3f, seed++)
        isl.scale.set(s, s * (0.9f + ((i * 5) % 7) / 6f), s)
        isl.position.set(cos(a) * r, -1.6f, sin(a) * r)
        isl.withShadows()
        islands.add(isl)
    }
    Batcher.mergeStatic(islands)
    // đảo ở xa không cần đổ bóng lên bàn
    islands.traverse { if (it is Mesh) it.castShadow = false }
    root.add(islands)

    val boats = (0 until 4).map { i ->
        junkBoat().withShadows().dyn().also {
            it.scale.setScalar(5f)
            root.add(it)
        }
    }
    root.tick = { t, _ ->
        boats.forEachIndexed { i, b ->
            val radius = 22f + i * 2
            val a = i / 4f * PI_F * 2 + t * 0.03f
            b.position.set(cos(a) * radius, -1.5f + sin(t + radius) * 0.08f, sin(a) * radius)
            b.rotation.y = -a - PI_F / 2
        }
    }
    return root
}

/**
 * Toàn bộ thế giới 3D của một ván: bàn cờ, xúc xắc, cảnh nền và quân cờ.
 * Dựng trên luồng nền (không gọi OpenGL), sau đó [attach] vào engine.
 */
class GameWorld(private val engine: Engine) {
    val board = Board3D()
    val dice = Dice3D(engine)
    private val scenery = scenery()
    private val tokenLayer = Node().dyn()
    var tokens: List<Token> = emptyList()
        private set

    fun prepare() = board.prepareTextures()

    fun attach() = engine.post { engine.scene.add(scenery, board.group, dice.group, tokenLayer) }

    /** Đặt lại quân cờ, chủ sở hữu và nhà theo trạng thái ván (bắt đầu ván mới hoặc khôi phục). */
    suspend fun reset(players: List<PlayerState>, owners: List<Pair<Int?, Int>>, colorOf: (Int) -> Int, dice1: Int, dice2: Int) {
        val newTokens = players.map { Token(engine, it.token, colorOf(it.id)) }
        engine.run {
            tokenLayer.clear()
            newTokens.forEach { tokenLayer.add(it.group) }
            players.forEach { p ->
                val tk = newTokens[p.id]
                tk.group.position.set(board.tokenSlot(p.pos, p.id, p.inJail && p.pos == JAIL_INDEX))
                tk.group.visible = !p.bankrupt
            }
            owners.forEachIndexed { i, (owner, level) ->
                val color = owner?.let(colorOf)
                board.setOwner(i, color)
                board.setLevel(i, level, color ?: 0xFFFFFF)
            }
            dice.show(dice1, dice2)
        }
        tokens = newTokens
    }

    fun tokenPosition(id: Int): Vec3 = tokens[id].group.position.copy()
}
