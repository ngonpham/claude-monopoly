package vn.cotyphu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import vn.cotyphu.gl.Engine
import vn.cotyphu.render.Fonts
import vn.cotyphu.ui.CoTyPhuTheme
import vn.cotyphu.ui.GameController
import vn.cotyphu.ui.GameScreen
import vn.cotyphu.ui.GameSurface
import vn.cotyphu.ui.SaveStore
import vn.cotyphu.ui.Sound

/**
 * Cờ Tỷ Phú Việt Nam 3D — bản Android. Một Activity: khung OpenGL ES (bàn cờ 3D) phủ toàn màn hình,
 * giao diện Compose vẽ chồng lên trên.
 */
class MainActivity : ComponentActivity() {
    private lateinit var surface: GameSurface
    private lateinit var sound: Sound

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        loadFonts()

        val engine = Engine()
        sound = Sound(this)
        val controller = GameController(engine, sound, SaveStore(this), lifecycleScope, resources.displayMetrics.density)
        surface = GameSurface(this, engine) { x, y -> controller.onTap(x, y) }

        setContent {
            CoTyPhuTheme {
                GameScreen(controller) { surface }
            }
        }
    }

    /** Font Be Vietnam Pro cho chữ vẽ lên texture (tên ô, giá, mặt trống đồng). */
    private fun loadFonts() {
        fun font(id: Int) = ResourcesCompat.getFont(this, id)
        font(R.font.be_vietnam_pro_regular)?.let { Fonts.regular = it }
        font(R.font.be_vietnam_pro_semibold)?.let { Fonts.semibold = it }
        font(R.font.be_vietnam_pro_bold)?.let { Fonts.bold = it }
        font(R.font.be_vietnam_pro_extrabold)?.let { Fonts.extrabold = it }
        font(R.font.be_vietnam_pro_black)?.let { Fonts.black = it }
    }

    override fun onResume() {
        super.onResume()
        surface.onResume()
    }

    override fun onPause() {
        surface.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        sound.release()
        super.onDestroy()
    }
}
