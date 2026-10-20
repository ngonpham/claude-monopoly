package vn.cotyphu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Port từ vietnam-3d/tests/game.test.js — cùng kịch bản, cùng kỳ vọng.

private val PLAYERS = listOf(
    PlayerSetup("An", 0xFFE53935, "hat"),
    PlayerSetup("Bình", 0xFF1E88E5, "lantern"),
)

/** Bộ sinh số giả ngẫu nhiên LCG giống hệt bản JS để tái lập kết quả. */
private fun seeded(seed: Int): () -> Double {
    var s = seed.toLong() and 0xFFFFFFFFL
    return {
        s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
        s / 4294967296.0
    }
}

private fun newGame(players: List<PlayerSetup> = PLAYERS, maxRounds: Int = 0) =
    Game.newGame(players, maxRounds, seeded(42))

class GameTest {

    @Test
    fun `bàn cờ có 40 ô với 22 ô đất, 4 giao thông, 2 tiện ích`() {
        assertEquals(40, Board.SIZE)
        assertEquals(22, Board.TILES.count { it.type == TileType.PROPERTY })
        assertEquals(4, Board.TILES.count { it.type == TileType.TRANSPORT })
        assertEquals(2, Board.TILES.count { it.type == TileType.UTILITY })
        assertEquals(TileType.GO, Board.TILES[0].type)
        assertEquals(TileType.JAIL, Board.TILES[10].type)
        assertEquals(TileType.PARKING, Board.TILES[20].type)
        assertEquals(TileType.GOTOJAIL, Board.TILES[30].type)
        assertEquals(listOf(6, 30, 90, 240, 360), Board.TILES[1].rent)
        assertEquals(listOf(40, 200, 600, 1600, 2400), Board.TILES[39].rent)
    }

    @Test
    fun `mua đất và trả tiền thuê`() {
        val g = newGame()
        g.roll(1 to 2) // An tới ô 3 Thác Dray Nur
        assertEquals(Phase.DECIDE, g.state.phase)
        assertEquals(PendingKind.BUY, g.state.pending!!.kind)
        g.decideBuy(true)
        assertEquals(START_MONEY - 60, g.player(0).money)
        assertEquals(0, g.state.tiles[3].owner)
        g.endTurn()

        g.roll(2 to 1) // Bình cũng tới ô 3
        val rent = Board.TILES[3].rent[0]
        assertEquals(START_MONEY - rent, g.player(1).money)
        assertEquals(START_MONEY - 60 + rent, g.player(0).money)
    }

    @Test
    fun `sở hữu trọn vùng nhân đôi tiền thuê đất trống và cho phép xây tự do`() {
        val g = newGame()
        g.state.tiles[1].owner = 0
        g.state.tiles[3].owner = 0
        assertEquals(Board.TILES[3].rent[0] * 2, g.rentFor(3))
        assertTrue(g.canBuildFreely(0, 1))
        g.buildOn(1)
        assertEquals(1, g.state.tiles[1].level)
        assertEquals(START_MONEY - 50, g.player(0).money)
        assertEquals(Board.TILES[1].rent[1], g.rentFor(1))
    }

    @Test
    fun `dừng trên đất của mình được nâng 1 cấp`() {
        val g = newGame()
        g.state.tiles[3].owner = 0
        g.roll(1 to 2)
        assertEquals(PendingKind.BUILD, g.state.pending!!.kind)
        assertEquals(listOf(BuildOption(1, 50)), g.state.pending!!.options)
        g.decideBuild(1)
        assertEquals(1, g.state.tiles[3].level)
    }

    @Test
    fun `đi qua Xuất phát nhận lương`() {
        val g = newGame()
        g.player(0).pos = 35
        val ev = g.roll(2 to 4) // 35 -> 1
        assertEquals(1, g.player(0).pos)
        assertTrue(ev.any { it is GameEvent.Money && it.reason == "Qua ô Xuất phát" && it.amount == 200 })
        assertEquals(START_MONEY + 200, g.player(0).money)
    }

    @Test
    fun `thẻ lùi bước không nhận lương, tiến tới ô có chủ thì trả tiền thuê`() {
        val g = newGame()
        val p = g.player(0)
        p.pos = 1
        val back = mutableListOf<GameEvent>()
        g.moveBy(p, -3, back)
        assertEquals(38, p.pos)
        assertTrue(back.none { it is GameEvent.Money })
        g.state.tiles[39].owner = 1
        val ev = mutableListOf<GameEvent>()
        g.moveTo(p, 39, ev)
        g.resolveLanding(p, ev, 7)
        assertTrue(ev.any { it is GameEvent.Rent && it.amount == Board.TILES[39].rent[0] })
    }

    @Test
    fun `ô Vào tù đưa vào Nhà tạm giữ, ra tù sau 3 lượt bằng nộp phạt`() {
        val g = newGame()
        g.player(0).pos = 25
        g.roll(2 to 3) // -> 30
        assertEquals(10, g.player(0).pos)
        assertTrue(g.player(0).inJail)
        g.endTurn()
        fun other() {
            g.roll(1 to 2)
            if (g.state.phase == Phase.DECIDE) g.decideBuy(false)
            g.endTurn()
        }
        other()
        g.roll(1 to 2); assertTrue(g.player(0).inJail); g.endTurn()
        other()
        g.roll(1 to 2); assertTrue(g.player(0).inJail); g.endTurn()
        other()
        val ev = g.roll(1 to 2) // lần thử thứ 3: buộc nộp phạt rồi đi
        assertTrue(ev.any { it is GameEvent.JailForcedFine })
        assertTrue(!g.player(0).inJail)
        assertEquals(13, g.player(0).pos)
    }

