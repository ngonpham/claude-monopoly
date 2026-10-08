// Lớp giao diện HTML phủ lên cảnh 3D.
import { TILES, GROUPS, formatMoney, MAX_LEVEL } from '../data/board-data.js';
import { TOKENS } from '../render/tokens.js';

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

export const LEVEL_NAMES = ['Đất trống', '1 nhà phố', '2 nhà phố', '3 nhà phố', 'Khách sạn'];

export function playerTag(p) {
  return `<span class="pn" style="color:${p.color}">${esc(p.name)}</span>`;
}

export class Hud {
  constructor() {
    this.players = $('players');
    this.log = $('log');
    this.tileCard = $('tile-card');
    this.actionMsg = $('action-msg');
    this.actionButtons = $('action-buttons');
    this.toastEl = $('toast');
    this.floatLayer = $('float-layer');
    this.modal = $('modal');
    this.modalBox = $('modal-box');
    this.pinnedTile = null;
    this.onPlayerClick = null;
  }

  // ---------- người chơi ----------

  renderPlayers(game, displayedMoney) {
    const s = game.state;
    this.players.innerHTML = s.players.map((p) => {
      const owned = game.ownedTiles(p.id);
      const chips = owned.map((i) => {
        const t = TILES[i];
        const c = t.group ? GROUPS[t.group].color : (t.type === 'transport' ? '#37474f' : '#00695c');
        return `<span class="chip" style="background:${c}" title="${esc(t.name)}"></span>`;
      }).join('');
      const badges = [];
      if (p.isBot) badges.push('<span class="badge">🤖 Máy</span>');
      if (p.inJail) badges.push('<span class="badge">🔒 Tạm giữ</span>');
      if (p.jailFree.length) badges.push(`<span class="badge">🎫 Ân xá ×${p.jailFree.length}</span>`);
      if (p.bankrupt) badges.push('<span class="badge">💸 Phá sản</span>');
      const money = displayedMoney ? displayedMoney[p.id] : p.money;
      return `<div class="player-card panel ${s.current === p.id && s.phase !== 'over' ? 'active' : ''} ${p.bankrupt ? 'bankrupt' : ''}"
          style="--pc:${p.color}" data-id="${p.id}">
        <div class="row"><span class="dot"></span><span class="name">${esc(p.name)}</span>
          <span class="money" data-money="${p.id}">${formatMoney(money)}</span></div>
        <div class="meta"><span>${TOKENS[p.token] || ''}</span><span>Tài sản: ${formatMoney(game.netWorth(p.id))}</span>${badges.join('')}</div>
        <div class="chips">${chips}</div>
      </div>`;
    }).join('');
    this.players.querySelectorAll('.player-card').forEach((el) => {
      el.addEventListener('click', () => this.onPlayerClick && this.onPlayerClick(Number(el.dataset.id)));
    });
  }

  setMoney(id, value) {
    const el = this.players.querySelector(`[data-money="${id}"]`);
    if (el) el.textContent = formatMoney(value);
  }

  setRound(round, maxRounds) {
    $('round-info').textContent = maxRounds ? `Vòng ${Math.min(round, maxRounds)}/${maxRounds}` : `Vòng ${round}`;
  }

  setPot(pot) {
    $('pot-info').textContent = `Quỹ: ${formatMoney(pot)}`;
  }

  // ---------- nhật ký, thông báo ----------

  addLog(html, cls = '') {
    const li = document.createElement('li');
    li.innerHTML = html;
    if (cls) li.className = cls;
    this.log.prepend(li);
    while (this.log.children.length > 80) this.log.lastChild.remove();
  }

  clearLog() {
    this.log.innerHTML = '';
  }

  toast(text, ms = 1800) {
    this.toastEl.textContent = text;
    this.toastEl.classList.add('show');
    clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(() => this.toastEl.classList.remove('show'), ms);
  }

  floatText(pos, text, good) {
    if (!pos.visible) return;
    const el = document.createElement('div');
    el.className = `float-text ${good ? 'good' : 'bad'}`;
    el.textContent = text;
    el.style.left = `${pos.x}px`;
    el.style.top = `${pos.y}px`;
    this.floatLayer.appendChild(el);
    setTimeout(() => el.remove(), 1700);
  }

  // ---------- thẻ thông tin ô ----------

