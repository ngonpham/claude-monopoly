// Dữ liệu bàn cờ "Cờ Tỷ Phú Việt Nam".
// Bố cục 40 ô giữ nguyên tinh thần game gốc (monopoly/core/board.py):
// ô 0 Xuất phát, ô 10/30 liên quan đến "tù", ô 20 bãi nghỉ, các ô Cơ hội,
// hạ tầng (infra) và đất xây dựng — nhưng thay toàn bộ địa danh CMU bằng
// công trình / thắng cảnh tiêu biểu của Việt Nam, gom theo vùng miền.

export const START_SALARY = 200;
export const START_MONEY = 1500;
export const JAIL_FINE = 50;
export const JAIL_INDEX = 10;
export const GO_TO_JAIL_INDEX = 30;
export const MAX_JAIL_TURNS = 3;
export const MAX_LEVEL = 4; // 1–3: nhà phố, 4: khách sạn / công trình biểu tượng

// Nhóm màu — mỗi nhóm là một vùng/miền.
export const GROUPS = {
  brown:     { name: 'Đại ngàn Tây Nguyên',   color: '#8d5a3b', buildCost: 50 },
  lightblue: { name: 'Địa đầu phía Bắc',      color: '#4fb3d9', buildCost: 50 },
  pink:      { name: 'Miền Tây sông nước',     color: '#d6588f', buildCost: 100 },
  orange:    { name: 'Biển xanh Nam Trung Bộ', color: '#ef8a2a', buildCost: 100 },
  red:       { name: 'Di sản miền Trung',      color: '#d7262e', buildCost: 150 },
  yellow:    { name: 'Kỳ quan Đà Nẵng – Quảng Bình', color: '#f2c230', buildCost: 150 },
  green:     { name: 'Thăng Long ngàn năm',    color: '#2f9e5b', buildCost: 200 },
  darkblue:  { name: 'Biểu tượng quốc gia',    color: '#2a4fa0', buildCost: 200 },
};

// Tiền thuê theo cấp: [đất trống, 1 nhà, 2 nhà, 3 nhà, khách sạn].
function rentTable(price) {
  const base = Math.max(2, Math.round(price / 10));
  const r5 = (v) => Math.max(5, Math.round(v / 5) * 5);
  return [base, r5(base * 5), r5(base * 15), r5(base * 40), r5(base * 60)];
}

function property(name, group, price, model, info) {
  return { type: 'property', name, group, price, rent: rentTable(price), model, info };
}

function transport(name, model, info) {
  return { type: 'transport', name, price: 200, model, info };
}

function utility(name, model, info) {
  return { type: 'utility', name, price: 150, model, info };
}

