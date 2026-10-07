// "Máy" chơi đơn giản: giữ tiền dự phòng, ưu tiên gom trọn vùng.
import { TILES, JAIL_FINE, groupTiles } from '../data/board-data.js';

const RESERVE_BUY = 150;
const RESERVE_BUILD = 250;

/** Quyết định trước khi đổ: 'card' | 'fine' | 'roll'. */
export function botJailChoice(game) {
  const p = game.current;
  if (!p.inJail) return 'roll';
  if (p.jailFree.length > 0) return 'card';
  // Đầu game nên ra tù sớm để mua đất; cuối game ở tù lại an toàn.
  const unowned = game.state.tiles.filter((t, i) => t.owner === null && TILES[i].price).length;
  if (p.money > JAIL_FINE + 300 && unowned > 8) return 'fine';
  return 'roll';
}

export function botDecide(game) {
  const s = game.state;
  const p = game.current;
  const pending = s.pending;
  if (!pending) return false;
  if (pending.kind === 'buy') {
    const tile = TILES[pending.tile];
    const after = p.money - tile.price;
    if (tile.group) {
      const others = groupTiles(tile.group).filter((i) => i !== pending.tile);
      const completes = others.every((i) => s.tiles[i].owner === p.id);
      if (completes && after >= 0) return true;
    }
    return after >= RESERVE_BUY;
  }
  if (pending.kind === 'build') {
    const affordable = pending.options.filter((o) => p.money - o.cost >= RESERVE_BUILD);
    return affordable.length ? affordable[affordable.length - 1].level : false;
  }
  return false;
}

/** Danh sách ô bot muốn xây thêm trong pha quản lý (mỗi lần trả về 1 ô hoặc null). */
export function botNextBuild(game) {
  const p = game.current;
  const candidates = game.ownedTiles(p.id)
    .filter((i) => game.canBuildFreely(p.id, i) && p.money - game.buildCost(i) >= RESERVE_BUILD)
    .sort((a, b) => game.state.tiles[a].level - game.state.tiles[b].level || TILES[b].price - TILES[a].price);
  return candidates.length ? candidates[0] : null;
}
