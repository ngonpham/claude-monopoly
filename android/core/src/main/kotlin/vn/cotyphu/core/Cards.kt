package vn.cotyphu.core

// Thẻ Cơ hội & Khí vận — port từ vietnam-3d/js/data/cards.js.

sealed interface CardAction {
    data class Money(val amount: Int) : CardAction
    data class MoveTo(val target: Int) : CardAction
    data class MoveBy(val steps: Int) : CardAction
    data class Nearest(val type: TileType) : CardAction
    data object Jail : CardAction
    data object JailFree : CardAction
    data class Repairs(val house: Int, val hotel: Int) : CardAction
    data class PayEach(val amount: Int) : CardAction
    data class CollectEach(val amount: Int) : CardAction
}

data class Card(val text: String, val action: CardAction)

enum class Deck(val label: String) { CHANCE("Cơ hội"), COMMUNITY("Khí vận") }

object Cards {
    val CHANCE = listOf(
        Card("Đi phượt Hà Giang! Tiến tới Cột cờ Lũng Cú.", CardAction.MoveTo(6)),
        Card("Vé máy bay giá rẻ: bay thẳng tới Vịnh Hạ Long.", CardAction.MoveTo(39)),
        Card("Tham quan đêm rằm Phố cổ Hội An. Tiến tới Phố cổ Hội An.", CardAction.MoveTo(23)),
        Card("Đăng ký tour khám phá Sơn Đoòng. Tiến tới Hang Sơn Đoòng.", CardAction.MoveTo(29)),
        Card("Về quê ăn Tết! Tiến về ô Xuất phát và nhận lương.", CardAction.MoveTo(0)),
        Card("Đặt vé đi công tác: tới ga/cảng/sân bay gần nhất. Nếu đã có chủ, trả gấp đôi tiền vé.", CardAction.Nearest(TileType.TRANSPORT)),
        Card("Lạc đường ở chợ nổi, lùi lại 3 bước.", CardAction.MoveBy(-3)),
        Card("Vi phạm nồng độ cồn! Đi thẳng vào Nhà tạm giữ.", CardAction.Jail),
        Card("Thẻ ân xá: ra khỏi Nhà tạm giữ miễn phí (giữ lại đến khi cần).", CardAction.JailFree),
        Card("Trúng giải Jackpot Vietlott! Nhận 150 tr₫.", CardAction.Money(150)),
        Card("Vượt đèn đỏ bị phạt nguội. Nộp 15 tr₫.", CardAction.Money(-15)),
        Card("Mùa bão về, gia cố nhà cửa: 25 tr₫ mỗi nhà, 100 tr₫ mỗi khách sạn.", CardAction.Repairs(25, 100)),
        Card("Được bầu làm trưởng đoàn du lịch: bao mỗi người chơi 50 tr₫.", CardAction.PayEach(50)),
        Card("Trúng mùa cà phê Tây Nguyên! Nhận 100 tr₫.", CardAction.Money(100)),
    )

    val COMMUNITY = listOf(
        Card("Mùng 1 Tết: mỗi người chơi lì xì bạn 20 tr₫.", CardAction.CollectEach(20)),
        Card("Thưởng Tết cuối năm. Nhận 100 tr₫.", CardAction.Money(100)),
        Card("Lương tháng 13 về tài khoản. Nhận 100 tr₫.", CardAction.Money(100)),
        Card("Đóng học phí cho con. Nộp 100 tr₫.", CardAction.Money(-100)),
        Card("Đi khám sức khoẻ tổng quát. Nộp 50 tr₫.", CardAction.Money(-50)),
        Card("Được hoàn thuế thu nhập cá nhân. Nhận 20 tr₫.", CardAction.Money(20)),
        Card("Bán bánh chưng ngày Tết đắt hàng. Nhận 45 tr₫.", CardAction.Money(45)),
        Card("Hoá đơn tiền điện mùa nắng nóng. Nộp 40 tr₫.", CardAction.Money(-40)),
        Card("Hôm nay sinh nhật bạn: mỗi người chơi tặng 10 tr₫.", CardAction.CollectEach(10)),
        Card("Đoạt giải ảnh đẹp Festival Huế. Nhận 25 tr₫.", CardAction.Money(25)),
        Card("Đi chuyến tàu Thống Nhất về Xuất phát, nhận lương.", CardAction.MoveTo(0)),
        Card("Trốn thuế bị phát hiện! Đi thẳng vào Nhà tạm giữ.", CardAction.Jail),
        Card("Thẻ ân xá: ra khỏi Nhà tạm giữ miễn phí (giữ lại đến khi cần).", CardAction.JailFree),
        Card("Trùng tu nhà cổ: 40 tr₫ mỗi nhà, 115 tr₫ mỗi khách sạn.", CardAction.Repairs(40, 115)),
    )

    fun of(deck: Deck) = if (deck == Deck.CHANCE) CHANCE else COMMUNITY
}
