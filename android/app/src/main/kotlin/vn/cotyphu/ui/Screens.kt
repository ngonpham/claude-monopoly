@file:OptIn(ExperimentalLayoutApi::class)

package vn.cotyphu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import vn.cotyphu.core.Board
import vn.cotyphu.core.Deck
import vn.cotyphu.core.Game
import vn.cotyphu.core.TileType
import vn.cotyphu.core.formatMoney
import vn.cotyphu.render.TOKENS

private val noRipple = MutableInteractionSource()

private fun groupColor(index: Int): Color {
    val t = Board.TILES[index]
    t.group?.let { return Color(Board.GROUPS.getValue(it).color.toInt()) }
    return when (t.type) {
        TileType.TRANSPORT -> Color(0xFF37474F)
        TileType.UTILITY -> Color(0xFF00695C)
        TileType.CHANCE -> Color(0xFFE06A00)
        TileType.COMMUNITY -> Color(0xFFB0102A)
        TileType.TAX -> Color(0xFF6D4C41)
        else -> Palette.lacquer2
    }
}

private fun groupLabel(index: Int): String {
    val t = Board.TILES[index]
    t.group?.let { return Board.GROUPS.getValue(it).name }
    return when (t.type) {
        TileType.TRANSPORT -> "Giao thông"
        TileType.UTILITY -> "Năng lượng"
        TileType.CHANCE -> "Cơ hội"
        TileType.COMMUNITY -> "Khí vận"
        TileType.TAX -> "Thuế"
        else -> "Ô đặc biệt"
    }
}

@Composable
fun GameScreen(c: GameController, surface: () -> android.view.View) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.lacquerDeep)) {
        val wide = maxWidth >= 840.dp || (maxWidth > maxHeight && maxWidth >= 640.dp)
        AndroidView(factory = { surface() }, modifier = Modifier.fillMaxSize())
        // chữ tiền bay theo toạ độ màn hình của khung OpenGL (không lùi theo vùng an toàn)
        c.floats.forEach { FloatingMoney(it) }

        val game = c.game
        // đọc version để vẽ lại khi trạng thái ván đổi
        @Suppress("UNUSED_VARIABLE") val v = c.version

        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                TopBar(c, wide)
                if (game != null && !wide) PlayersRow(c, game)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (game != null && wide) {
                        Column(Modifier.align(Alignment.TopStart).padding(12.dp).width(250.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            game.state.players.forEach { PlayerCard(c, game, it.id, compact = false) }
                        }
                        Column(Modifier.align(Alignment.TopEnd).padding(12.dp).width(310.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            c.tileCard?.let { TileCardView(c, game, it) }
                            LogPanel(c, Modifier.weight(1f, fill = false).heightIn(max = 420.dp))
                        }
                    } else if (game != null) {
                        c.tileCard?.let { Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp).padding(bottom = 4.dp)) { TileCardView(c, game, it) } }
                    }
                    Toast(c, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                }
                ActionBar(c, Modifier.align(Alignment.CenterHorizontally))
            }
        }

        if (c.showLog && !wide) Overlay(onDismiss = { c.showLog = false }) {
            DialogBox(maxWidth = 520.dp) {
                Text("Nhật ký ván đấu", style = TextStyleTitle, color = Palette.ink)
                Spacer(Modifier.height(8.dp))
                LogList(c, dark = false, modifier = Modifier.heightIn(max = 460.dp))
                DialogActions { GameButton("Đóng", ButtonStyle.PRIMARY) { c.showLog = false } }
            }
        }
        if (game != null && c.showManage) ManageDialog(c, game)
        c.cardDialog?.let { CardDialogView(c, it) }
        if (game != null && c.showGameOver) GameOverDialog(c, game)
        if (c.showHelp) HelpDialog(c)
        if (c.showSetup && !c.loading) SetupScreen(c)
        if (c.loading) LoadingScreen()

        BackHandler(enabled = c.cardDialog != null || c.showManage || c.showHelp || c.showLog || (c.showSetup && game != null)) {
            when {
                c.cardDialog != null -> c.closeCard()
                c.showManage -> c.showManage = false
                c.showHelp -> c.showHelp = false
                c.showLog -> c.showLog = false
                c.showSetup -> c.showSetup = false
            }
        }
    }
}

private val TextStyleTitle = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)

// ---------------------------------------------------------------- thanh trên