  showTileCard(index, game, pinned = false) {
    if (this.pinnedTile !== null && !pinned && index !== this.pinnedTile) return;
    if (pinned) this.pinnedTile = index;
    this.shownTile = index;
    const tile = TILES[index];
    const st = game.state.tiles[index];
    const band = tile.group ? GROUPS[tile.group] : null;
    const bandColor = band ? band.color : ({ transport: '#37474f', utility: '#00695c', chance: '#e06a00', community: '#b0102a', tax: '#6d4c41' }[tile.type] || '#7a1c14');
    const bandLabel = band ? band.name : ({ transport: 'Giao thông', utility: 'Năng lượng', chance: 'Cơ hội', community: 'Khí vận', tax: 'Thuế' }[tile.type] || 'Ô đặc biệt');
    let table = '';
    if (tile.type === 'property') {
      const rows = tile.rent.map((r, lv) => `<tr class="${st.owner !== null && st.level === lv ? 'current' : ''}"><td>${LEVEL_NAMES[lv]}${lv === 0 ? ' (×2 nếu đủ bộ)' : ''}</td><td>${formatMoney(r)}</td></tr>`).join('');
      table = `<table>${rows}<tr><td>Chi phí xây mỗi cấp</td><td>${formatMoney(GROUPS[tile.group].buildCost)}</td></tr></table>`;
    } else if (tile.type === 'transport') {
      table = `<table>${[1, 2, 3, 4].map((n) => `<tr><td>Sở hữu ${n} ô giao thông</td><td>${formatMoney(25 * 2 ** (n - 1))}</td></tr>`).join('')}</table>`;
    } else if (tile.type === 'utility') {
      table = '<table><tr><td>Sở hữu 1 nhà máy</td><td>×4 tổng xúc xắc</td></tr><tr><td>Sở hữu cả 2</td><td>×10 tổng xúc xắc</td></tr></table>';
    }
    let owner = '';
    if (tile.price) {
      if (st.owner === null) owner = `<div class="owner">Chưa có chủ · Giá ${formatMoney(tile.price)}</div>`;
      else {
        const p = game.player(st.owner);
        owner = `<div class="owner"><i style="background:${p.color}"></i>Chủ: ${esc(p.name)}${tile.type === 'property' ? ` · ${LEVEL_NAMES[st.level]}` : ''}</div>`;
      }
    }
    this.tileCard.innerHTML = `
      <div class="band" style="background:${bandColor}">${esc(bandLabel)}</div>
      ${pinned ? '<button class="close" title="Đóng">✕</button>' : ''}
      <div class="body"><h2>${esc(tile.name)}</h2><p>${esc(tile.info || '')}</p>${table}${owner}</div>`;
    this.tileCard.hidden = false;
    this.tileCard.classList.toggle('pinned', this.pinnedTile === index);
    const close = this.tileCard.querySelector('.close');
    if (close) close.addEventListener('click', () => this.hideTileCard(true));
  }

  hideTileCard(force = false) {
    if (this.pinnedTile !== null && !force) return;
    this.pinnedTile = null;
    this.shownTile = null;
    this.tileCard.hidden = true;
  }

  /** Vẽ lại thẻ ô đang hiển thị để chủ sở hữu / cấp công trình luôn mới nhất. */
  refreshTileCard(game) {
    if (this.tileCard.hidden || this.shownTile === null || this.shownTile === undefined) return;
    this.showTileCard(this.shownTile, game, this.pinnedTile === this.shownTile);
  }

  // ---------- hành động ----------

  setActions(message, buttons = []) {
    this.actionMsg.innerHTML = message;
    this.actionButtons.innerHTML = '';
    this.primaryAction = null;
    for (const b of buttons) {
      const el = document.createElement('button');
      el.innerHTML = b.label;
      el.className = b.cls || '';
      el.disabled = !!b.disabled;
      if (b.title) el.title = b.title;
      el.addEventListener('click', () => {
        if (el.disabled) return;
        this.actionButtons.querySelectorAll('button').forEach((x) => { x.disabled = true; });
        b.onClick();
      });
      this.actionButtons.appendChild(el);
      if (b.primary && !b.disabled) this.primaryAction = () => el.click();
    }
  }

  // ---------- hộp thoại ----------

  openModal(html, { onOpen } = {}) {
    this.modalBox.className = 'modal-box';
    this.modalBox.innerHTML = html;
    this.modal.hidden = false;
    if (onOpen) onOpen(this.modalBox);
  }

  closeModal() {
    this.modal.hidden = true;
    this.modalBox.innerHTML = '';
  }

