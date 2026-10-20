package vn.cotyphu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.cotyphu.core.Game
import vn.cotyphu.core.Phase
import vn.cotyphu.gl.Engine
import vn.cotyphu.ui.ButtonStyle
import vn.cotyphu.ui.GameController
import vn.cotyphu.ui.SaveStore
import vn.cotyphu.ui.SetupRow
import vn.cotyphu.ui.Sound
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Chạy bộ điều khiển thật (luật chơi + diễn hoạt + giao diện trạng thái) với 1 người và 2 máy:
 * test tự "bơm" khung hình cho Engine (không cần GPU) và tự bấm nút thay người chơi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControllerFlowTest {

    @Test
    fun humanAndBotsPlaySeveralRoundsWithoutErrors() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val engine = Engine().also { it.setViewport(1080, 2160) }
        val errors = CopyOnWriteArrayList<Throwable>()
        val ui = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + ui + CoroutineExceptionHandler { _, e -> errors += e })
        val store = SaveStore(ctx)
        store.savedGame = null
        val c = runBlocking(ui) { GameController(engine, Sound(ctx), store, scope, 2.5f) }

        var simNs = 1_000_000_000L
        fun pump(frames: Int = 1) = repeat(frames) {
            simNs += 16_000_000L
            engine.update(simNs)
            Thread.sleep(1)
        }
        fun <T> onUi(block: () -> T): T = runBlocking(ui) { block() }

        val start = System.currentTimeMillis()
        while (onUi { c.loading }) { pump(); check(System.currentTimeMillis() - start < 60_000) { "dựng cảnh quá lâu" } }

        onUi {
            c.botSpeed = 0.05f
            c.setupRows.clear()
            c.setupRows += SetupRow("Bạn", "hat", false)
            c.setupRows += SetupRow("Máy Hà Nội", "lantern", true)
            c.setupRows += SetupRow("Máy Huế", "drum", true)
            c.startNewGame()
        }

        var humanClicks = 0
        var cardsClosed = 0
        val deadline = System.currentTimeMillis() + 150_000
        while (System.currentTimeMillis() < deadline) {
            pump(4)
            val done = onUi {
                val g = c.game ?: return@onUi false
                if (g.state.phase == Phase.OVER || g.state.round > 4) return@onUi true
                val human = !g.current.isBot
                val card = c.cardDialog
                if (card != null && human) { c.closeCard(card.done); cardsClosed++ }
                else if (human && card == null && !c.showManage) {
                    c.actions.firstOrNull { it.style == ButtonStyle.PRIMARY && it.enabled }?.let { it.onClick(); humanClicks++ }
                }
                false
            }
            if (done) break
        }
        // để các hoạt cảnh cuối chạy xong
        pump(120)

        val g = onUi { c.game }
        assertNotNull(g)
        g!!
        println("Vòng: ${g.state.round}, lượt bấm của người: $humanClicks, thẻ đã đóng: $cardsClosed, nhật ký: ${onUi { c.log.size }} dòng")
        assertTrue("lỗi trong coroutine: $errors", errors.isEmpty())
        assertTrue("ván phải chạy qua ít nhất 4 vòng (đang ở vòng ${g.state.round})", g.state.round > 4 || g.state.phase == Phase.OVER)
        assertTrue("người chơi phải được bấm nút", humanClicks >= 4)
        assertTrue(onUi { c.log.any { it.text.text.contains("đổ được") } })
        // tiền hiển thị khớp trạng thái thật sau khi diễn hoạt xong
        onUi { if (c.game!!.state.phase != Phase.OVER) assertEquals(g.state.players.map { it.money }, c.displayedMoney.toList()) }

        // ván được lưu và khôi phục được
        val saved = store.savedGame
        if (g.state.phase != Phase.OVER) {
            assertNotNull("phải có ván đã lưu", saved)
            assertEquals(g.state, Game.fromJson(saved!!).state)
        }
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        ui.close()
    }
}
