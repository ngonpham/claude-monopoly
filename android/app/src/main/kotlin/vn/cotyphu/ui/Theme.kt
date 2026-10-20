package vn.cotyphu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.cotyphu.R

// Bảng màu và kiểu chữ giống css/style.css của bản web: sơn mài đỏ, vàng kim, giấy dó.

object Palette {
    val lacquer = Color(0xFF5A1410)
    val lacquer2 = Color(0xFF7A1C14)
    val lacquerDeep = Color(0xFF2A0907)
    val gold = Color(0xFFF2C14E)
    val gold2 = Color(0xFFD4A24A)
    val paper = Color(0xFFFBF4E2)
    val paper2 = Color(0xFFF3E6C6)
    val ink = Color(0xFF2B1A0E)
    val muted = Color(0xFF7A5A3A)
    val cream = Color(0xFFFFF6E0)
    val creamDim = Color(0xFFF0D9B5)
    val panel = Color(0xC71E0A06)
    val panelBorder = Color(0x59F2C14E)
    val good = Color(0xFFB9F6C0)
    val bad = Color(0xFFFFC4BD)
    val floatGood = Color(0xFF8DFF9A)
    val floatBad = Color(0xFFFF8A80)
    val pot = Color(0xFF9FF0A8)
}

val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
    Font(R.font.be_vietnam_pro_extrabold, FontWeight.ExtraBold),
    Font(R.font.be_vietnam_pro_black, FontWeight.Black),
)

@Composable
fun CoTyPhuTheme(content: @Composable () -> Unit) {
    val base = TextStyle(fontFamily = BeVietnamPro)
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Palette.gold, onPrimary = Color(0xFF3A1D00), surface = Palette.paper, onSurface = Palette.ink, background = Palette.lacquerDeep),
        typography = Typography(
            bodyLarge = base.copy(fontSize = 15.sp), bodyMedium = base.copy(fontSize = 14.sp), bodySmall = base.copy(fontSize = 12.sp),
            titleLarge = base.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold), titleMedium = base.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            labelLarge = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), labelMedium = base.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            headlineMedium = base.copy(fontSize = 30.sp, fontWeight = FontWeight.Black),
        ),
        content = content,
    )
}

/** Khung kính tối viền vàng (class .panel). */
fun Modifier.panel(shape: RoundedCornerShape = RoundedCornerShape(14.dp)) = this
    .clip(shape)
    .background(Palette.panel)
    .border(1.dp, Palette.panelBorder, shape)

private val goldGradient = Brush.verticalGradient(listOf(Color(0xFFFFD96A), Color(0xFFE0A422)))

@Composable
fun GameButton(label: String, style: ButtonStyle, modifier: Modifier = Modifier, enabled: Boolean = true, big: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val pad = if (big) PaddingValues(horizontal = 22.dp, vertical = 12.dp) else PaddingValues(horizontal = 14.dp, vertical = 9.dp)
    when (style) {
        ButtonStyle.PRIMARY -> Button(
            onClick = onClick, enabled = enabled, shape = shape, contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
            modifier = modifier,
        ) {
            Text(label, color = if (enabled) Color(0xFF3A1D00) else Color(0x803A1D00), fontWeight = FontWeight.ExtraBold,
                fontSize = if (big) 17.sp else 15.sp,
                modifier = Modifier.background(if (enabled) goldGradient else Brush.verticalGradient(listOf(Color(0x80FFD96A), Color(0x80E0A422))), shape).padding(pad))
        }
        ButtonStyle.GHOST -> Button(
            onClick = onClick, enabled = enabled, shape = shape, contentPadding = pad, border = BorderStroke(1.dp, Palette.panelBorder),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x1FFFF6E0), contentColor = Palette.cream, disabledContentColor = Color(0x80FFF6E0), disabledContainerColor = Color(0x10FFF6E0)),
            modifier = modifier,
        ) { Text(label, fontWeight = FontWeight.Bold) }
        ButtonStyle.NORMAL -> Button(
            onClick = onClick, enabled = enabled, shape = shape, contentPadding = pad,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.paper, contentColor = Palette.ink, disabledContainerColor = Color(0x80FBF4E2), disabledContentColor = Color(0x802B1A0E)),
            modifier = modifier,
        ) { Text(label, fontWeight = FontWeight.Bold) }
    }
}
