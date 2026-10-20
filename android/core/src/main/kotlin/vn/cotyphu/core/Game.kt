package vn.cotyphu.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

// Engine luật chơi — port từ vietnam-3d/js/core/game.js.
// Mọi hành động trả về danh sách GameEvent để tầng hiển thị diễn hoạt lần lượt.
// GameState là dữ liệu thuần, tuần tự hoá JSON để lưu/khôi phục ván.

@Serializable
enum class Phase { ROLL, DECIDE, END, OVER }

@Serializable
data class JailCard(val deck: Deck, val card: Int)

@Serializable
data class PlayerState(
    val id: Int,
    val name: String,
    val color: Long,
    val token: String,
    val isBot: Boolean,
    var money: Int = START_MONEY,
    var pos: Int = 0,
    var inJail: Boolean = false,
    var jailTurns: Int = 0,
    val jailFree: MutableList<JailCard> = mutableListOf(),
    var bankrupt: Boolean = false,
)

@Serializable
data class TileState(var owner: Int? = null, var level: Int = 0)

@Serializable
data class BuildOption(val level: Int, val cost: Int)

@Serializable
enum class PendingKind { BUY, BUILD }

@Serializable
data class Pending(val kind: PendingKind, val tile: Int, val price: Int = 0, val options: List<BuildOption> = emptyList())

@Serializable
data class DeckState(var order: MutableList<Int>, var next: Int = 0)

@Serializable
data class GameState(
    val players: List<PlayerState>,
    val tiles: List<TileState>,
    var current: Int = 0,
    var phase: Phase = Phase.ROLL,
    var pending: Pending? = null,
    var doubles: Int = 0,
    var rollAgain: Boolean = false,
    var pot: Int = 0,
    var round: Int = 1,
    val maxRounds: Int = 0,
    var dice: List<Int> = listOf(1, 1),
    val decks: MutableMap<Deck, DeckState>,
    var winner: Int? = null,
)

data class PlayerSetup(val name: String, val color: Long, val token: String, val isBot: Boolean = false)

data class Ranked(val id: Int, val worth: Int)

enum class ReleaseReason { DOUBLES, FINE, CARD }

sealed interface GameEvent {
    data class Dice(val player: Int, val d1: Int, val d2: Int) : GameEvent
    data class Move(val player: Int, val from: Int, val to: Int, val path: List<Int>) : GameEvent
    data class Money(val player: Int, val amount: Int, val reason: String) : GameEvent
    data class Pot(val pot: Int) : GameEvent
    data class Buy(val player: Int, val tile: Int) : GameEvent
    data class Build(val player: Int, val tile: Int, val level: Int) : GameEvent
    data class SellBuilding(val player: Int, val tile: Int, val level: Int) : GameEvent
    data class SellProperty(val player: Int, val tile: Int) : GameEvent
    data class Rent(val player: Int, val owner: Int, val tile: Int, val amount: Int) : GameEvent
    data class CardDrawn(val player: Int, val deck: Deck, val text: String) : GameEvent
    data class Jail(val player: Int, val from: Int, val to: Int) : GameEvent
    data class JailRelease(val player: Int, val reason: ReleaseReason) : GameEvent
    data class JailStay(val player: Int, val turnsLeft: Int) : GameEvent
    data class JailForcedFine(val player: Int, val amount: Int) : GameEvent
    data class ThreeDoubles(val player: Int) : GameEvent
    data class CantAfford(val player: Int, val tile: Int, val price: Int) : GameEvent
    data class Decline(val player: Int, val kind: PendingKind, val tile: Int) : GameEvent
    data class JailFreeCard(val player: Int) : GameEvent
    data class Bankrupt(val player: Int) : GameEvent
    data class GameOver(val winner: Int, val byRounds: Boolean, val ranking: List<Ranked> = emptyList()) : GameEvent
    data class Turn(val player: Int, val round: Int) : GameEvent
    data class RollAgain(val player: Int) : GameEvent
}

class GameException(message: String) : IllegalStateException(message)

private sealed interface Creditor {
    data object Pot : Creditor
    data class Player(val id: Int) : Creditor
}