    @Test
    fun `đổ đôi 3 lần liên tiếp thì vào tù`() {
        val g = newGame()
        fun skip() { if (g.state.phase == Phase.DECIDE) g.decideBuy(false) }
        g.roll(1 to 1); skip(); g.endTurn()
        assertEquals(0, g.state.current, "đổ đôi được đi tiếp")
        g.roll(2 to 2); skip(); g.endTurn()
        val ev = g.roll(3 to 3)
        assertTrue(ev.any { it is GameEvent.ThreeDoubles })
        assertTrue(g.player(0).inJail)
        g.endTurn()
        assertEquals(1, g.state.current)
    }

    @Test
    fun `thuế vào Quỹ chung, Bến Ninh Kiều nhận Quỹ`() {
        val g = newGame()
        g.roll(1 to 3) // ô 4 thuế 200
        assertEquals(200, g.state.pot)
        g.endTurn()
        g.player(1).pos = 17
        g.roll(1 to 2) // ô 20
        assertEquals(0, g.state.pot)
        assertEquals(START_MONEY + 200, g.player(1).money)
    }

    @Test
    fun `phá sản tự thanh lý tài sản, hết tiền thì thua và kết thúc game`() {
        val g = newGame()
        g.state.tiles[39].apply { owner = 1; level = 4 }
        g.state.tiles[1].apply { owner = 0; level = 2 }
        g.player(0).money = 100
        g.player(0).pos = 36
        val ev = g.roll(1 to 2) // 36 -> 39
        assertTrue(ev.any { it is GameEvent.SellBuilding })
        assertTrue(ev.any { it is GameEvent.Bankrupt && it.player == 0 })
        assertEquals(Phase.OVER, g.state.phase)
        assertEquals(1, g.state.winner)
        assertNull(g.state.tiles[1].owner)
    }

    @Test
    fun `giới hạn số vòng thì người giàu nhất thắng`() {
        val g = newGame(maxRounds = 1)
        g.player(1).money = 5000
        g.roll(1 to 3); g.endTurn()
        g.roll(1 to 3); g.endTurn()
        assertEquals(Phase.OVER, g.state.phase)
        assertEquals(1, g.state.winner)
    }

    @Test
    fun `lưu JSON và khôi phục đúng trạng thái`() {
        val g = newGame()
        g.roll(1 to 2); g.decideBuy(true); g.endTurn()
        g.player(0).jailFree += JailCard(Deck.CHANCE, 8)
        val g2 = Game.fromJson(g.toJson())
        assertEquals(g.state, g2.state)
        g2.roll(2 to 1)
        assertNotEquals(g.state, g2.state)
    }

    @Test
    fun `mô phỏng 300 ván bot bảo toàn bất biến và luôn kết thúc`() {
        val four = listOf("A", "B", "C", "D").mapIndexed { i, n -> PlayerSetup(n, 0xFF000000 + i, "hat", isBot = true) }
        var finished = 0
        for (seed in 1..300) {
            val g = Game.newGame(four.take(2 + seed % 3), maxRounds = 150, rng = seeded(seed))
            var steps = 0
            while (g.state.phase != Phase.OVER && steps < 20000) {
                steps++
                when (g.state.phase) {
                    Phase.ROLL -> {
                        when (Bot.jailChoice(g)) {
                            JailChoice.CARD -> g.useJailCard()
                            JailChoice.FINE -> g.payJailFine()
                            JailChoice.ROLL -> Unit
                        }
                        g.roll()
                    }
                    Phase.DECIDE ->
                        if (g.state.pending!!.kind == PendingKind.BUY) g.decideBuy(Bot.wantsToBuy(g))
                        else g.decideBuild(Bot.buildChoice(g))
                    Phase.END -> {
                        while (true) g.buildOn(Bot.nextBuild(g) ?: break)
                        g.endTurn()
                    }
                    Phase.OVER -> Unit
                }
                for (p in g.state.players) {
                    assertTrue(p.money >= 0, "tiền âm (seed $seed)")
                    assertTrue(p.pos in 0 until 40)
                    if (p.bankrupt) assertTrue(g.state.tiles.none { it.owner == p.id })
                }
                g.state.tiles.forEachIndexed { i, t ->
                    if (t.owner == null) assertEquals(0, t.level)
                    if (t.level > 0) assertEquals(TileType.PROPERTY, Board.TILES[i].type)
                    assertTrue(t.level <= MAX_LEVEL)
                }
            }
            assertEquals(Phase.OVER, g.state.phase, "ván seed $seed không kết thúc")
            assertNotNull(g.state.winner)
            finished++
        }
        assertEquals(300, finished)
    }

    @Test
    fun `định dạng tiền kiểu Việt Nam`() {
        assertEquals("1.500 tr₫", formatMoney(1500))
        assertEquals("-60 tr₫", formatMoney(-60))
    }
}