@Composable
private fun TopBar(c: GameController, wide: Boolean) {
    val g = c.game
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xEB2A0907), Color(0xA62A0907)))).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (wide) {
            Text(buildBrand(), fontFamily = BeVietnamPro, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Palette.cream, maxLines = 1)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, if (wide) Alignment.CenterHorizontally else Alignment.Start)) {
            if (g != null) {
                val s = g.state
                Chip(if (s.maxRounds > 0) "Vòng ${minOf(s.round, s.maxRounds)}/${s.maxRounds}" else "Vòng ${s.round}", Palette.cream)
                Chip("Quỹ: ${formatMoney(c.displayedPot)}", Palette.pot)
            }
        }
        IconChip("🎥", c.follow, "Camera theo quân cờ") { c.toggleFollow() }
        IconChip("🗺️", false, "Toàn cảnh bàn cờ") { c.overview() }
        IconChip(if (c.muted) "🔇" else "🔊", false, "Âm thanh") { c.toggleSound() }
        if (!wide) IconChip("📜", c.showLog, "Nhật ký") { c.showLog = true }
        IconChip("❔", false, "Luật chơi") { c.showHelp = true }
        IconChip("☰", false, "Ván mới") { c.openSetup() }
    }
}

private fun buildBrand() = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFFFDE00))); append("★ "); pop()
    append("Cờ Tỷ Phú ")
    pushStyle(androidx.compose.ui.text.SpanStyle(color = Palette.gold)); append("Việt Nam"); pop()
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(text, color = color, fontFamily = BeVietnamPro, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x1AFFF6E0)).border(1.dp, Palette.panelBorder, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
private fun IconChip(icon: String, on: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Color(0x59F2C14E) else Color(0x1FFFF6E0))
            .border(1.dp, Palette.panelBorder, RoundedCornerShape(10.dp)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(icon, fontSize = 17.sp) }
}

// ---------------------------------------------------------------- người chơi

@Composable
private fun PlayersRow(c: GameController, g: Game) {
    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(g.state.players, key = { it.id }) { Box(Modifier.width(150.dp)) { PlayerCard(c, g, it.id, compact = true) } }
    }
}

