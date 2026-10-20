package vn.cotyphu.core

// "Máy" chơi đơn giản: giữ tiền dự phòng, ưu tiên gom trọn vùng — port từ js/core/bot.js.

private const val RESERVE_BUY = 150
private const val RESERVE_BUILD = 250

enum class JailChoice { CARD, FINE, ROLL }

object Bot {
    fun jailChoice(game: Game): JailChoice {
        val p = game.current
        if (!p.inJail) return JailChoice.ROLL
        if (p.jailFree.isNotEmpty()) return JailChoice.CARD
        // Đầu game nên ra tù sớm để mua đất; cuối game ở tù lại an toàn.
        val unowned = game.state.tiles.indices.count { game.state.tiles[it].owner == null && Board.TILES[it].price > 0 }
        if (p.money > JAIL_FINE + 300 && unowned > 8) return JailChoice.FINE
        return JailChoice.ROLL
    }

    /** Quyết định mua (true/false). */
    fun wantsToBuy(game: Game): Boolean {
        val s = game.state
        val p = game.current
        val pending = s.pending ?: return false
        val tile = Board.TILES[pending.tile]
        val after = p.money - tile.price
        val group = tile.group
        if (group != null) {
            val completes = Board.groupTiles(group).filter { it != pending.tile }.all { s.tiles[it].owner == p.id }
            if (completes && after >= 0) return true
        }
        return after >= RESERVE_BUY
    }

    /** Cấp muốn xây, hoặc null nếu không xây. */
    fun buildChoice(game: Game): Int? {
        val p = game.current
        val pending = game.state.pending ?: return null
        return pending.options.lastOrNull { p.money - it.cost >= RESERVE_BUILD }?.level
    }

    /** Ô muốn xây thêm trong pha quản lý, hoặc null. */
    fun nextBuild(game: Game): Int? {
        val p = game.current
        return game.ownedTiles(p.id)
            .filter { game.canBuildFreely(p.id, it) && p.money - game.buildCost(it) >= RESERVE_BUILD }
            .sortedWith(compareBy<Int> { game.state.tiles[it].level }.thenByDescending { Board.TILES[it].price })
            .firstOrNull()
    }
}
