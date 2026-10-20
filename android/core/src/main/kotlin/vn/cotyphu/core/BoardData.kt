package vn.cotyphu.core

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// Dữ liệu bàn cờ "Cờ Tỷ Phú Việt Nam" — port 1:1 từ vietnam-3d/js/data/board-data.js.

const val START_SALARY = 200
const val START_MONEY = 1500
const val JAIL_FINE = 50
const val JAIL_INDEX = 10
const val GO_TO_JAIL_INDEX = 30
const val MAX_JAIL_TURNS = 3
/** 1–3: nhà phố, 4: khách sạn. */
const val MAX_LEVEL = 4

enum class TileType { GO, PROPERTY, COMMUNITY, TAX, TRANSPORT, CHANCE, JAIL, UTILITY, PARKING, GOTOJAIL }

/** Nhóm màu — mỗi nhóm là một vùng/miền. [color] dạng 0xAARRGGBB. */
data class Group(val key: String, val name: String, val color: Long, val buildCost: Int)

data class Tile(
    val index: Int,
    val type: TileType,
    val name: String,
    val model: String,
    val info: String,
    val group: String? = null,
    val price: Int = 0,
    val rent: List<Int> = emptyList(),
    val amount: Int = 0,
) {
    val ownable get() = type == TileType.PROPERTY || type == TileType.TRANSPORT || type == TileType.UTILITY
}

object Board {
    val GROUPS: Map<String, Group> = listOf(
        Group("brown", "Đại ngàn Tây Nguyên", 0xFF8D5A3B, 50),
        Group("lightblue", "Địa đầu phía Bắc", 0xFF4FB3D9, 50),
        Group("pink", "Miền Tây sông nước", 0xFFD6588F, 100),
        Group("orange", "Biển xanh Nam Trung Bộ", 0xFFEF8A2A, 100),
        Group("red", "Di sản miền Trung", 0xFFD7262E, 150),
        Group("yellow", "Kỳ quan Đà Nẵng – Quảng Bình", 0xFFF2C230, 150),
        Group("green", "Thăng Long ngàn năm", 0xFF2F9E5B, 200),
        Group("darkblue", "Biểu tượng quốc gia", 0xFF2A4FA0, 200),
    ).associateBy { it.key }

    /** Tiền thuê theo cấp: [đất trống, 1 nhà, 2 nhà, 3 nhà, khách sạn]. */
    fun rentTable(price: Int): List<Int> {
        val base = max(2, (price / 10.0).roundToInt())
        fun r5(v: Int) = max(5, (v / 5.0).roundToInt() * 5)
        return listOf(base, r5(base * 5), r5(base * 15), r5(base * 40), r5(base * 60))
    }

    private val defs = mutableListOf<Tile>()

    private fun special(type: TileType, name: String, model: String, info: String, amount: Int = 0) {
        defs += Tile(defs.size, type, name, model, info, amount = amount)
    }

    private fun property(name: String, group: String, price: Int, model: String, info: String) {
        defs += Tile(defs.size, TileType.PROPERTY, name, model, info, group, price, rentTable(price))
    }

    private fun transport(name: String, model: String, info: String) {
        defs += Tile(defs.size, TileType.TRANSPORT, name, model, info, price = 200)
    }

    private fun utility(name: String, model: String, info: String) {
        defs += Tile(defs.size, TileType.UTILITY, name, model, info, price = 150)
    }

    private const val COMMUNITY_INFO = "Rút một thẻ Khí vận — lì xì, thưởng Tết hay hoá đơn bất ngờ."
    private const val CHANCE_INFO = "Rút một thẻ Cơ hội — có thể là một chuyến du lịch bất ngờ!"

