// Thẻ Cơ hội & Khí vận. Game gốc chỉ có thẻ cộng/trừ tiền (core/card_deck.py);
// ở đây bổ sung thêm thẻ di chuyển, vào tù, ân xá, sửa nhà, lì xì...

export const CHANCE_CARDS = [
  { text: 'Đi phượt Hà Giang! Tiến tới Cột cờ Lũng Cú.', action: { kind: 'moveTo', target: 6 } },
  { text: 'Vé máy bay giá rẻ: bay thẳng tới Vịnh Hạ Long.', action: { kind: 'moveTo', target: 39 } },
  { text: 'Tham quan đêm rằm Phố cổ Hội An. Tiến tới Phố cổ Hội An.', action: { kind: 'moveTo', target: 23 } },
  { text: 'Đăng ký tour khám phá Sơn Đoòng. Tiến tới Hang Sơn Đoòng.', action: { kind: 'moveTo', target: 29 } },
  { text: 'Về quê ăn Tết! Tiến về ô Xuất phát và nhận lương.', action: { kind: 'moveTo', target: 0 } },
  { text: 'Đặt vé đi công tác: tới ga/cảng/sân bay gần nhất. Nếu đã có chủ, trả gấp đôi tiền vé.', action: { kind: 'nearest', type: 'transport' } },
  { text: 'Lạc đường ở chợ nổi, lùi lại 3 bước.', action: { kind: 'moveBy', steps: -3 } },
  { text: 'Vi phạm nồng độ cồn! Đi thẳng vào Nhà tạm giữ.', action: { kind: 'jail' } },
  { text: 'Thẻ ân xá: ra khỏi Nhà tạm giữ miễn phí (giữ lại đến khi cần).', action: { kind: 'jailFree' } },
  { text: 'Trúng giải Jackpot Vietlott! Nhận 150 tr₫.', action: { kind: 'money', amount: 150 } },
  { text: 'Vượt đèn đỏ bị phạt nguội. Nộp 15 tr₫.', action: { kind: 'money', amount: -15 } },
  { text: 'Mùa bão về, gia cố nhà cửa: 25 tr₫ mỗi nhà, 100 tr₫ mỗi khách sạn.', action: { kind: 'repairs', house: 25, hotel: 100 } },
  { text: 'Được bầu làm trưởng đoàn du lịch: bao mỗi người chơi 50 tr₫.', action: { kind: 'payEach', amount: 50 } },
  { text: 'Trúng mùa cà phê Tây Nguyên! Nhận 100 tr₫.', action: { kind: 'money', amount: 100 } },
];

export const COMMUNITY_CARDS = [
  { text: 'Mùng 1 Tết: mỗi người chơi lì xì bạn 20 tr₫.', action: { kind: 'collectEach', amount: 20 } },
  { text: 'Thưởng Tết cuối năm. Nhận 100 tr₫.', action: { kind: 'money', amount: 100 } },
  { text: 'Lương tháng 13 về tài khoản. Nhận 100 tr₫.', action: { kind: 'money', amount: 100 } },
  { text: 'Đóng học phí cho con. Nộp 100 tr₫.', action: { kind: 'money', amount: -100 } },
  { text: 'Đi khám sức khoẻ tổng quát. Nộp 50 tr₫.', action: { kind: 'money', amount: -50 } },
  { text: 'Được hoàn thuế thu nhập cá nhân. Nhận 20 tr₫.', action: { kind: 'money', amount: 20 } },
  { text: 'Bán bánh chưng ngày Tết đắt hàng. Nhận 45 tr₫.', action: { kind: 'money', amount: 45 } },
  { text: 'Hoá đơn tiền điện mùa nắng nóng. Nộp 40 tr₫.', action: { kind: 'money', amount: -40 } },
  { text: 'Hôm nay sinh nhật bạn: mỗi người chơi tặng 10 tr₫.', action: { kind: 'collectEach', amount: 10 } },
  { text: 'Đoạt giải ảnh đẹp Festival Huế. Nhận 25 tr₫.', action: { kind: 'money', amount: 25 } },
  { text: 'Đi chuyến tàu Thống Nhất về Xuất phát, nhận lương.', action: { kind: 'moveTo', target: 0 } },
  { text: 'Trốn thuế bị phát hiện! Đi thẳng vào Nhà tạm giữ.', action: { kind: 'jail' } },
  { text: 'Thẻ ân xá: ra khỏi Nhà tạm giữ miễn phí (giữ lại đến khi cần).', action: { kind: 'jailFree' } },
  { text: 'Trùng tu nhà cổ: 40 tr₫ mỗi nhà, 115 tr₫ mỗi khách sạn.', action: { kind: 'repairs', house: 40, hotel: 115 } },
];
