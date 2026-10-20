package vn.cotyphu.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.cotyphu.core.Board
import vn.cotyphu.core.Bot
import vn.cotyphu.core.Deck
import vn.cotyphu.core.Game
import vn.cotyphu.core.GameEvent
import vn.cotyphu.core.JAIL_FINE
import vn.cotyphu.core.JAIL_INDEX
import vn.cotyphu.core.JailChoice
import vn.cotyphu.core.MAX_JAIL_TURNS
import vn.cotyphu.core.PendingKind
import vn.cotyphu.core.Phase
import vn.cotyphu.core.PlayerSetup
import vn.cotyphu.core.PlayerState
import vn.cotyphu.core.ReleaseReason
import vn.cotyphu.core.formatMoney
import vn.cotyphu.gl.Engine
import vn.cotyphu.gl.Vec3
import vn.cotyphu.render.GameWorld
import vn.cotyphu.render.TILE_TOP
import vn.cotyphu.render.tileAt
import kotlin.math.abs

// Bộ điều khiển ván chơi — port từ vietnam-3d/js/main.js: nối engine luật chơi với cảnh 3D và giao diện.

val LEVEL_NAMES = listOf("Đất trống", "1 nhà phố", "2 nhà phố", "3 nhà phố", "Khách sạn")
val PLAYER_COLORS = listOf(0xFFE53935, 0xFF1E88E5, 0xFF8E24AA, 0xFF00897B)

enum class Tone { NORMAL, GOOD, BAD }
enum class ButtonStyle { PRIMARY, NORMAL, GHOST }

data class LogEntry(val id: Long, val text: AnnotatedString, val tone: Tone)
data class ActionButton(val label: String, val style: ButtonStyle = ButtonStyle.NORMAL, val enabled: Boolean = true, val onClick: () -> Unit)
data class FloatText(val id: Long, val x: Float, val y: Float, val text: String, val good: Boolean)
data class CardDialog(val deck: Deck, val text: String, val player: String, val done: CompletableDeferred<Unit>)
data class SetupRow(val name: String, val token: String, val isBot: Boolean)

/** Dựng chuỗi có định dạng: tên người chơi tô màu, phần nhấn mạnh in đậm. */
class Msg(private val accent: Color?) {
    private val b = AnnotatedString.Builder()
    fun t(s: String) = apply { b.append(s) }
    fun b(s: String) = apply { b.withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = accent ?: Color.Unspecified)) { append(s) } }
    fun who(p: PlayerState) = apply { b.withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(p.color.toInt()))) { append(p.name) } }
    fun build() = b.toAnnotatedString()
}

fun msg(accent: Color? = null, block: Msg.() -> Unit): AnnotatedString = Msg(accent).apply(block).build()