    init {
        special(TileType.GO, "Xuất phát", "go", "Mỗi lần đi qua hoặc dừng ở đây bạn nhận $START_SALARY tr₫ \"lương\" từ ngân hàng.")
        property("Nhà thờ Gỗ Kon Tum", "brown", 60, "woodenChurch",
            "Xây dựng 1913–1918, kết hợp phong cách Roman với kiến trúc nhà sàn Ba Na, toàn bộ bằng gỗ cà chít.")
        special(TileType.COMMUNITY, "Khí vận", "envelope", COMMUNITY_INFO)
        property("Thác Dray Nur", "brown", 60, "waterfall",
            "Ngọn thác hùng vĩ trên dòng Sêrêpốk, gắn với truyền thuyết tình yêu của người Ê Đê.")
        special(TileType.TAX, "Thuế thu nhập", "coins",
            "Nộp 200 tr₫ vào Quỹ chung. Ai dừng ở Bến Ninh Kiều sẽ nhận toàn bộ Quỹ.", amount = 200)
        transport("Ga Hà Nội", "train", "Nhà ga xây năm 1902, điểm đầu tuyến đường sắt Thống Nhất Bắc – Nam.")
        property("Cột cờ Lũng Cú", "lightblue", 100, "flagpole",
            "Nơi địa đầu cực Bắc Tổ quốc; lá cờ rộng 54 m² tượng trưng cho 54 dân tộc anh em.")
        special(TileType.CHANCE, "Cơ hội", "question", CHANCE_INFO)
        property("Ruộng bậc thang Mù Cang Chải", "lightblue", 100, "terraces",
            "Những thửa ruộng bậc thang của người Mông, đẹp nhất mùa lúa chín tháng 9 – 10.")
        property("Đỉnh Fansipan", "lightblue", 120, "mountain",
            "\"Nóc nhà Đông Dương\" cao 3.143 m thuộc dãy Hoàng Liên Sơn.")

        special(TileType.JAIL, "Nhà tạm giữ", "jail",
            "Chỉ ghé thăm thì không sao. Bị giữ thì phải đổ đôi, dùng thẻ ân xá hoặc nộp phạt 50 tr₫.")
        property("Chợ nổi Cái Răng", "pink", 140, "floatingMarket",
            "Chợ họp trên sông ở Cần Thơ, ghe xuồng treo hàng trên cây \"bẹo\"; di sản văn hoá phi vật thể quốc gia.")
        utility("Thuỷ điện Hoà Bình", "dam",
            "Công trình thế kỷ trên sông Đà, khởi công 1979, hoàn thành 1994 với công suất 1.920 MW.")
        property("Rừng tràm Trà Sư", "pink", 140, "mangrove",
            "Rừng tràm ngập nước ở Tịnh Biên, An Giang — thiên đường của chim trời và bèo tấm xanh mướt.")
        property("Đất Mũi Cà Mau", "pink", 160, "shipMonument",
            "Điểm cực Nam đất liền, nổi bật với biểu tượng con tàu hướng ra biển và rừng đước bạt ngàn.")
        transport("Cảng Cái Mép – Thị Vải", "ship",
            "Cụm cảng nước sâu đón được tàu container lớn nhất thế giới, cửa ngõ xuất khẩu phía Nam.")
        property("Tháp Bà Ponagar", "orange", 180, "chamTowers",
            "Quần thể tháp Chăm ở Nha Trang, xây từ thế kỷ 7 – 12, thờ nữ thần Yang Pô Inư Nagar.")
        special(TileType.COMMUNITY, "Khí vận", "envelope", COMMUNITY_INFO)
        property("Đồi cát Mũi Né", "orange", 180, "dunes",
            "Những đồi cát đỏ, cát trắng đổi hình theo gió — \"tiểu sa mạc\" ven biển Phan Thiết.")
        property("Ghềnh Đá Đĩa", "orange", 200, "basalt",
            "Hàng nghìn cột đá bazan hình lục giác xếp khít nhau, hình thành từ dung nham nguội đặc.")

        special(TileType.PARKING, "Bến Ninh Kiều", "pier",
            "Nghỉ chân bên sông Hậu. Người dừng ở đây nhận toàn bộ tiền trong Quỹ chung.")
        property("Thánh địa Mỹ Sơn", "red", 220, "mySon",
            "Quần thể đền tháp Chăm Pa thế kỷ 4 – 13, di sản văn hoá thế giới UNESCO năm 1999.")
        special(TileType.CHANCE, "Cơ hội", "question", CHANCE_INFO)
        property("Phố cổ Hội An", "red", 220, "hoiAn",
            "Thương cảng sầm uất thế kỷ 15 – 19 với Chùa Cầu và đèn lồng lung linh; di sản UNESCO năm 1999.")
        property("Kinh thành Huế", "red", 240, "hueCitadel",
            "Kinh đô triều Nguyễn (1802 – 1945); Quần thể di tích Cố đô Huế là di sản UNESCO năm 1993.")
        transport("Sân bay Nội Bài", "plane", "Cửa ngõ hàng không của Thủ đô, nhà ga T2 khánh thành năm 2015.")
        property("Cầu Rồng", "yellow", 260, "dragonBridge",
            "Cây cầu hình rồng dài 666 m bắc qua sông Hàn, phun lửa và phun nước vào tối cuối tuần.")
        property("Cầu Vàng Bà Nà", "yellow", 260, "goldenBridge",
            "Cây cầu dát vàng dài khoảng 150 m được nâng đỡ bởi đôi bàn tay đá khổng lồ, mở cửa năm 2018.")
        utility("Điện gió Bạc Liêu", "windTurbine",
            "Nhà máy điện gió gần bờ đầu tiên của Việt Nam với hàng chục tua-bin vươn ra biển.")
        property("Hang Sơn Đoòng", "yellow", 280, "cave",
            "Hang động lớn nhất thế giới trong Vườn quốc gia Phong Nha – Kẻ Bàng, được khảo sát năm 2009.")

        special(TileType.GOTOJAIL, "Vào tù", "whistle",
            "Bị cảnh sát thổi còi! Đi thẳng vào Nhà tạm giữ, không đi qua Xuất phát, không nhận lương.")
        property("Văn Miếu – Quốc Tử Giám", "green", 300, "vanMieu",
            "Xây năm 1070, nơi có trường đại học đầu tiên của Việt Nam và 82 bia Tiến sĩ.")
        property("Chùa Một Cột", "green", 300, "onePillar",
            "Ngôi chùa hình bông sen nở trên một cột đá giữa hồ, dựng năm 1049 thời vua Lý Thái Tông.")
        special(TileType.COMMUNITY, "Khí vận", "envelope", COMMUNITY_INFO)
        property("Hồ Gươm – Tháp Rùa", "green", 320, "turtleTower",
            "Trái tim Hà Nội, gắn với truyền thuyết vua Lê Lợi trả gươm cho Rùa Vàng.")
        transport("Sân bay Tân Sơn Nhất", "plane2", "Sân bay quốc tế bận rộn nhất Việt Nam, giữa lòng TP. Hồ Chí Minh.")
        special(TileType.CHANCE, "Cơ hội", "question", CHANCE_INFO)
        property("Landmark 81", "darkblue", 350, "skyscraper",
            "Toà nhà cao nhất Việt Nam (461 m, 81 tầng) bên sông Sài Gòn, khánh thành năm 2018.")
        special(TileType.TAX, "Thuế xa xỉ", "coins", "Nộp 100 tr₫ vào Quỹ chung vì chơi sang!", amount = 100)
        property("Vịnh Hạ Long", "darkblue", 400, "halong",
            "Kỳ quan thiên nhiên thế giới với gần 2.000 đảo đá vôi; di sản thiên nhiên UNESCO.")
    }

    val TILES: List<Tile> = defs.toList()
    val SIZE: Int get() = TILES.size

    fun groupTiles(group: String): List<Int> = TILES.filter { it.group == group }.map { it.index }
}

private val vnNumber: NumberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))

/** "1.500 tr₫" — cùng định dạng với bản web. */
fun formatMoney(v: Int): String {
    val sign = if (v < 0) "-" else ""
    return "$sign${vnNumber.format(abs(v).toLong())} tr₫"
}
