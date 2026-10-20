package vn.cotyphu

import android.graphics.Bitmap
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.cotyphu.core.Board
import vn.cotyphu.gl.Engine
import vn.cotyphu.gl.Mesh
import vn.cotyphu.gl.Node
import vn.cotyphu.gl.Vec3
import vn.cotyphu.render.Board3D
import vn.cotyphu.render.Dice3D
import vn.cotyphu.render.Fonts
import vn.cotyphu.render.LANDMARK_COUNT
import vn.cotyphu.render.TILE_TOP
import vn.cotyphu.render.Token
import vn.cotyphu.render.centerTexture
import vn.cotyphu.render.cornerTexture
import vn.cotyphu.render.tileAt
import vn.cotyphu.render.tileFrame
import vn.cotyphu.render.tileTexture
import java.io.File

/**
 * Dựng toàn bộ cảnh 3D trên JVM (Robolectric, Canvas thật) và xuất ảnh xem trước vào
 * app/build/preview/ — không cần GPU hay máy ảo Android.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderPreviewTest {
    private val out = File("build/preview")

    @Before
    fun loadFonts() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ResourcesCompat.getFont(ctx, R.font.be_vietnam_pro_regular)?.let { Fonts.regular = it }
        ResourcesCompat.getFont(ctx, R.font.be_vietnam_pro_semibold)?.let { Fonts.semibold = it }
        ResourcesCompat.getFont(ctx, R.font.be_vietnam_pro_bold)?.let { Fonts.bold = it }
        ResourcesCompat.getFont(ctx, R.font.be_vietnam_pro_extrabold)?.let { Fonts.extrabold = it }
        ResourcesCompat.getFont(ctx, R.font.be_vietnam_pro_black)?.let { Fonts.black = it }
    }

    private fun savePng(bmp: Bitmap, name: String) {
        File(out, name).also { f -> f.parentFile.mkdirs(); f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    @Test
    fun texturesRenderVietnameseText() {
        // một vài ô tiêu biểu + góc + mặt trống đồng
        for (i in listOf(1, 5, 8, 12, 23, 39)) savePng(tileTexture(Board.TILES[i]).renderBitmap(), "tile-$i.png")
        for (i in listOf(0, 10, 20, 30)) savePng(cornerTexture(Board.TILES[i]).renderBitmap(), "corner-$i.png")
        val center = centerTexture().renderBitmap()
        assertEquals(2048, center.width)
        savePng(Bitmap.createScaledBitmap(center, 512, 512, true), "center.png")
    }

    @Test
    fun tileLookupMatchesTileFrames() {
        for (i in 0 until Board.SIZE) {
            val f = tileFrame(i)
            assertEquals("ô $i", i, tileAt(f.x, f.z))
        }
        assertEquals(null, tileAt(0f, 0f)) // giữa bàn
    }

    @Test
    fun wholeSceneBuildsBatchesAndRenders() {
        assertEquals(35, LANDMARK_COUNT)
        val engine = Engine()
        val board = Board3D()
        val dice = Dice3D(engine)
        val root = Node().add(board.group, dice.group)

        // vài chủ sở hữu, nhà và khách sạn để soát mô hình công trình
        board.setOwner(1, 0xE53935); board.setLevel(1, 2, 0xE53935)
        board.setOwner(3, 0xE53935); board.setLevel(3, 4, 0xE53935)
        board.setOwner(39, 0x1E88E5); board.setLevel(39, 3, 0x1E88E5)
        board.setOwner(23, 0x8E24AA)
        val tokens = listOf("hat", "lantern", "drum", "lotus").mapIndexed { id, t ->
            Token(engine, t, intArrayOf(0xE53935, 0x1E88E5, 0x8E24AA, 0x00897B)[id]).also {
                it.group.position.set(board.tokenSlot(0, id))
                root.add(it.group)
            }
        }
        assertEquals(4, tokens.size)

        var meshes = 0
        root.traverse { if (it is Mesh) meshes++ }
        println("Số lưới (lệnh vẽ) sau khi gộp tĩnh: $meshes")
        assertTrue("gộp tĩnh phải giảm số lệnh vẽ xuống dưới 900, đang là $meshes", meshes < 900)

        // ô 0 (Xuất phát) ở góc dưới-phải, ô 10 ở góc dưới-trái như bản web
        val go = board.tileCenter(0); val jail = board.tileCenter(10)
        assertTrue(go.x > 0 && go.z > 0 && jail.x < 0 && jail.z > 0)
        assertTrue(board.tokenSlot(5, 0).y in TILE_TOP - 0.01f..TILE_TOP + 0.01f)

        val r = SoftRenderer(960, 600)
        val views = mapOf(
            "overview" to (Vec3(0f, 30f, 31f) to Vec3()),
            "bottom" to (Vec3(0f, 7f, 22f) to Vec3(0f, 0f, 10f)),
            "left" to (Vec3(-22f, 7f, 0f) to Vec3(-10f, 0f, 0f)),
            "top" to (Vec3(0f, 7f, -22f) to Vec3(0f, 0f, -10f)),
            "right" to (Vec3(22f, 7f, 0f) to Vec3(10f, 0f, 0f)),
            "go-corner" to (Vec3(16f, 6f, 17f) to Vec3(10f, 0.5f, 10f)),
        )
        for ((name, v) in views) {
            r.render(root, v.first, v.second)
            r.save(File(out, "scene-$name.png"))
        }
        println("Tam giác đã vẽ (lần cuối): ${r.triangles}")
        assertTrue(r.triangles > 1000)
    }
}