private data class LandingContext(val diceTotal: Int, val rentMultiplier: Int = 1)

class Game private constructor(val state: GameState, private val rng: () -> Double) {

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun newGame(players: List<PlayerSetup>, maxRounds: Int = 0, rng: () -> Double = { Math.random() }): Game {
            if (players.size !in 2..4) throw GameException("Cần từ 2 đến 4 người chơi")
            val state = GameState(
                players = players.mapIndexed { id, p -> PlayerState(id, p.name, p.color, p.token, p.isBot) },
                tiles = Board.TILES.map { TileState() },
                maxRounds = maxRounds,
                decks = mutableMapOf(
                    Deck.CHANCE to DeckState(shuffled(Cards.CHANCE.size, rng)),
                    Deck.COMMUNITY to DeckState(shuffled(Cards.COMMUNITY.size, rng)),
                ),
            )
            return Game(state, rng)
        }

        fun fromJson(text: String, rng: () -> Double = { Math.random() }): Game =
            Game(json.decodeFromString<GameState>(text), rng)

        fun fromState(state: GameState, rng: () -> Double = { Math.random() }): Game =
            Game(json.decodeFromString<GameState>(json.encodeToString(state)), rng)

        private fun shuffled(n: Int, rng: () -> Double): MutableList<Int> {
            val a = MutableList(n) { it }
            for (i in n - 1 downTo 1) {
                val j = (rng() * (i + 1)).toInt()
                a[i] = a[j].also { a[j] = a[i] }
            }
            return a
        }
    }

    fun toJson(): String = json.encodeToString(state)

    // ---------- truy vấn ----------

    val current: PlayerState get() = state.players[state.current]

    fun player(id: Int) = state.players[id]

    fun activePlayers() = state.players.filter { !it.bankrupt }

    fun ownsGroup(playerId: Int, group: String) =
        Board.groupTiles(group).all { state.tiles[it].owner == playerId }

    fun countOwned(playerId: Int, type: TileType) =
        Board.TILES.indices.count { Board.TILES[it].type == type && state.tiles[it].owner == playerId }

    fun rentFor(index: Int, diceTotal: Int = state.dice.sum(), multiplier: Int = 1): Int {
        val tile = Board.TILES[index]
        val t = state.tiles[index]
        val owner = t.owner ?: return 0
        val rent = when (tile.type) {
            TileType.PROPERTY -> {
                val r = tile.rent[t.level]
                if (t.level == 0 && ownsGroup(owner, tile.group!!)) r * 2 else r
            }
            TileType.TRANSPORT -> 25 * (1 shl (countOwned(owner, TileType.TRANSPORT) - 1))
            TileType.UTILITY -> diceTotal * (if (countOwned(owner, TileType.UTILITY) == 2) 10 else 4)
            else -> 0
        }
        return rent * multiplier
    }

    fun buildCost(index: Int) = Board.GROUPS.getValue(Board.TILES[index].group!!).buildCost

    fun netWorth(playerId: Int): Int {
        var total = player(playerId).money
        state.tiles.forEachIndexed { i, t ->
            if (t.owner != playerId) return@forEachIndexed
            total += Board.TILES[i].price
            if (t.level > 0) total += t.level * buildCost(i)
        }
        return total
    }

    fun ownedTiles(playerId: Int) = state.tiles.indices.filter { state.tiles[it].owner == playerId }

    /** Ô có thể xây thêm qua bảng quản lý (cần sở hữu trọn vùng). */
    fun canBuildFreely(playerId: Int, index: Int): Boolean {
        val tile = Board.TILES[index]
        val t = state.tiles[index]
        return tile.type == TileType.PROPERTY && t.owner == playerId && t.level < MAX_LEVEL &&
            ownsGroup(playerId, tile.group!!) && player(playerId).money >= buildCost(index)
    }

    /** Quản lý tài sản chỉ được phép trong lượt của mình, khi không chờ quyết định. */
    fun canManage(playerId: Int) =
        state.current == playerId && (state.phase == Phase.ROLL || state.phase == Phase.END)

    // ---------- hành động ----------

    fun roll(forced: Pair<Int, Int>? = null): List<GameEvent> {
        val s = state
        if (s.phase != Phase.ROLL) throw GameException("Không thể đổ xúc xắc ở pha ${s.phase}")
        val p = current
        val d1 = forced?.first ?: (1 + (rng() * 6).toInt())
        val d2 = forced?.second ?: (1 + (rng() * 6).toInt())
        val isDouble = d1 == d2
        val total = d1 + d2
        s.dice = listOf(d1, d2)
        val events = mutableListOf<GameEvent>(GameEvent.Dice(p.id, d1, d2))

        if (p.inJail) {
            s.rollAgain = false
            if (isDouble) {
                p.inJail = false
                p.jailTurns = 0
                events += GameEvent.JailRelease(p.id, ReleaseReason.DOUBLES)
            } else {
                p.jailTurns += 1
                if (p.jailTurns < MAX_JAIL_TURNS) {
                    events += GameEvent.JailStay(p.id, MAX_JAIL_TURNS - p.jailTurns)
                    s.phase = Phase.END
                    return events
                }
                events += GameEvent.JailForcedFine(p.id, JAIL_FINE)
                pay(p, JAIL_FINE, Creditor.Pot, events, "Nộp phạt ra tù")
                if (p.bankrupt) return finishAction(events)
                p.inJail = false
                p.jailTurns = 0
                events += GameEvent.JailRelease(p.id, ReleaseReason.FINE)
            }
        } else if (isDouble) {
            s.doubles += 1
            if (s.doubles >= 3) {
                events += GameEvent.ThreeDoubles(p.id)
                sendToJail(p, events)
                s.rollAgain = false
                return finishAction(events)
            }
            s.rollAgain = true
        } else {
            s.rollAgain = false
        }

        moveBy(p, total, events)
        resolveLanding(p, events, LandingContext(total))
        return finishAction(events)
    }

    fun payJailFine(): List<GameEvent> {
        val p = current
        if (state.phase != Phase.ROLL || !p.inJail) throw GameException("Không ở trong tù")
        if (p.money < JAIL_FINE) throw GameException("Không đủ tiền nộp phạt")
        val events = mutableListOf<GameEvent>()
        pay(p, JAIL_FINE, Creditor.Pot, events, "Nộp phạt ra tù")
        p.inJail = false
        p.jailTurns = 0
        events += GameEvent.JailRelease(p.id, ReleaseReason.FINE)
        return events
    }

    fun useJailCard(): List<GameEvent> {
        val p = current
        if (state.phase != Phase.ROLL || !p.inJail || p.jailFree.isEmpty()) throw GameException("Không thể dùng thẻ ân xá")
        p.jailFree.removeAt(0)
        p.inJail = false
        p.jailTurns = 0
        return listOf(GameEvent.JailRelease(p.id, ReleaseReason.CARD))
    }

    /** Trả lời quyết định đang chờ: mua (true/false) hoặc cấp muốn xây (null = thôi). */
    fun decideBuy(accept: Boolean) = decide(if (accept) 1 else null, PendingKind.BUY)

    fun decideBuild(level: Int?) = decide(level, PendingKind.BUILD)

    private fun decide(choice: Int?, expected: PendingKind): List<GameEvent> {
        val s = state
        val pending = s.pending
        if (s.phase != Phase.DECIDE || pending == null) throw GameException("Không có quyết định nào đang chờ")
        if (pending.kind != expected) throw GameException("Quyết định không khớp")
        val p = current
        val events = mutableListOf<GameEvent>()
        s.pending = null
        if (pending.kind == PendingKind.BUY && choice != null) {
            val tile = Board.TILES[pending.tile]
            if (p.money >= tile.price) {
                p.money -= tile.price
                s.tiles[pending.tile].owner = p.id
                events += GameEvent.Money(p.id, -tile.price, "Mua ${tile.name}")
                events += GameEvent.Buy(p.id, pending.tile)
            }
        } else if (pending.kind == PendingKind.BUILD && choice != null) {
            val opt = pending.options.firstOrNull { it.level == choice }
            if (opt != null && p.money >= opt.cost) build(p, pending.tile, opt.level, opt.cost, events)
        }
        if (events.isEmpty()) events += GameEvent.Decline(p.id, pending.kind, pending.tile)
        s.phase = Phase.END
        return events
    }

    /** Xây thêm 1 cấp khi sở hữu trọn vùng (bảng quản lý tài sản). */
    fun buildOn(index: Int): List<GameEvent> {
        val p = current
        if (!canManage(p.id) || !canBuildFreely(p.id, index)) throw GameException("Không thể xây ở đây")
        val events = mutableListOf<GameEvent>()
        build(p, index, state.tiles[index].level + 1, buildCost(index), events)
        return events
    }

    /** Bán bớt 1 cấp công trình, nhận lại 50% chi phí xây. */
    fun sellBuilding(index: Int): List<GameEvent> {
        val p = current
        val t = state.tiles[index]
        if (!canManage(p.id) || t.owner != p.id || t.level == 0) throw GameException("Không có công trình để bán")
        val events = mutableListOf<GameEvent>()
        sellOneLevel(p, index, events)
        return events
    }

    /** Bán đất (chưa có công trình) cho ngân hàng, nhận lại 50% giá. */
    fun sellProperty(index: Int): List<GameEvent> {
        val p = current
        val t = state.tiles[index]
        if (!canManage(p.id) || t.owner != p.id || t.level > 0) throw GameException("Không thể bán ô đất này")
        val events = mutableListOf<GameEvent>()
        sellLand(p, index, events)
        return events
    }

    fun endTurn(): List<GameEvent> {
        val s = state
        if (s.phase != Phase.END) throw GameException("Không thể kết thúc lượt ở pha ${s.phase}")
        val events = mutableListOf<GameEvent>()
        val p = current
        if (s.rollAgain && !p.bankrupt && !p.inJail) {
            s.rollAgain = false
            s.phase = Phase.ROLL
            events += GameEvent.RollAgain(p.id)
            return events
        }
        s.doubles = 0
        s.rollAgain = false
        var next = s.current
        do {
            next = (next + 1) % s.players.size
            if (next == 0) s.round += 1
        } while (s.players[next].bankrupt)
        if (s.maxRounds > 0 && s.round > s.maxRounds) {
            endGameByWorth(events)
            return events
        }
        s.current = next
        s.phase = Phase.ROLL
        events += GameEvent.Turn(next, s.round)
        return events
    }

    // ---------- nội bộ ----------

    private fun finishAction(events: MutableList<GameEvent>): List<GameEvent> {
        val s = state
        if (s.phase == Phase.OVER) return events
        if (current.bankrupt) {
            s.pending = null
            s.rollAgain = false
            s.phase = Phase.END
        } else {
            s.phase = if (s.pending != null) Phase.DECIDE else Phase.END
        }
        return events
    }

    internal fun moveBy(p: PlayerState, steps: Int, events: MutableList<GameEvent>, collectGo: Boolean = true) {
        val from = p.pos
        val dir = steps.sign
        val path = mutableListOf<Int>()
        var pos = from
        var passedGo = false
        repeat(abs(steps)) {
            pos = (pos + dir + Board.SIZE) % Board.SIZE
            path += pos
            if (dir > 0 && pos == 0) passedGo = true
        }
        p.pos = pos
        events += GameEvent.Move(p.id, from, pos, path)
        if (passedGo && collectGo) {
            p.money += START_SALARY
            events += GameEvent.Money(p.id, START_SALARY, "Qua ô Xuất phát")
        }
    }

    internal fun moveTo(p: PlayerState, target: Int, events: MutableList<GameEvent>) {
        moveBy(p, (target - p.pos + Board.SIZE) % Board.SIZE, events)
    }

    private fun sendToJail(p: PlayerState, events: MutableList<GameEvent>) {
        p.inJail = true
        p.jailTurns = 0
        state.doubles = 0
        state.rollAgain = false
        val from = p.pos
        p.pos = JAIL_INDEX
        events += GameEvent.Jail(p.id, from, JAIL_INDEX)
    }

    internal fun resolveLanding(p: PlayerState, events: MutableList<GameEvent>, diceTotal: Int) =
        resolveLanding(p, events, LandingContext(diceTotal))

    private fun resolveLanding(p: PlayerState, events: MutableList<GameEvent>, ctx: LandingContext) {
        val s = state
        val index = p.pos
        val tile = Board.TILES[index]

        if (tile.ownable) {
            val t = s.tiles[index]
            val owner = t.owner
            when {
                owner == null ->
                    if (p.money >= tile.price) s.pending = Pending(PendingKind.BUY, index, tile.price)
                    else events += GameEvent.CantAfford(p.id, index, tile.price)
                owner == p.id ->
                    if (tile.type == TileType.PROPERTY && t.level < MAX_LEVEL) {
                        val cost = buildCost(index)
                        if (p.money >= cost) s.pending = Pending(PendingKind.BUILD, index, options = listOf(BuildOption(t.level + 1, cost)))
                    }
                else -> {
                    val rent = rentFor(index, ctx.diceTotal, ctx.rentMultiplier)
                    events += GameEvent.Rent(p.id, owner, index, rent)
                    pay(p, rent, Creditor.Player(owner), events, "Thuê ${tile.name}")
                }
            }
            return
        }

        when (tile.type) {
            TileType.TAX -> pay(p, tile.amount, Creditor.Pot, events, tile.name)
            TileType.CHANCE -> drawCard(p, Deck.CHANCE, events, ctx)
            TileType.COMMUNITY -> drawCard(p, Deck.COMMUNITY, events, ctx)
            TileType.GOTOJAIL -> sendToJail(p, events)
            TileType.PARKING -> if (s.pot > 0) {
                val amount = s.pot
                s.pot = 0
                p.money += amount
                events += GameEvent.Money(p.id, amount, "Nhận Quỹ chung")
                events += GameEvent.Pot(0)
            }
            else -> Unit
        }
    }

    private fun drawCard(p: PlayerState, deckName: Deck, events: MutableList<GameEvent>, ctx: LandingContext) {
        val s = state
        val deck = s.decks.getValue(deckName)
        val cards = Cards.of(deckName)
        var cardIndex = 0
        // Bỏ qua thẻ ân xá đang nằm trong tay người chơi khác.
        for (tries in cards.indices) {
            cardIndex = deck.order[deck.next]
            deck.next = (deck.next + 1) % deck.order.size
            if (deck.next == 0) deck.order = shuffled(cards.size, rng)
            val held = s.players.any { q -> q.jailFree.any { it.deck == deckName && it.card == cardIndex } }
            if (!held) break
        }
        val card = cards[cardIndex]
        events += GameEvent.CardDrawn(p.id, deckName, card.text)
        val label = "Thẻ ${deckName.label}"
        when (val a = card.action) {
            is CardAction.Money ->
                if (a.amount >= 0) {
                    p.money += a.amount
                    events += GameEvent.Money(p.id, a.amount, label)
                } else {
                    pay(p, -a.amount, Creditor.Pot, events, label)
                }
            is CardAction.MoveTo -> {
                moveTo(p, a.target, events)
                resolveLanding(p, events, ctx)
            }
            is CardAction.MoveBy -> {
                moveBy(p, a.steps, events)
                resolveLanding(p, events, ctx)
            }
            is CardAction.Nearest -> {
                var target = p.pos
                do { target = (target + 1) % Board.SIZE } while (Board.TILES[target].type != a.type)
                moveTo(p, target, events)
                resolveLanding(p, events, ctx.copy(rentMultiplier = 2))
            }
            CardAction.Jail -> sendToJail(p, events)
            CardAction.JailFree -> {
                p.jailFree += JailCard(deckName, cardIndex)
                events += GameEvent.JailFreeCard(p.id)
            }
            is CardAction.Repairs -> {
                var cost = 0
                s.tiles.forEach { t ->
                    if (t.owner != p.id || t.level == 0) return@forEach
                    cost += if (t.level == MAX_LEVEL) a.hotel else t.level * a.house
                }
                if (cost > 0) pay(p, cost, Creditor.Pot, events, "Sửa chữa công trình")
            }
            is CardAction.PayEach -> for (q in activePlayers()) {
                if (q.id == p.id || p.bankrupt) continue
                pay(p, a.amount, Creditor.Player(q.id), events, "Bao ${q.name}")
            }
            is CardAction.CollectEach -> for (q in activePlayers()) {
                if (q.id == p.id) continue
                pay(q, a.amount, Creditor.Player(p.id), events, "Tặng ${p.name}")
            }
        }
    }

    private fun build(p: PlayerState, index: Int, level: Int, cost: Int, events: MutableList<GameEvent>) {
        p.money -= cost
        state.tiles[index].level = level
        events += GameEvent.Money(p.id, -cost, "Xây dựng tại ${Board.TILES[index].name}")
        events += GameEvent.Build(p.id, index, level)
    }

    private fun sellOneLevel(p: PlayerState, index: Int, events: MutableList<GameEvent>) {
        val t = state.tiles[index]
        val refund = buildCost(index) / 2
        t.level -= 1
        p.money += refund
        events += GameEvent.SellBuilding(p.id, index, t.level)
        events += GameEvent.Money(p.id, refund, "Bán công trình ${Board.TILES[index].name}")
    }

    private fun sellLand(p: PlayerState, index: Int, events: MutableList<GameEvent>) {
        val t = state.tiles[index]
        val refund = Board.TILES[index].price / 2
        t.owner = null
        t.level = 0
        p.money += refund
        events += GameEvent.SellProperty(p.id, index)
        events += GameEvent.Money(p.id, refund, "Bán đất ${Board.TILES[index].name}")
    }

    /** Tự động thanh lý để trả nợ: bán công trình trước, rồi bán đất rẻ trước. */
    private fun liquidate(p: PlayerState, needed: Int, events: MutableList<GameEvent>) {
        while (p.money < needed) {
            val built = ownedTiles(p.id).filter { state.tiles[it].level > 0 }
                .sortedByDescending { state.tiles[it].level }
            if (built.isEmpty()) break
            sellOneLevel(p, built[0], events)
        }
        while (p.money < needed) {
            val lands = ownedTiles(p.id).sortedBy { Board.TILES[it].price }
            if (lands.isEmpty()) break
            sellLand(p, lands[0], events)
        }
    }

    private fun pay(p: PlayerState, amount: Int, to: Creditor, events: MutableList<GameEvent>, reason: String) {
        if (amount <= 0 || p.bankrupt) return
        if (p.money < amount) liquidate(p, amount, events)
        val paid = min(amount, p.money)
        p.money -= paid
        events += GameEvent.Money(p.id, -paid, reason)
        when (to) {
            Creditor.Pot -> {
                state.pot += paid
                events += GameEvent.Pot(state.pot)
            }
            is Creditor.Player -> {
                val q = player(to.id)
                q.money += paid
                events += GameEvent.Money(q.id, paid, reason)
            }
        }
        if (paid < amount) bankrupt(p, events)
    }

    private fun bankrupt(p: PlayerState, events: MutableList<GameEvent>) {
        p.bankrupt = true
        p.money = 0
        p.jailFree.clear()
        p.inJail = false
        state.tiles.forEachIndexed { i, t ->
            if (t.owner == p.id) {
                t.owner = null
                t.level = 0
                events += GameEvent.SellProperty(p.id, i)
            }
        }
        events += GameEvent.Bankrupt(p.id)
        val alive = activePlayers()
        if (alive.size == 1) {
            state.phase = Phase.OVER
            state.pending = null
            state.winner = alive[0].id
            events += GameEvent.GameOver(alive[0].id, byRounds = false)
        }
    }

    private fun endGameByWorth(events: MutableList<GameEvent>) {
        val ranking = activePlayers().map { Ranked(it.id, netWorth(it.id)) }.sortedByDescending { it.worth }
        state.phase = Phase.OVER
        state.winner = ranking[0].id
        events += GameEvent.GameOver(ranking[0].id, byRounds = true, ranking = ranking)
    }
}