export const TILES = [
  { type: 'go', name: 'Xuất phát', model: 'go',
    info: `Mỗi lần đi qua hoặc dừng ở đây bạn nhận ${START_SALARY} tr₫ "lương" từ ngân hàng.` },
  property('Nhà thờ Gỗ Kon Tum', 'brown', 60, 'woodenChurch',
    'Xây dựng 1913–1918, kết hợp phong cách Roman với kiến trúc nhà sàn Ba Na, toàn bộ bằng gỗ cà chít.'),
  { type: 'community', name: 'Khí vận', model: 'envelope', info: 'Rút một thẻ Khí vận — lì xì, thưởng Tết hay hoá đơn bất ngờ.' },
  property('Thác Dray Nur', 'brown', 60, 'waterfall',
    'Ngọn thác hùng vĩ trên dòng Sêrêpốk, gắn với truyền thuyết tình yêu của người Ê Đê.'),
  { type: 'tax', name: 'Thuế thu nhập', amount: 200, model: 'coins',
    info: 'Nộp 200 tr₫ vào Quỹ chung. Ai dừng ở Bến Ninh Kiều sẽ nhận toàn bộ Quỹ.' },
  transport('Ga Hà Nội', 'train',
    'Nhà ga xây năm 1902, điểm đầu tuyến đường sắt Thống Nhất Bắc – Nam.'),
  property('Cột cờ Lũng Cú', 'lightblue', 100, 'flagpole',
    'Nơi địa đầu cực Bắc Tổ quốc; lá cờ rộng 54 m² tượng trưng cho 54 dân tộc anh em.'),
  { type: 'chance', name: 'Cơ hội', model: 'question', info: 'Rút một thẻ Cơ hội — có thể là một chuyến du lịch bất ngờ!' },
  property('Ruộng bậc thang Mù Cang Chải', 'lightblue', 100, 'terraces',
    'Những thửa ruộng bậc thang của người Mông, đẹp nhất mùa lúa chín tháng 9 – 10.'),
  property('Đỉnh Fansipan', 'lightblue', 120, 'mountain',
    '"Nóc nhà Đông Dương" cao 3.143 m thuộc dãy Hoàng Liên Sơn.'),

  { type: 'jail', name: 'Nhà tạm giữ', model: 'jail',
    info: 'Chỉ ghé thăm thì không sao. Bị giữ thì phải đổ đôi, dùng thẻ ân xá hoặc nộp phạt 50 tr₫.' },
  property('Chợ nổi Cái Răng', 'pink', 140, 'floatingMarket',
    'Chợ họp trên sông ở Cần Thơ, ghe xuồng treo hàng trên cây "bẹo"; di sản văn hoá phi vật thể quốc gia.'),
  utility('Thuỷ điện Hoà Bình', 'dam',
    'Công trình thế kỷ trên sông Đà, khởi công 1979, hoàn thành 1994 với công suất 1.920 MW.'),
  property('Rừng tràm Trà Sư', 'pink', 140, 'mangrove',
    'Rừng tràm ngập nước ở Tịnh Biên, An Giang — thiên đường của chim trời và bèo tấm xanh mướt.'),
  property('Đất Mũi Cà Mau', 'pink', 160, 'shipMonument',
    'Điểm cực Nam đất liền, nổi bật với biểu tượng con tàu hướng ra biển và rừng đước bạt ngàn.'),
  transport('Cảng Cái Mép – Thị Vải', 'ship',
    'Cụm cảng nước sâu đón được tàu container lớn nhất thế giới, cửa ngõ xuất khẩu phía Nam.'),
  property('Tháp Bà Ponagar', 'orange', 180, 'chamTowers',
    'Quần thể tháp Chăm ở Nha Trang, xây từ thế kỷ 7 – 12, thờ nữ thần Yang Pô Inư Nagar.'),
  { type: 'community', name: 'Khí vận', model: 'envelope', info: 'Rút một thẻ Khí vận — lì xì, thưởng Tết hay hoá đơn bất ngờ.' },
  property('Đồi cát Mũi Né', 'orange', 180, 'dunes',
    'Những đồi cát đỏ, cát trắng đổi hình theo gió — "tiểu sa mạc" ven biển Phan Thiết.'),
  property('Ghềnh Đá Đĩa', 'orange', 200, 'basalt',
    'Hàng nghìn cột đá bazan hình lục giác xếp khít nhau, hình thành từ dung nham nguội đặc.'),

  { type: 'parking', name: 'Bến Ninh Kiều', model: 'pier',
    info: 'Nghỉ chân bên sông Hậu. Người dừng ở đây nhận toàn bộ tiền trong Quỹ chung.' },
  property('Thánh địa Mỹ Sơn', 'red', 220, 'mySon',
    'Quần thể đền tháp Chăm Pa thế kỷ 4 – 13, di sản văn hoá thế giới UNESCO năm 1999.'),
  { type: 'chance', name: 'Cơ hội', model: 'question', info: 'Rút một thẻ Cơ hội — có thể là một chuyến du lịch bất ngờ!' },
  property('Phố cổ Hội An', 'red', 220, 'hoiAn',
    'Thương cảng sầm uất thế kỷ 15 – 19 với Chùa Cầu và đèn lồng lung linh; di sản UNESCO năm 1999.'),
  property('Kinh thành Huế', 'red', 240, 'hueCitadel',
    'Kinh đô triều Nguyễn (1802 – 1945); Quần thể di tích Cố đô Huế là di sản UNESCO năm 1993.'),
  transport('Sân bay Nội Bài', 'plane',
    'Cửa ngõ hàng không của Thủ đô, nhà ga T2 khánh thành năm 2015.'),
  property('Cầu Rồng', 'yellow', 260, 'dragonBridge',
    'Cây cầu hình rồng dài 666 m bắc qua sông Hàn, phun lửa và phun nước vào tối cuối tuần.'),
  property('Cầu Vàng Bà Nà', 'yellow', 260, 'goldenBridge',
    'Cây cầu dát vàng dài khoảng 150 m được nâng đỡ bởi đôi bàn tay đá khổng lồ, mở cửa năm 2018.'),
  utility('Điện gió Bạc Liêu', 'windTurbine',
    'Nhà máy điện gió gần bờ đầu tiên của Việt Nam với hàng chục tua-bin vươn ra biển.'),
  property('Hang Sơn Đoòng', 'yellow', 280, 'cave',
    'Hang động lớn nhất thế giới trong Vườn quốc gia Phong Nha – Kẻ Bàng, được khảo sát năm 2009.'),

  { type: 'gotojail', name: 'Vào tù', model: 'whistle',
    info: 'Bị cảnh sát thổi còi! Đi thẳng vào Nhà tạm giữ, không đi qua Xuất phát, không nhận lương.' },
  property('Văn Miếu – Quốc Tử Giám', 'green', 300, 'vanMieu',
    'Xây năm 1070, nơi có trường đại học đầu tiên của Việt Nam và 82 bia Tiến sĩ.'),
  property('Chùa Một Cột', 'green', 300, 'onePillar',
    'Ngôi chùa hình bông sen nở trên một cột đá giữa hồ, dựng năm 1049 thời vua Lý Thái Tông.'),
  { type: 'community', name: 'Khí vận', model: 'envelope', info: 'Rút một thẻ Khí vận — lì xì, thưởng Tết hay hoá đơn bất ngờ.' },
  property('Hồ Gươm – Tháp Rùa', 'green', 320, 'turtleTower',
    'Trái tim Hà Nội, gắn với truyền thuyết vua Lê Lợi trả gươm cho Rùa Vàng.'),
  transport('Sân bay Tân Sơn Nhất', 'plane2',
    'Sân bay quốc tế bận rộn nhất Việt Nam, giữa lòng TP. Hồ Chí Minh.'),
  { type: 'chance', name: 'Cơ hội', model: 'question', info: 'Rút một thẻ Cơ hội — có thể là một chuyến du lịch bất ngờ!' },
  property('Landmark 81', 'darkblue', 350, 'skyscraper',
    'Toà nhà cao nhất Việt Nam (461 m, 81 tầng) bên sông Sài Gòn, khánh thành năm 2018.'),
  { type: 'tax', name: 'Thuế xa xỉ', amount: 100, model: 'coins',
    info: 'Nộp 100 tr₫ vào Quỹ chung vì chơi sang!' },
  property('Vịnh Hạ Long', 'darkblue', 400, 'halong',
    'Kỳ quan thiên nhiên thế giới với gần 2.000 đảo đá vôi; di sản thiên nhiên UNESCO.'),
];

TILES.forEach((t, i) => { t.index = i; });

export const BOARD_SIZE = TILES.length;

export function groupTiles(group) {
  return TILES.filter((t) => t.group === group).map((t) => t.index);
}

export function formatMoney(v) {
  const sign = v < 0 ? '-' : '';
  return `${sign}${Math.abs(Math.round(v)).toLocaleString('vi-VN')} tr₫`;
}