@Composable
private fun PlayerCard(c: GameController, g: Game, id: Int, compact: Boolean) {
    val p = g.player(id)
    val color = Color(p.color.toInt())
    val active = g.state.current == id && g.state.phase != vn.cotyphu.core.Phase.OVER
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().alpha(if (p.bankrupt) 0.45f else 1f).clip(shape).background(Palette.panel)
            .border(if (active) 2.dp else 1.dp, if (active) color else Palette.panelBorder, shape)
            .clickable { c.focusPlayer(id) },
    ) {
        Box(Modifier.width(5.dp).height(if (compact) 48.dp else 76.dp).background(color))
        Column(Modifier.padding(horizontal = 10.dp, vertical = if (compact) 5.dp else 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(7.dp))
                Text(p.name, color = Palette.cream, fontFamily = BeVietnamPro, fontWeight = FontWeight.ExtraBold, fontSize = if (compact) 13.sp else 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!compact) Text(formatMoney(c.displayedMoney.getOrElse(id) { p.money }), color = Palette.gold, fontFamily = BeVietnamPro, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            if (compact) {
                Text(formatMoney(c.displayedMoney.getOrElse(id) { p.money }), color = Palette.gold, fontFamily = BeVietnamPro, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(start = 19.dp))
            } else {
                val badges = buildList {
                    add(TOKENS[p.token] ?: "")
                    add("Tài sản: ${formatMoney(g.netWorth(id))}")
                    if (p.isBot) add("🤖 Máy")
                    if (p.inJail) add("🔒 Tạm giữ")
                    if (p.jailFree.isNotEmpty()) add("🎫 Ân xá ×${p.jailFree.size}")
                    if (p.bankrupt) add("💸 Phá sản")
                }
                FlowRow(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { Text(it, color = Palette.creamDim, fontSize = 11.5.sp, fontFamily = BeVietnamPro) }
                }
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    g.ownedTiles(id).forEach { Box(Modifier.size(12.dp, 8.dp).clip(RoundedCornerShape(2.dp)).background(groupColor(it))) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- nhật ký

@Composable
private fun LogPanel(c: GameController, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().panel().padding(10.dp)) {
        Text("NHẬT KÝ VÁN ĐẤU", color = Palette.gold, fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        LogList(c, dark = true)
    }
}

@Composable
private fun LogList(c: GameController, dark: Boolean, modifier: Modifier = Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(c.log, key = { it.id }) { e ->
            val color = when (e.tone) {
                Tone.GOOD -> if (dark) Palette.good else Color(0xFF1B6E2A)
                Tone.BAD -> if (dark) Palette.bad else Color(0xFF9A2A1A)
                Tone.NORMAL -> if (dark) Palette.cream else Palette.ink
            }
            Text(e.text, color = color, fontFamily = BeVietnamPro, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// ---------------------------------------------------------------- thẻ thông tin ô

@Composable
private fun TileCardView(c: GameController, g: Game, index: Int) {
    val tile = Board.TILES[index]
    val st = g.state.tiles[index]
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().shadow(8.dp, shape).clip(shape).background(Palette.paper)) {
        Row(Modifier.fillMaxWidth().background(groupColor(index)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(groupLabel(index).uppercase(), color = Color.White, fontFamily = BeVietnamPro, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            if (c.tilePinned) Text("✕", color = Color.White, fontSize = 15.sp, modifier = Modifier.clickable { c.hideTile(force = true) }.padding(horizontal = 6.dp))
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(tile.name, color = Palette.ink, fontFamily = BeVietnamPro, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(tile.info, color = Color(0xFF4A3220), fontFamily = BeVietnamPro, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            val rows: List<Pair<String, String>> = when (tile.type) {
                TileType.PROPERTY -> tile.rent.mapIndexed { lv, r -> (LEVEL_NAMES[lv] + if (lv == 0) " (×2 nếu đủ bộ)" else "") to formatMoney(r) } +
                    ("Chi phí xây mỗi cấp" to formatMoney(Board.GROUPS.getValue(tile.group!!).buildCost))
                TileType.TRANSPORT -> (1..4).map { "Sở hữu $it ô giao thông" to formatMoney(25 * (1 shl (it - 1))) }
                TileType.UTILITY -> listOf("Sở hữu 1 nhà máy" to "×4 tổng xúc xắc", "Sở hữu cả 2" to "×10 tổng xúc xắc")
                else -> emptyList()
            }
            if (c.tilePinned || rows.size <= 5) rows.forEachIndexed { i, (k, v) ->
                val current = tile.type == TileType.PROPERTY && st.owner != null && st.level == i
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(k, color = if (current) Color(0xFFC8102E) else Palette.ink, fontFamily = BeVietnamPro, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(v, color = if (current) Color(0xFFC8102E) else Palette.ink, fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            if (tile.price > 0) {
                val owner = st.owner
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (owner == null) {
                        Text("Chưa có chủ · Giá ${formatMoney(tile.price)}", color = Palette.ink, fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        val p = g.player(owner)
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(p.color.toInt())))
                        Spacer(Modifier.width(6.dp))
                        Text("Chủ: ${p.name}${if (tile.type == TileType.PROPERTY) " · ${LEVEL_NAMES[st.level]}" else ""}", color = Palette.ink, fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- thanh hành động, toast, chữ bay

@Composable
private fun ActionBar(c: GameController, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(12.dp).widthIn(max = 680.dp).fillMaxWidth().panel().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(c.actionText, color = Palette.cream, fontFamily = BeVietnamPro, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
        if (c.actions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var clicked by remember(c.actions) { mutableStateOf(false) }
                c.actions.forEach { a ->
                    GameButton(a.label, a.style, enabled = a.enabled && !clicked, big = a.style == ButtonStyle.PRIMARY) {
                        clicked = true // chặn bấm hai lần trong lúc hành động đang chạy
                        a.onClick()
                        if (a.style == ButtonStyle.GHOST) clicked = false
                    }
                }
            }
        }
    }
}

@Composable
private fun Toast(c: GameController, modifier: Modifier) {
    var last by remember { mutableStateOf("") }
    c.toast?.let { last = it }
    AnimatedVisibility(c.toast != null, modifier, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
        Text(last, color = Color(0xFF3A1D00), fontFamily = BeVietnamPro, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
            modifier = Modifier.shadow(8.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(Color(0xFFFFD96A), Color(0xFFE0A422)))).padding(horizontal = 20.dp, vertical = 9.dp))
    }
}

@Composable
private fun FloatingMoney(f: FloatText) {
    val anim = remember(f.id) { Animatable(0f) }
    LaunchedEffect(f.id) { anim.animateTo(1f, tween(1600)) }
    val k = anim.value
    val alpha = if (k < 0.15f) k / 0.15f else 1f - (k - 0.15f) / 0.85f
    Text(
        f.text,
        color = if (f.good) Palette.floatGood else Palette.floatBad,
        style = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Black, fontSize = 20.sp, shadow = Shadow(Color(0x99000000), blurRadius = 8f)),
        modifier = Modifier.offset(x = f.x.dp - 50.dp, y = f.y.dp - 14.dp - (70 * k).dp).width(100.dp).alpha(alpha.coerceIn(0f, 1f)),
        textAlign = TextAlign.Center,
    )
}

// ---------------------------------------------------------------- hộp thoại

@Composable
private fun Overlay(onDismiss: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x8C2A0907), Color(0xCC0A0302))))
            .clickable(interactionSource = noRipple, indication = null) { onDismiss?.invoke() }
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun DialogBox(maxWidth: Dp = 520.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.widthIn(max = maxWidth).fillMaxWidth().shadow(24.dp, shape).clip(shape).background(Palette.paper)
            .border(3.dp, Palette.gold2, shape).clickable(interactionSource = noRipple, indication = null) {}
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
    ) { content() }
}

@Composable
private fun DialogActions(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) { content() }
}

@Composable
private fun CardDialogView(c: GameController, d: CardDialog) {
    Overlay {
        val shape = RoundedCornerShape(18.dp)
        val head = if (d.deck == Deck.CHANCE) listOf(Color(0xFFFF9A2A), Color(0xFFE06A00)) else listOf(Color(0xFFE0303A), Color(0xFFA00C20))
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().shadow(24.dp, shape).clip(shape).background(Palette.paper), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (d.deck == Deck.CHANCE) "? CƠ HỘI" else "✉ KHÍ VẬN", color = Color.White, fontFamily = BeVietnamPro, fontWeight = FontWeight.Black,
                fontSize = 24.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(head)).padding(16.dp))
            Text(d.text, color = Palette.ink, fontFamily = BeVietnamPro, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp))
            Text("Thẻ của ${d.player}", color = Palette.muted, fontFamily = BeVietnamPro, fontSize = 13.sp)
            Box(Modifier.padding(16.dp)) { GameButton("OK", ButtonStyle.PRIMARY) { c.closeCard(d.done) } }
        }
    }
}

@Composable
private fun ManageDialog(c: GameController, g: Game) {
    @Suppress("UNUSED_VARIABLE") val v = c.version
    val p = g.current
    val armed = remember { mutableStateMapOf<Int, Boolean>() }
    Overlay(onDismiss = { c.showManage = false }) {
        DialogBox {
            Text("Tài sản của ${p.name}", style = TextStyleTitle, color = Palette.ink)
            Text("Tiền mặt: ${formatMoney(p.money)} · Tổng tài sản: ${formatMoney(g.netWorth(p.id))}", color = Palette.ink, fontFamily = BeVietnamPro, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            val owned = g.ownedTiles(p.id).sorted()
            if (owned.isEmpty()) Text("Chưa sở hữu ô nào. Hãy mua đất khi dừng ở ô chưa có chủ!", color = Palette.muted, fontFamily = BeVietnamPro)
            owned.forEach { i ->
                val t = Board.TILES[i]
                val st = g.state.tiles[i]
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Palette.paper2).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(8.dp).height(40.dp).clip(RoundedCornerShape(4.dp)).background(groupColor(i)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, color = Palette.ink, fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val kind = when (t.type) { TileType.PROPERTY -> LEVEL_NAMES[st.level]; TileType.TRANSPORT -> "Giao thông"; else -> "Năng lượng" }
                        Text("$kind · thuê ${formatMoney(g.rentFor(i))}", color = Palette.muted, fontFamily = BeVietnamPro, fontSize = 12.sp)
                        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (t.type == TileType.PROPERTY) {
                                SmallButton("+ Xây ${formatMoney(g.buildCost(i))}", Palette.gold, g.canBuildFreely(p.id, i)) { c.build(i) }
                                if (st.level > 0) SmallButton("− Bán công trình +${formatMoney(g.buildCost(i) / 2)}", Palette.paper, true) { c.sellBuilding(i) }
                            }
                            if (st.level == 0) {
                                val isArmed = armed[i] == true
                                SmallButton(if (isArmed) "Bấm lần nữa để bán" else "Bán đất +${formatMoney(t.price / 2)}", Color(0xFFEF6A5A), true) {
                                    if (isArmed) { armed.remove(i); c.sellLand(i) } else armed[i] = true
                                }
                            }
                        }
                        if (t.type == TileType.PROPERTY && !g.ownsGroup(p.id, t.group!!)) {
                            Text("Cần sở hữu trọn vùng để xây tự do", color = Palette.muted, fontFamily = BeVietnamPro, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
            DialogActions { GameButton("Đóng", ButtonStyle.PRIMARY) { c.showManage = false } }
        }
    }
}

@Composable
private fun SmallButton(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Text(label, color = if (enabled) Palette.ink else Palette.muted, fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (enabled) color else color.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable
private fun GameOverDialog(c: GameController, g: Game) {
    val winner = g.state.winner?.let { g.player(it) } ?: return
    val ranking = g.state.players.map { it to if (it.bankrupt) 0 else g.netWorth(it.id) }
        .sortedWith(compareBy<Pair<vn.cotyphu.core.PlayerState, Int>> { it.first.bankrupt }.thenByDescending { it.second })
    Overlay {
        DialogBox {
            Text("🏆", fontSize = 52.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("${winner.name} trở thành Tỷ Phú Việt Nam!", style = TextStyleTitle, color = Palette.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            ranking.forEachIndexed { i, (p, worth) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (i == 0) Brush.horizontalGradient(listOf(Color(0xFFFFE08A), Palette.paper2)) else Brush.horizontalGradient(listOf(Palette.paper2, Palette.paper2)))
                    .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(p.color.toInt())))
                    Spacer(Modifier.width(10.dp))
                    Text(p.name, color = Palette.ink, fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = if (i == 0) 17.sp else 15.sp, modifier = Modifier.weight(1f))
                    Text(if (p.bankrupt) "Phá sản" else formatMoney(worth), color = Palette.ink, fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold)
                }
            }
            DialogActions {
                GameButton("Xem bàn cờ", ButtonStyle.NORMAL) { c.showGameOver = false }
                GameButton("Ván mới", ButtonStyle.PRIMARY) { c.showGameOver = false; c.openSetup() }
            }
        }
    }
}

@Composable
private fun HelpDialog(c: GameController) {
    Overlay(onDismiss = { c.showHelp = false }) {
        DialogBox {
            Text("Luật chơi", style = TextStyleTitle, color = Palette.ink)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Mỗi người bắt đầu với 1.500 tr₫. Đi qua Xuất phát nhận 200 tr₫.",
                "Dừng ở ô chưa có chủ: được quyền mua. Dừng ở ô của người khác: trả tiền thuê.",
                "Sở hữu trọn bộ một vùng (cùng màu): tiền thuê đất trống ×2 và được xây tự do trong lượt của mình qua nút Tài sản.",
                "Dừng lại ở đất của mình: được nâng thêm 1 cấp (3 nhà phố → khách sạn).",
                "Giao thông: 25/50/100/200 tr₫ tuỳ số ga, cảng, sân bay sở hữu. Năng lượng: ×4 (hoặc ×10 nếu có cả hai) tổng xúc xắc.",
                "Thuế và tiền phạt vào Quỹ chung; dừng ở Bến Ninh Kiều nhận toàn bộ Quỹ.",
                "Đổ đôi được đổ tiếp; đổ đôi 3 lần liên tiếp hoặc dừng ở ô Vào tù thì vào Nhà tạm giữ. Ra tù bằng cách đổ đôi, dùng thẻ ân xá hoặc nộp 50 tr₫ (bắt buộc ở lần thử thứ 3).",
                "Thiếu tiền: tài sản được tự động bán (công trình và đất được 50% giá). Vẫn không đủ thì phá sản.",
                "Thắng khi là người cuối cùng trụ lại, hoặc giàu nhất (tiền + tài sản) khi hết số vòng giới hạn.",
            ).forEach { line ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("•  ", color = Palette.lacquer2, fontWeight = FontWeight.Bold)
                    Text(line, color = Palette.ink, fontFamily = BeVietnamPro, fontSize = 14.5.sp, lineHeight = 21.sp)
                }
            }
            Text("Điều khiển: kéo một ngón để xoay bàn cờ, chụm hai ngón để phóng to, kéo hai ngón để dịch chuyển. Chạm vào ô để xem thông tin thắng cảnh.",
                color = Palette.muted, fontFamily = BeVietnamPro, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            DialogActions { GameButton("Đã hiểu", ButtonStyle.PRIMARY) { c.showHelp = false } }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Palette.lacquer2, Color(0xFF1A0503)))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = Palette.gold, strokeWidth = 5.dp, modifier = Modifier.size(54.dp))
            Text("Đang dựng bàn cờ và các thắng cảnh…", color = Palette.creamDim, fontFamily = BeVietnamPro)
        }
    }
}

// ---------------------------------------------------------------- màn hình thiết lập

@Composable
private fun SetupScreen(c: GameController) {
    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xBF7A1C14), Color(0xEB140503))))
            .clickable(interactionSource = noRipple, indication = null) { if (c.game != null) c.showSetup = false }
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().shadow(30.dp, shape).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF55A1410), Color(0xF52A0907)))).border(2.dp, Palette.gold2, shape)
                .clickable(interactionSource = noRipple, indication = null) {}
                .verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("★", color = Color(0xFFFFDE00), fontSize = 42.sp, style = TextStyle(shadow = Shadow(Color(0xCCFFDE00), blurRadius = 24f)))
            Text(buildTitle(), fontFamily = BeVietnamPro, fontWeight = FontWeight.Black, fontSize = 32.sp, color = Palette.cream, textAlign = TextAlign.Center)
            Text("Mua Phố cổ Hội An, xây khách sạn bên Vịnh Hạ Long, thu tiền vé ở Cầu Vàng — ai sẽ trở thành tỷ phú di sản?",
                color = Palette.creamDim, fontFamily = BeVietnamPro, textAlign = TextAlign.Center, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            c.setupRows.forEachIndexed { i, row -> SetupRowView(c, i, row) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Start) {
                GameButton("+ Thêm người chơi", ButtonStyle.GHOST, enabled = c.setupRows.size < 4) { c.addSetupRow() }
            }
            OptionRow("Giới hạn", listOf(20 to "20 vòng", 30 to "30 vòng", 50 to "50 vòng", 0 to "Không giới hạn"), c.maxRounds) { c.maxRounds = it }
            OptionRow("Tốc độ máy", listOf(0.5f to "Nhanh", 1f to "Thường", 1.6f to "Chậm"), c.botSpeed) { c.botSpeed = it }
            FlowRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (c.hasSave) GameButton("↺ Chơi tiếp ván đã lưu", ButtonStyle.GHOST) { c.resumeGame() }
                GameButton("Bắt đầu hành trình", ButtonStyle.PRIMARY, big = true) { c.startNewGame() }
            }
        }
    }
}