  /** Hiện lá thẻ Cơ hội / Khí vận. Trả về Promise khi người chơi đóng (hoặc tự đóng với máy). */
  showCard(deck, text, player, autoMs = 0) {
    return new Promise((resolve) => {
      this.modalBox.className = `modal-box game-card ${deck}`;
      this.modalBox.innerHTML = `
        <div class="head">${deck === 'chance' ? '? CƠ HỘI' : '✉ KHÍ VẬN'}</div>
        <div class="text">${esc(text)}<div class="who">Thẻ của ${esc(player.name)}</div></div>
        <div class="modal-actions"><button class="primary">OK</button></div>`;
      this.modal.hidden = false;
      let done = false;
      const finish = () => {
        if (done) return;
        done = true;
        this.closeModal();
        this.cardResolve = null;
        resolve();
      };
      this.cardResolve = finish;
      this.modalBox.querySelector('button').addEventListener('click', finish);
      if (autoMs) setTimeout(finish, autoMs);
    });
  }

  showManage(game, { onBuild, onSellBuilding, onSellLand }) {
    const p = game.current;
    const owned = game.ownedTiles(p.id).sort((a, b) => a - b);
    const items = owned.map((i) => {
      const t = TILES[i];
      const st = game.state.tiles[i];
      const color = t.group ? GROUPS[t.group].color : (t.type === 'transport' ? '#37474f' : '#00695c');
      const buttons = [];
      if (t.type === 'property') {
        const can = game.canBuildFreely(p.id, i);
        const full = game.ownsGroup(p.id, t.group);
        buttons.push(`<button class="primary" data-act="build" data-i="${i}" ${can ? '' : 'disabled'} title="${full ? '' : 'Cần sở hữu trọn vùng'}">+ Xây ${formatMoney(game.buildCost(i))}</button>`);
        if (st.level > 0) buttons.push(`<button data-act="sellb" data-i="${i}">− Bán công trình +${formatMoney(Math.floor(game.buildCost(i) / 2))}</button>`);
      }
      if (st.level === 0) buttons.push(`<button class="danger" data-act="selll" data-i="${i}">Bán đất +${formatMoney(Math.floor(t.price / 2))}</button>`);
      return `<div class="manage-item"><span class="sw" style="background:${color}"></span>
        <div class="info"><b>${esc(t.name)}</b><small>${t.type === 'property' ? LEVEL_NAMES[st.level] : (t.type === 'transport' ? 'Giao thông' : 'Năng lượng')} · thuê ${formatMoney(game.rentFor(i))}</small></div>${buttons.join('')}</div>`;
    }).join('');
    this.openModal(`
      <h2>Tài sản của ${esc(p.name)}</h2>
      <div>Tiền mặt: <b>${formatMoney(p.money)}</b> · Tổng tài sản: <b>${formatMoney(game.netWorth(p.id))}</b></div>
      <div class="manage-list">${items || '<div class="manage-empty">Chưa sở hữu ô nào. Hãy mua đất khi dừng ở ô chưa có chủ!</div>'}</div>
      <div class="modal-actions"><button class="primary" data-close>Đóng</button></div>`, {
      onOpen: (box) => {
        box.querySelector('[data-close]').addEventListener('click', () => this.closeModal());
        box.querySelectorAll('[data-act]').forEach((b) => b.addEventListener('click', () => {
          const i = Number(b.dataset.i);
          const act = b.dataset.act;
          if (act === 'build') onBuild(i);
          else if (act === 'sellb') onSellBuilding(i);
          else if (b.dataset.armed) onSellLand(i);
          else {
            // xác nhận ngay trên nút (trình duyệt nhúng có thể chặn confirm())
            b.dataset.armed = '1';
            b.textContent = 'Bấm lần nữa để bán';
          }
        }));
      },
    });
  }

  showGameOver(game, onNewGame) {
    const ranking = game.state.players
      .map((p) => ({ p, worth: p.bankrupt ? 0 : game.netWorth(p.id) }))
      .sort((a, b) => (a.p.bankrupt - b.p.bankrupt) || b.worth - a.worth);
    const winner = game.player(game.state.winner);
    const box = $('gameover-box');
    box.innerHTML = `
      <div class="trophy">🏆</div>
      <h2 style="text-align:center">${esc(winner.name)} trở thành Tỷ Phú Việt Nam!</h2>
      <ol class="ranking">${ranking.map(({ p, worth }) => `<li><i style="background:${p.color}"></i><span>${esc(p.name)}</span><span>${p.bankrupt ? 'Phá sản' : formatMoney(worth)}</span></li>`).join('')}</ol>
      <div class="modal-actions"><button class="ghost" data-view style="color:var(--ink)">Xem bàn cờ</button><button class="primary" data-new>Ván mới</button></div>`;
    $('gameover').hidden = false;
    box.querySelector('[data-new]').addEventListener('click', () => { $('gameover').hidden = true; onNewGame(); });
    box.querySelector('[data-view]').addEventListener('click', () => { $('gameover').hidden = true; });
  }
}

export { esc, MAX_LEVEL };