class GameController(
    private val engine: Engine,
    private val sound: Sound,
    private val store: SaveStore,
    private val scope: CoroutineScope,
    private val density: Float,
) {
    // ---------- trạng thái cho Compose ----------
    var game by mutableStateOf<Game?>(null)
        private set
    /** Tăng mỗi khi trạng thái ván đổi để giao diện vẽ lại. */
    var version by mutableIntStateOf(0)
        private set
    val displayedMoney = mutableStateListOf<Int>()
    var displayedPot by mutableIntStateOf(0)
        private set
    var actionText by mutableStateOf(AnnotatedString("Đang dựng bàn cờ…"))
        private set
    var actions by mutableStateOf<List<ActionButton>>(emptyList())
        private set
    val log = mutableStateListOf<LogEntry>()
    var toast by mutableStateOf<String?>(null)
        private set
    var tileCard by mutableStateOf<Int?>(null)
        private set
    var tilePinned by mutableStateOf(false)
        private set
    var cardDialog by mutableStateOf<CardDialog?>(null)
        private set
    var showManage by mutableStateOf(false)
    var showSetup by mutableStateOf(true)
    var showHelp by mutableStateOf(false)
    var showGameOver by mutableStateOf(false)
    var showLog by mutableStateOf(false)
    var loading by mutableStateOf(true)
        private set
    val floats = mutableStateListOf<FloatText>()
    var follow by mutableStateOf(true)
        private set
    var muted by mutableStateOf(store.muted)
        private set
    var botSpeed by mutableFloatStateOf(store.botSpeed)
    val setupRows = mutableStateListOf(
        SetupRow("Bạn", "hat", false), SetupRow("Máy Hà Nội", "lantern", true), SetupRow("Máy Huế", "drum", true),
    )
    var maxRounds by mutableIntStateOf(30)
    val hasSave get() = store.savedGame != null

    private var world: GameWorld? = null
    private var busy = false
    private var gameId = 0
    private var nextId = 0L
    private var toastJob: Job? = null

    init {
        sound.muted = muted
        scope.launch {
            // dựng toàn bộ cảnh và vẽ texture trên luồng nền, sau đó gắn vào engine
            val w = withContext(Dispatchers.Default) { GameWorld(engine).also { it.prepare() } }
            world = w
            w.attach()
            engine.run { }
            loading = false
            setActions(AnnotatedString("Thiết lập người chơi để bắt đầu hành trình."), emptyList())
        }
    }

    fun colorOf(id: Int): Int = (game!!.player(id).color and 0xFFFFFF).toInt()

    // ---------- lưu / khôi phục ----------

    private fun save() {
        val g = game ?: return
        store.savedGame = if (g.state.phase == Phase.OVER) null else g.toJson()
        store.botSpeed = botSpeed
    }

    // ---------- màn hình thiết lập ----------

    fun addSetupRow() {
        if (setupRows.size >= 4) return
        val defaults = listOf(SetupRow("Bạn", "hat", false), SetupRow("Máy Hà Nội", "lantern", true), SetupRow("Máy Huế", "drum", true), SetupRow("Máy Sài Gòn", "lotus", true))
        val used = setupRows.map { it.token }.toSet()
        val def = defaults[setupRows.size]
        setupRows += def.copy(token = vn.cotyphu.render.TOKENS.keys.firstOrNull { it !in used } ?: def.token)
    }

    fun startNewGame() {
        val players = setupRows.mapIndexed { i, r -> PlayerSetup(r.name.trim().ifEmpty { "Người chơi ${i + 1}" }, PLAYER_COLORS[i], r.token, r.isBot) }
        startGame(Game.newGame(players, maxRounds), fresh = true)
    }

    fun resumeGame() {
        val saved = store.savedGame ?: return
        val g = runCatching { Game.fromJson(saved) }.getOrNull()
        if (g == null) { store.savedGame = null; showToast("Không đọc được ván đã lưu"); return }
        botSpeed = store.botSpeed
        startGame(g, fresh = false)
    }

    private fun startGame(g: Game, fresh: Boolean) {
        val w = world ?: return
        game = g
        gameId++
        busy = false
        showSetup = false
        showGameOver = false
        showManage = false
        cardDialog?.done?.complete(Unit)
        cardDialog = null
        tileCard = null; tilePinned = false
        val s = g.state
        displayedMoney.clear(); displayedMoney.addAll(s.players.map { it.money })
        displayedPot = s.pot
        if (fresh) log.clear()
        addLog(if (fresh) msg {
            t("Ván mới bắt đầu với ")
            s.players.forEachIndexed { i, p -> if (i > 0) t(", "); who(p) }
            t(". Mỗi người có "); b(formatMoney(s.players[0].money)); t(".")
        } else msg { t("Đã khôi phục ván đấu đã lưu.") })
        save()
        val id = gameId
        scope.launch {
            w.reset(s.players, s.tiles.map { it.owner to it.level }, ::colorOf, s.dice[0], s.dice[1])
            engine.post { engine.camera.resetView() }
            delay(400)
            if (id == gameId) step()
        }
    }

    // ---------- vòng lặp lượt chơi ----------

    private fun refresh() { version++ }

    private fun setActions(text: AnnotatedString, buttons: List<ActionButton>) {
        actionText = text
        actions = buttons
    }

    private fun step() {
        val g = game ?: return
        val w = world ?: return
        if (busy) return
        refresh()
        val s = g.state
        displayedMoney.clear(); displayedMoney.addAll(s.players.map { it.money })
        displayedPot = s.pot
        if (s.phase == Phase.OVER) {
            w.tokens.forEach { it.active = false }
            setActions(msg { t("Ván đấu đã kết thúc.") }, listOf(ActionButton("Ván mới", ButtonStyle.PRIMARY) { showGameOver = false; showSetup = true }))
            showGameOver = true
            return
        }
        val p = g.current
        w.tokens.forEachIndexed { i, t -> t.active = i == p.id }
        if (follow) followToken(p.id)
        if (p.isBot) {
            setActions(msg(GOLD) { t("🤖 "); who(p); t(" đang tính nước đi…") }, emptyList())
            botTurn(gameId)
        } else {
            humanActions(p)
        }
    }

    private fun manageButton() = ActionButton("🏠 Tài sản", ButtonStyle.GHOST) { openManage() }

    private fun humanActions(p: PlayerState) {
        val g = game ?: return
        val s = g.state
        when (s.phase) {
            Phase.ROLL -> if (p.inJail) {
                val buttons = mutableListOf(ActionButton("🎲 Đổ tìm đôi", ButtonStyle.PRIMARY) { act { g.roll() } })
                buttons += ActionButton("Nộp phạt ${formatMoney(JAIL_FINE)}", enabled = p.money >= JAIL_FINE) { act { g.payJailFine() } }
                if (p.jailFree.isNotEmpty()) buttons += ActionButton("🎫 Dùng thẻ ân xá") { act { g.useJailCard() } }
                buttons += manageButton()
                setActions(msg(GOLD) { t("🔒 "); who(p); t(" đang ở Nhà tạm giữ (lần thử ${p.jailTurns + 1}/$MAX_JAIL_TURNS). Đổ được đôi để ra, hoặc nộp phạt.") }, buttons)
            } else {
                setActions(msg(GOLD) { t("Đến lượt "); who(p); t(" — hãy đổ xúc xắc!") }, listOf(
                    ActionButton("🎲 Đổ xúc xắc", ButtonStyle.PRIMARY) { act { g.roll() } }, manageButton(),
                ))
            }
            Phase.DECIDE -> {
                val pend = s.pending!!
                val tile = Board.TILES[pend.tile]
                showTile(pend.tile, pinned = true)
                if (pend.kind == PendingKind.BUY) {
                    setActions(msg(GOLD) { who(p); t(" dừng ở "); b(tile.name); t(". Mua với giá "); b(formatMoney(tile.price)); t("? (đang có ${formatMoney(p.money)})") }, listOf(
                        ActionButton("Mua ${formatMoney(tile.price)}", ButtonStyle.PRIMARY) { act { g.decideBuy(true) } },
                        ActionButton("Bỏ qua") { act { g.decideBuy(false) } },
                    ))
                } else {
                    val opt = pend.options[0]
                    setActions(msg(GOLD) { t("Nâng cấp "); b(tile.name); t(" lên "); b(LEVEL_NAMES[opt.level]); t(" với giá "); b(formatMoney(opt.cost)); t("?") }, listOf(
                        ActionButton("Xây ${formatMoney(opt.cost)}", ButtonStyle.PRIMARY) { act { g.decideBuild(opt.level) } },
                        ActionButton("Để sau") { act { g.decideBuild(null) } },
                    ))
                }
            }
            Phase.END -> {
                val again = s.rollAgain && !p.inJail && !p.bankrupt
                setActions(
                    if (again) msg(GOLD) { t("🎉 Đổ đôi! "); who(p); t(" được đổ thêm lần nữa.") } else msg(GOLD) { who(p); t(" đã xong nước đi.") },
                    listOf(ActionButton(if (again) "🎲 Đổ tiếp" else "Kết thúc lượt ➜", ButtonStyle.PRIMARY) { act { g.endTurn() } }, manageButton()),
                )
            }
            Phase.OVER -> Unit
        }
    }

    private fun botTurn(id: Int) = scope.launch {
        suspend fun pause(ms: Long) = delay((ms * botSpeed).toLong())
        fun alive() = id == gameId && game?.current?.isBot == true
        while (showSetup || showHelp) { delay(300); if (id != gameId) return@launch }
        pause(650)
        if (!alive()) return@launch
        val g = game!!
        when (g.state.phase) {
            Phase.ROLL -> {
                when (Bot.jailChoice(g)) {
                    JailChoice.CARD -> act(next = false) { g.useJailCard() }.join()
                    JailChoice.FINE -> act(next = false) { g.payJailFine() }.join()
                    JailChoice.ROLL -> Unit
                }
                if (!alive()) return@launch
                act { g.roll() }
            }
            Phase.DECIDE -> {
                pause(500)
                if (!alive()) return@launch
                val pend = g.state.pending!!
                act { if (pend.kind == PendingKind.BUY) g.decideBuy(Bot.wantsToBuy(g)) else g.decideBuild(Bot.buildChoice(g)) }
            }
            Phase.END -> {
                while (alive()) {
                    val b = Bot.nextBuild(g) ?: break
                    act(next = false) { g.buildOn(b) }.join()
                    pause(300)
                }
                if (!alive()) return@launch
                act { g.endTurn() }
            }
            Phase.OVER -> Unit
        }
    }

    /** Thực hiện một hành động của engine rồi diễn hoạt lần lượt các sự kiện trả về. */
    private fun act(next: Boolean = true, fn: () -> List<GameEvent>): Job = scope.launch {
        val g = game ?: return@launch
        if (busy) return@launch
        busy = true
        val id = gameId
        val actor = g.current
        if (!actor.isBot) setActions(msg(GOLD) { t("⏳ "); who(actor); t(" đang thực hiện nước đi…") }, emptyList())
        displayedMoney.clear(); displayedMoney.addAll(g.state.players.map { it.money })
        val events = try {
            fn()
        } catch (e: Exception) {
            showToast(e.message ?: "Không thực hiện được")
            busy = false
            if (next) step()
            return@launch
        }
        save() // trạng thái engine đã chốt — lưu ngay, không chờ diễn hoạt
        try {
            for (e in events) {
                if (id != gameId) return@launch
                playEvent(g, e)
            }
        } finally {
            if (id == gameId) {
                busy = false
                save()
                if (next) step() else refresh()
            }
        }
    }

    // ---------- diễn hoạt sự kiện ----------

    private suspend fun moneyFloat(playerId: Int, amount: Int) {
        val w = world ?: return
        val pos = engine.run { w.tokenPosition(playerId).add(Vec3(0f, 1.8f, 0f)) }
        val sp = engine.project(pos)
        if (!sp.visible) return
        val f = FloatText(nextId++, sp.x / density, sp.y / density, "${if (amount > 0) "+" else "−"}${formatMoney(abs(amount))}", amount > 0)
        floats += f
        scope.launch { delay(1700); floats.remove(f) }
    }

    private suspend fun playEvent(g: Game, e: GameEvent) {
        val w = world ?: return
        when (e) {
            is GameEvent.Dice -> {
                sound.play("dice")
                w.dice.roll(e.d1, e.d2)
                val p = g.player(e.player)
                addLog(msg { who(p); t(" đổ được ${e.d1} + ${e.d2} = "); b("${e.d1 + e.d2}"); if (e.d1 == e.d2) t(" (đôi!)") })
                delay(150)
            }
            is GameEvent.Move -> {
                val points = engine.run { e.path.map { w.board.tokenSlot(it, e.player) } }
                w.tokens[e.player].hopThrough(points, if (e.path.size > 12) 130 else 200) { k ->
                    sound.play("move", 0.2f)
                    if (follow) engine.post { engine.camera.follow(points[k]) }
                }
                showTile(e.to, pinned = false)
            }
            is GameEvent.Money -> {
                if (e.player < displayedMoney.size) displayedMoney[e.player] = displayedMoney[e.player] + e.amount
                if (e.amount != 0) {
                    moneyFloat(e.player, e.amount)
                    if (e.amount > 0) sound.play("cash", 0.5f)
                    val p = g.player(e.player)
                    addLog(msg { who(p); t(" ${if (e.amount > 0) "+" else "−"}${formatMoney(abs(e.amount))} · ${e.reason}") }, if (e.amount > 0) Tone.GOOD else Tone.BAD)
                }
                delay(260)
            }
            is GameEvent.Pot -> displayedPot = e.pot
            is GameEvent.Buy -> {
                val p = g.player(e.player)
                engine.post { w.board.setOwner(e.tile, colorOf(p.id), engine) }
                sound.play("cash")
                addLog(msg { who(p); t(" đã mua "); b(Board.TILES[e.tile].name); t(" 🎉") })
                refresh()
                showToast("${p.name} sở hữu ${Board.TILES[e.tile].name}")
                delay(350)
            }
            is GameEvent.Build -> {
                val p = g.player(e.player)
                engine.post { w.board.setLevel(e.tile, e.level, colorOf(p.id), engine) }
                sound.play("build")
                addLog(msg { who(p); t(" xây "); b(LEVEL_NAMES[e.level]); t(" tại "); b(Board.TILES[e.tile].name) })
                refresh()
                delay(550)
            }
            is GameEvent.SellBuilding -> {
                val p = g.player(e.player)
                engine.post { w.board.setLevel(e.tile, e.level, colorOf(p.id)) }
                addLog(msg { who(p); t(" bán bớt công trình ở "); b(Board.TILES[e.tile].name) }, Tone.BAD)
                delay(200)
            }
            is GameEvent.SellProperty -> {
                engine.post { w.board.setOwner(e.tile, null); w.board.setLevel(e.tile, 0, 0xFFFFFF) }
                addLog(msg { who(g.player(e.player)); t(": "); b(Board.TILES[e.tile].name); t(" trở về ngân hàng") }, Tone.BAD)
                delay(150)
            }
            is GameEvent.Rent -> {
                val owner = g.player(e.owner)
                addLog(msg { who(g.player(e.player)); t(" trả "); b(formatMoney(e.amount)); t(" tiền thuê "); b(Board.TILES[e.tile].name); t(" cho "); who(owner) }, Tone.BAD)
                showToast("Trả ${formatMoney(e.amount)} cho ${owner.name}")
            }
            is GameEvent.CardDrawn -> {
                w.board.drawCard(engine, e.deck)
                val p = g.player(e.player)
                addLog(msg { who(p); t(" rút thẻ ${e.deck.label}: “${e.text}”") })
                val done = CompletableDeferred<Unit>()
                cardDialog = CardDialog(e.deck, e.text, p.name, done)
                if (p.isBot) scope.launch { delay((2600 * botSpeed).toLong()); closeCard(done) }
                done.await()
            }
            is GameEvent.Jail -> {
                showToast("🚓 Vào Nhà tạm giữ!")
                val to = engine.run { w.board.tokenSlot(JAIL_INDEX, e.player, true) }
                w.tokens[e.player].flyTo(to)
                addLog(msg { who(g.player(e.player)); t(" bị đưa vào "); b("Nhà tạm giữ") }, Tone.BAD)
            }
            is GameEvent.JailRelease -> {
                val why = when (e.reason) { ReleaseReason.DOUBLES -> "nhờ đổ được đôi"; ReleaseReason.FINE -> "sau khi nộp phạt"; ReleaseReason.CARD -> "bằng thẻ ân xá" }
                addLog(msg { who(g.player(e.player)); t(" ra khỏi Nhà tạm giữ $why") }, Tone.GOOD)
                val to = engine.run { w.board.tokenSlot(JAIL_INDEX, e.player, false) }
                w.tokens[e.player].slideTo(to)
            }
            is GameEvent.JailStay -> addLog(msg { who(g.player(e.player)); t(" chưa đổ được đôi — vẫn ở Nhà tạm giữ (còn ${e.turnsLeft} lần thử)") })
            is GameEvent.JailForcedFine -> addLog(msg { who(g.player(e.player)); t(" hết lượt thử, buộc nộp phạt ${formatMoney(e.amount)}") }, Tone.BAD)
            is GameEvent.ThreeDoubles -> {
                showToast("Đổ đôi 3 lần liền — vào tù!")
                addLog(msg { who(g.player(e.player)); t(" đổ đôi 3 lần liên tiếp!") }, Tone.BAD)
            }
            is GameEvent.CantAfford -> addLog(msg { who(g.player(e.player)); t(" không đủ tiền mua "); b(Board.TILES[e.tile].name); t(" (${formatMoney(e.price)})") })
            is GameEvent.Decline -> addLog(msg { who(g.player(e.player)); t(if (e.kind == PendingKind.BUY) " không mua " else " chưa xây ở "); b(Board.TILES[e.tile].name) })
            is GameEvent.JailFreeCard -> addLog(msg { who(g.player(e.player)); t(" giữ một thẻ ân xá 🎫") }, Tone.GOOD)
            is GameEvent.Bankrupt -> {
                val p = g.player(e.player)
                showToast("💸 ${p.name} phá sản!", 2500)
                addLog(msg { who(p); t(" đã "); b("phá sản"); t(" và rời cuộc chơi") }, Tone.BAD)
                val tk = w.tokens[e.player]
                engine.tween(600) { k -> tk.group.scale.setScalar(1.35f * (1 - k) + 0.001f) }
                engine.post { tk.group.visible = false }
            }
            is GameEvent.Turn -> {
                showToast("Lượt của ${g.player(e.player).name}")
                hideTile(force = true)
            }
            is GameEvent.RollAgain -> showToast("🎉 Đổ đôi — được đổ tiếp!")
            is GameEvent.GameOver -> addLog(msg { t("🏆 "); who(g.player(e.winner)); t(" chiến thắng${if (e.byRounds) " khi hết số vòng giới hạn" else ""}!") }, Tone.GOOD)
        }
    }

    // ---------- thông báo, nhật ký, thẻ ô ----------

    private fun addLog(text: AnnotatedString, tone: Tone = Tone.NORMAL) {
        log.add(0, LogEntry(nextId++, text, tone))
        while (log.size > 80) log.removeAt(log.lastIndex)
    }

    fun showToast(text: String, ms: Long = 1800) {
        toast = text
        toastJob?.cancel()
        toastJob = scope.launch { delay(ms); toast = null }
    }

    fun closeCard(done: CompletableDeferred<Unit>? = cardDialog?.done) {
        if (done == null || done.isCompleted) return
        if (cardDialog?.done === done) cardDialog = null
        done.complete(Unit)
    }

    private fun showTile(index: Int, pinned: Boolean) {
        if (tilePinned && !pinned && tileCard != index) return
        tileCard = index
        tilePinned = pinned
    }

    fun hideTile(force: Boolean = false) {
        if (tilePinned && !force) return
        tileCard = null
        tilePinned = false
    }

    /** Chạm lên bàn cờ: chọn ô dưới ngón tay (hoặc đóng thẻ nếu chạm ra ngoài). */
    fun onTap(x: Float, y: Float) {
        if (game == null) return
        val hit = engine.pickPlane(x, y, TILE_TOP)
        val index = hit?.let { tileAt(it.x, it.z) }
        scope.launch {
            if (index == null) hideTile(force = true) else showTile(index, pinned = true)
            world?.let { w -> engine.post { w.board.setHover(index) } }
        }
    }

    // ---------- quản lý tài sản ----------

    fun openManage() {
        val g = game ?: return
        if (busy) return
        val p = g.current
        if (p.isBot || !g.canManage(p.id)) { showToast("Chỉ quản lý tài sản trong lượt của bạn"); return }
        showManage = true
    }

    fun build(i: Int) { game?.let { g -> act { g.buildOn(i) } } }
    fun sellBuilding(i: Int) { game?.let { g -> act { g.sellBuilding(i) } } }
    fun sellLand(i: Int) { game?.let { g -> act { g.sellProperty(i) } } }

    // ---------- thanh công cụ ----------

    private fun followToken(id: Int) {
        val w = world ?: return
        engine.post { if (id < w.tokens.size) engine.camera.follow(w.tokens[id].group.position) }
    }

    fun toggleFollow() {
        follow = !follow
        val g = game
        if (follow && g != null) followToken(g.current.id) else engine.post { engine.camera.follow(null) }
    }

    fun overview() {
        follow = false
        engine.post { engine.camera.resetView() }
    }

    fun toggleSound() {
        muted = !muted
        sound.muted = muted
        store.muted = muted
    }

    fun focusPlayer(id: Int) {
        val w = world ?: return
        engine.post { if (id < w.tokens.size) engine.camera.follow(w.tokens[id].group.position) }
    }

    fun openSetup() { showSetup = true }

    companion object {
        val GOLD = Color(0xFFF2C14E)
    }
}