private fun buildTitle() = androidx.compose.ui.text.buildAnnotatedString {
    append("Cờ Tỷ Phú ")
    pushStyle(androidx.compose.ui.text.SpanStyle(color = Palette.gold)); append("Việt Nam"); pop()
}

@Composable
private fun SetupRowView(c: GameController, i: Int, row: SetupRow) {
    val color = Color(PLAYER_COLORS[i].toInt())
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x12FFF6E0))
            .border(1.dp, Color(0x33F2C14E), RoundedCornerShape(12.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(color))
        OutlinedTextField(
            value = row.name, onValueChange = { if (it.length <= 18) c.setupRows[i] = row.copy(name = it) },
            singleLine = true, modifier = Modifier.weight(1f),
            textStyle = TextStyle(fontFamily = BeVietnamPro, fontSize = 15.sp, color = Palette.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Palette.paper, unfocusedContainerColor = Palette.paper, focusedBorderColor = Palette.gold, unfocusedBorderColor = Color(0x66F2C14E)),
        )
        var open by remember { mutableStateOf(false) }
        Box {
            SmallButton(TOKENS[row.token] ?: row.token, Palette.paper, true) { open = true }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                TOKENS.forEach { (k, label) -> DropdownMenuItem(text = { Text(label, fontFamily = BeVietnamPro) }, onClick = { c.setupRows[i] = row.copy(token = k); open = false }) }
            }
        }
        SmallButton(if (row.isBot) "🤖 Máy" else "👤 Người", if (row.isBot) Palette.creamDim else Palette.gold, true) { c.setupRows[i] = row.copy(isBot = !row.isBot) }
        Text("✕", color = if (c.setupRows.size > 2) Palette.creamDim else Color(0x55F0D9B5), fontSize = 18.sp,
            modifier = Modifier.clickable(enabled = c.setupRows.size > 2) { c.setupRows.removeAt(i) }.padding(6.dp))
    }
}

@Composable
private fun <T> OptionRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Palette.creamDim, fontFamily = BeVietnamPro, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, end = 4.dp))
        options.forEach { (value, text) ->
            val on = value == selected
            Text(text, color = if (on) Color(0xFF3A1D00) else Palette.cream, fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Palette.gold else Color(0x1FFFF6E0))
                    .border(1.dp, Palette.panelBorder, RoundedCornerShape(50)).clickable { onSelect(value) }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}
