// Bộ điều khiển: nối engine luật chơi (core) với cảnh 3D (render) và giao diện (ui).
import * as THREE from 'three';
import { Stage } from './render/scene.js';
import { Board3D } from './render/board3d.js';
import { Dice3D } from './render/dice3d.js';
import { Token, TOKENS } from './render/tokens.js';
import { wait, tween } from './render/helpers.js';
import { Game } from './core/game.js';
import { botDecide, botJailChoice, botNextBuild } from './core/bot.js';
import { TILES, formatMoney, JAIL_FINE, JAIL_INDEX, MAX_JAIL_TURNS } from './data/board-data.js';
import { Hud, playerTag, LEVEL_NAMES, esc } from './ui/hud.js';
import { Sound } from './ui/sound.js';

const SAVE_KEY = 'cotyphu-vn-save-v1';
const COLORS = ['#e53935', '#1e88e5', '#8e24aa', '#00897b'];
const TOKEN_KEYS = Object.keys(TOKENS);
const DEFAULT_PLAYERS = [
  { name: 'Bạn', token: 'hat', isBot: false },
  { name: 'Máy Hà Nội', token: 'lantern', isBot: true },
  { name: 'Máy Huế', token: 'drum', isBot: true },
  { name: 'Máy Sài Gòn', token: 'lotus', isBot: true },
];

const $ = (id) => document.getElementById(id);
const hud = new Hud();
const sound = new Sound();

let stage;
let board;
let dice;
let game = null;
let gameId = 0;
let tokens = [];
let busy = false;
let displayed = [];
let botSpeed = 1;
let follow = true;

// ------------------------------------------------------------------ khởi động

async function boot() {
  try {
    await Promise.race([document.fonts.load('800 40px "Be Vietnam Pro"'), wait(2500)]);
  } catch { /* dùng font dự phòng */ }
  try {
    stage = new Stage($('stage'));
  } catch (err) {
    $('loading').innerHTML = '<p>Trình duyệt của bạn chưa hỗ trợ WebGL — không thể hiển thị bàn cờ 3D.</p>';
    throw err;
  }
  board = new Board3D(stage.scene);
  dice = new Dice3D(stage.scene);
  stage.onTick((t, dt) => {
    board.tick(t, dt);
    tokens.forEach((tk) => tk.tick(t));
  });
  setupPicking();
  setupToolbar();
  setupForm();
  setupKeyboard();
  // móc gỡ lỗi cho console / kiểm thử tự động
  window.cotyphu = { stage, board, dice, get game() { return game; } };
  hud.onPlayerClick = (id) => tokens[id] && stage.follow(tokens[id].group.position);
  hud.setActions('Thiết lập người chơi để bắt đầu hành trình.', []);
  requestAnimationFrame(() => $('loading').classList.add('done'));
  setTimeout(() => $('loading').remove(), 800);
}

// ------------------------------------------------------------------ lưu / khôi phục

function save() {
  if (!game) return;
  try {
    if (game.state.phase === 'over') localStorage.removeItem(SAVE_KEY);
    else localStorage.setItem(SAVE_KEY, JSON.stringify({ snapshot: game.snapshot(), botSpeed }));
  } catch { /* chế độ riêng tư: bỏ qua */ }
}

function loadSave() {
  try {
    const raw = localStorage.getItem(SAVE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

// ------------------------------------------------------------------ màn hình thiết lập

let setupPlayers = DEFAULT_PLAYERS.slice(0, 3).map((p) => ({ ...p }));

function renderSetupRows() {
  const rows = $('player-rows');
  rows.innerHTML = setupPlayers.map((p, i) => `
    <div class="prow" data-i="${i}">
      <span class="dot" style="background:${COLORS[i]}"></span>
      <input value="${esc(p.name)}" maxlength="18" aria-label="Tên người chơi ${i + 1}" data-f="name">
      <select data-f="token" aria-label="Quân cờ">${TOKEN_KEYS.map((k) => `<option value="${k}" ${p.token === k ? 'selected' : ''}>${TOKENS[k]}</option>`).join('')}</select>
      <select data-f="isBot" aria-label="Loại"><option value="0" ${p.isBot ? '' : 'selected'}>👤 Người</option><option value="1" ${p.isBot ? 'selected' : ''}>🤖 Máy</option></select>
      <button type="button" data-remove title="Bỏ người chơi" ${setupPlayers.length <= 2 ? 'disabled' : ''}>✕</button>
    </div>`).join('');
  rows.querySelectorAll('.prow').forEach((row) => {
    const i = Number(row.dataset.i);
    row.querySelectorAll('[data-f]').forEach((input) => input.addEventListener('input', () => {
      const f = input.dataset.f;
      setupPlayers[i][f] = f === 'isBot' ? input.value === '1' : input.value;
    }));
    row.querySelector('[data-remove]').addEventListener('click', () => {
      setupPlayers.splice(i, 1);
      renderSetupRows();
    });
  });
  $('add-player').disabled = setupPlayers.length >= 4;
}

function setupForm() {
  renderSetupRows();
  $('add-player').addEventListener('click', () => {
    if (setupPlayers.length >= 4) return;
    const used = new Set(setupPlayers.map((p) => p.token));
    const def = DEFAULT_PLAYERS[setupPlayers.length];
    setupPlayers.push({ ...def, token: TOKEN_KEYS.find((k) => !used.has(k)) || def.token });
    renderSetupRows();
  });
  $('setup-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const players = setupPlayers.map((p, i) => ({
      name: (p.name || '').trim() || `Người chơi ${i + 1}`, token: p.token, isBot: p.isBot, color: COLORS[i],
    }));
    botSpeed = Number($('bot-speed').value);
    startGame(new Game({ players, maxRounds: Number($('max-rounds').value) }), true);
  });
  $('resume-btn').addEventListener('click', () => {
    const saved = loadSave();
    if (!saved) return;
    botSpeed = saved.botSpeed || 1;
    startGame(new Game({ snapshot: saved.snapshot }), false);
  });
  openSetup();
}

function openSetup() {
  const saved = loadSave();
  $('resume-btn').hidden = !saved;
  $('setup').hidden = false;
}

// ------------------------------------------------------------------ bắt đầu ván

function startGame(newGame, fresh) {
  if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
  game = newGame;
  gameId += 1;
  busy = false;
  $('setup').hidden = true;
  $('gameover').hidden = true;
  if (hud.cardResolve) hud.cardResolve();
  hud.closeModal();
  hud.hideTileCard(true);
  tokens.forEach((t) => t.dispose());
  const s = game.state;
  tokens = s.players.map((p) => new Token(stage.scene, p));
  s.players.forEach((p) => placeToken(p));
  TILES.forEach((_, i) => {
    const t = s.tiles[i];
    const owner = t.owner === null ? null : game.player(t.owner);
    board.setOwner(i, owner ? owner.color : null);
    board.setLevel(i, t.level, owner ? owner.color : 0xffffff, false);
  });
  dice.show(s.dice);
  displayed = s.players.map((p) => p.money);
  if (fresh) {
    hud.clearLog();
    hud.addLog(`Ván mới bắt đầu với ${s.players.map(playerTag).join(', ')}. Mỗi người có <b>${formatMoney(s.players[0].money)}</b>.`);
  } else {
    hud.addLog('Đã khôi phục ván đấu đã lưu.');
  }
  save();
  stage.resetView();
  setTimeout(() => step(), 400);
}

function placeToken(p) {
  const tk = tokens[p.id];
  tk.setPosition(board.tokenSlot(p.pos, p.id, p.inJail && p.pos === JAIL_INDEX));
  tk.setVisible(!p.bankrupt);
}

function renderAll() {
  hud.renderPlayers(game);
  hud.setRound(game.state.round, game.state.maxRounds);
  hud.setPot(game.state.pot);
  if (hud.pinnedTile !== null) hud.showTileCard(hud.pinnedTile, game, true);
}

// ------------------------------------------------------------------ vòng lặp lượt chơi

function step() {
  if (!game || busy) return;
  renderAll();
  const s = game.state;
  if (s.phase === 'over') {
    tokens.forEach((t) => t.setActive(false));
    hud.setActions('Ván đấu đã kết thúc.', [{ label: 'Ván mới', cls: 'primary', primary: true, onClick: openSetup }]);
    hud.showGameOver(game, openSetup);
    return;
  }
  const p = game.current;
  tokens.forEach((t, i) => t.setActive(i === p.id));
  if (follow) stage.follow(tokens[p.id].group.position);
  if (p.isBot) {
    hud.setActions(`🤖 ${playerTag(p)} đang tính nước đi…`, []);
    botTurn(gameId);
  } else {
    humanActions(p);
  }
}

function manageButton() {
  return { label: '🏠 Tài sản', cls: 'ghost', onClick: () => { openManage(); step(); } };
}

function humanActions(p) {
  const s = game.state;
  if (s.phase === 'roll') {
    if (p.inJail) {
      const buttons = [{ label: '🎲 Đổ tìm đôi', cls: 'primary big', primary: true, onClick: () => act(() => game.roll()) }];
      buttons.push({ label: `Nộp phạt ${formatMoney(JAIL_FINE)}`, disabled: p.money < JAIL_FINE, onClick: () => act(() => game.payJailFine()) });
      if (p.jailFree.length) buttons.push({ label: '🎫 Dùng thẻ ân xá', onClick: () => act(() => game.useJailCard()) });
      buttons.push(manageButton());
      hud.setActions(`🔒 ${playerTag(p)} đang ở Nhà tạm giữ (lần thử ${p.jailTurns + 1}/${MAX_JAIL_TURNS}). Đổ được đôi để ra, hoặc nộp phạt.`, buttons);
    } else {
      hud.setActions(`Đến lượt ${playerTag(p)} — hãy đổ xúc xắc!`, [
        { label: '🎲 Đổ xúc xắc', cls: 'primary big', primary: true, onClick: () => act(() => game.roll()) },
        manageButton(),
      ]);
    }
  } else if (s.phase === 'decide') {
    const pend = s.pending;
    const tile = TILES[pend.tile];
    hud.showTileCard(pend.tile, game, true);
    if (pend.kind === 'buy') {
      hud.setActions(`${playerTag(p)} dừng ở <b>${esc(tile.name)}</b>. Mua với giá <b>${formatMoney(tile.price)}</b>? (đang có ${formatMoney(p.money)})`, [
        { label: `Mua ${formatMoney(tile.price)}`, cls: 'primary big', primary: true, onClick: () => act(() => game.decide(true)) },
        { label: 'Bỏ qua', onClick: () => act(() => game.decide(false)) },
      ]);
    } else {
      const opt = pend.options[0];
      hud.setActions(`Nâng cấp <b>${esc(tile.name)}</b> lên <b>${LEVEL_NAMES[opt.level]}</b> với giá <b>${formatMoney(opt.cost)}</b>?`, [
        { label: `Xây ${formatMoney(opt.cost)}`, cls: 'primary big', primary: true, onClick: () => act(() => game.decide(opt.level)) },
        { label: 'Để sau', onClick: () => act(() => game.decide(false)) },
      ]);
    }
  } else if (s.phase === 'end') {
    const again = s.rollAgain && !p.inJail && !p.bankrupt;
    hud.setActions(again ? `🎉 Đổ đôi! ${playerTag(p)} được đổ thêm lần nữa.` : `${playerTag(p)} đã xong nước đi.`, [
      { label: again ? '🎲 Đổ tiếp' : 'Kết thúc lượt ➜', cls: 'primary big', primary: true, onClick: () => act(() => game.endTurn()) },
      manageButton(),
    ]);
  }
}

async function botTurn(id) {
  const delay = (ms) => wait(ms * botSpeed);
  const alive = () => id === gameId && game && game.current.isBot;
  while (!$('setup').hidden || !$('help').hidden) {
    await wait(300);
    if (id !== gameId) return;
  }
  await delay(650);
  if (!alive()) return;
  const s = game.state;
  if (s.phase === 'roll') {
    const choice = botJailChoice(game);
    if (choice === 'card') await act(() => game.useJailCard(), { next: false });
    else if (choice === 'fine') await act(() => game.payJailFine(), { next: false });
    if (!alive()) return;
    await act(() => game.roll());
  } else if (s.phase === 'decide') {
    await delay(500);
    if (!alive()) return;
    await act(() => game.decide(botDecide(game)));
  } else if (s.phase === 'end') {
    let b;
    while (alive() && (b = botNextBuild(game)) !== null) {
      await act(() => game.buildOn(b), { next: false });
      await delay(300);
    }
    if (!alive()) return;
    await act(() => game.endTurn());
  }
}

/** Thực hiện một hành động của engine rồi diễn hoạt các sự kiện trả về. */
async function act(fn, { next = true } = {}) {
  if (busy || !game) return;
  busy = true;
  const id = gameId;
  const actor = game.current;
  if (!actor.isBot) hud.setActions(`⏳ ${playerTag(actor)} đang thực hiện nước đi…`, []);
  displayed = game.state.players.map((p) => p.money);
  let events;
  try {
    events = fn();
  } catch (err) {
    console.warn(err);
    hud.toast(err.message);
    busy = false;
    if (next) step();
    return;
  }
  save(); // trạng thái engine đã chốt — lưu ngay, không chờ diễn hoạt
  try {
    for (const e of events) {
      if (id !== gameId) return;
      await playEvent(e);
    }
  } finally {
    if (id === gameId) {
      busy = false;
      save();
      if (next) step();
      else renderAll();
    }
  }
}

// ------------------------------------------------------------------ diễn hoạt sự kiện

function moneyFloat(playerId, amount) {
  const pos = tokens[playerId].group.position.clone().add(new THREE.Vector3(0, 1.8, 0));
  hud.floatText(stage.project(pos), `${amount > 0 ? '+' : '−'}${formatMoney(Math.abs(amount))}`, amount > 0);
}

async function playEvent(e) {
  const p = e.player !== undefined ? game.player(e.player) : null;
  const tag = p ? playerTag(p) : '';
  const tk = p ? tokens[p.id] : null;
  const tileName = e.tile !== undefined ? `<b>${esc(TILES[e.tile].name)}</b>` : '';
  switch (e.type) {
    case 'dice': {
      sound.play('dice');
      await dice.roll(e.dice);
      const [a, b] = e.dice;
      hud.addLog(`${tag} đổ được ${a} + ${b} = <b>${a + b}</b>${a === b ? ' (đôi!)' : ''}`);
      await wait(150);
      break;
    }
    case 'move': {
      const points = e.path.map((i) => board.tokenSlot(i, e.player));
      const stepMs = e.path.length > 12 ? 130 : 200;
      await tk.hopThrough(points, {
        stepMs,
        onStep: (k) => {
          sound.play('move', 0.2);
          if (follow) stage.follow(points[k]);
        },
      });
      hud.showTileCard(e.to, game);
      break;
    }
    case 'money':
      displayed[e.player] += e.amount;
      hud.setMoney(e.player, displayed[e.player]);
      if (e.amount !== 0) {
        moneyFloat(e.player, e.amount);
        if (e.amount > 0) sound.play('cash', 0.5);
        hud.addLog(`${tag} ${e.amount > 0 ? '+' : '−'}${formatMoney(Math.abs(e.amount))} · ${esc(e.reason)}`, e.amount > 0 ? 'good' : 'bad');
      }
      await wait(260);
      break;
    case 'pot':
      hud.setPot(e.pot);
      break;
    case 'buy':
      board.setOwner(e.tile, p.color);
      sound.play('cash');
      hud.addLog(`${tag} đã mua ${tileName} 🎉`);
      hud.toast(`${p.name} sở hữu ${TILES[e.tile].name}`);
      await wait(350);
      break;
    case 'build':
      board.setLevel(e.tile, e.level, p.color);
      sound.play('build');
      hud.addLog(`${tag} xây <b>${LEVEL_NAMES[e.level]}</b> tại ${tileName}`);
      await wait(550);
      break;
    case 'sellBuilding':
      board.setLevel(e.tile, e.level, p.color, false);
      hud.addLog(`${tag} bán bớt công trình ở ${tileName}`, 'bad');
      await wait(200);
      break;
    case 'sellProperty':
      board.setOwner(e.tile, null);
      board.setLevel(e.tile, 0, null, false);
      hud.addLog(`${tag}: ${tileName} trở về ngân hàng`, 'bad');
      await wait(150);
      break;
    case 'rent': {
      const owner = game.player(e.owner);
      hud.addLog(`${tag} trả <b>${formatMoney(e.amount)}</b> tiền thuê ${tileName} cho ${playerTag(owner)}`, 'bad');
      hud.toast(`Trả ${formatMoney(e.amount)} cho ${owner.name}`);
      break;
    }
    case 'card':
      await board.drawCard(e.deck, stage.camera);
      hud.addLog(`${tag} rút thẻ ${e.deck === 'chance' ? 'Cơ hội' : 'Khí vận'}: “${esc(e.text)}”`);
      await hud.showCard(e.deck, e.text, p, p.isBot ? 2600 * botSpeed : 0);
      break;
    case 'jail':
      hud.toast('🚓 Vào Nhà tạm giữ!');
      await tk.flyTo(board.tokenSlot(JAIL_INDEX, e.player, true));
      hud.addLog(`${tag} bị đưa vào <b>Nhà tạm giữ</b>`, 'bad');
      break;
    case 'jailRelease': {
      const why = { doubles: 'nhờ đổ được đôi', fine: 'sau khi nộp phạt', card: 'bằng thẻ ân xá' }[e.reason];
      hud.addLog(`${tag} ra khỏi Nhà tạm giữ ${why}`, 'good');
      await tk.slideTo(board.tokenSlot(JAIL_INDEX, e.player, false));
      break;
    }
    case 'jailStay':
      hud.addLog(`${tag} chưa đổ được đôi — vẫn ở Nhà tạm giữ (còn ${e.turnsLeft} lần thử)`);
      break;
    case 'jailForcedFine':
      hud.addLog(`${tag} hết lượt thử, buộc nộp phạt ${formatMoney(e.amount)}`, 'bad');
      break;
    case 'threeDoubles':
      hud.toast('Đổ đôi 3 lần liền — vào tù!');
      hud.addLog(`${tag} đổ đôi 3 lần liên tiếp!`, 'bad');
      break;
    case 'cantAfford':
      hud.addLog(`${tag} không đủ tiền mua ${tileName} (${formatMoney(e.price)})`);
      break;
    case 'decline':
      hud.addLog(`${tag} ${e.kind === 'buy' ? 'không mua' : 'chưa xây ở'} ${tileName}`);
      break;
    case 'jailFreeCard':
      hud.addLog(`${tag} giữ một thẻ ân xá 🎫`, 'good');
      break;
    case 'bankrupt':
      hud.toast(`💸 ${p.name} phá sản!`, 2500);
      hud.addLog(`${tag} đã <b>phá sản</b> và rời cuộc chơi`, 'bad');
      await tween(600, (k) => tk.group.scale.setScalar(1.35 * (1 - k) + 0.001));
      tk.setVisible(false);
      break;
    case 'turn':
      hud.toast(`Lượt của ${p.name}`);
      hud.hideTileCard(true);
      break;
    case 'rollAgain':
      hud.toast('🎉 Đổ đôi — được đổ tiếp!');
      break;
    case 'gameOver':
      hud.addLog(`🏆 ${playerTag(game.player(e.winner))} chiến thắng${e.reason === 'rounds' ? ' khi hết số vòng giới hạn' : ''}!`, 'good');
      break;
    default:
      break;
  }
}

// ------------------------------------------------------------------ quản lý tài sản

function openManage() {
  if (!game || busy) return;
  const p = game.current;
  if (p.isBot || !game.canManage(p.id)) {
    hud.toast('Chỉ quản lý tài sản trong lượt của bạn');
    return;
  }
  const refresh = () => { if (!hud.modal.hidden) hud.showManage(game, handlers); };
  const handlers = {
    onBuild: (i) => act(() => game.buildOn(i)).then(refresh),
    onSellBuilding: (i) => act(() => game.sellBuilding(i)).then(refresh),
    onSellLand: (i) => {
      if (!window.confirm(`Bán ${TILES[i].name} cho ngân hàng với giá ${formatMoney(Math.floor(TILES[i].price / 2))}?`)) return;
      act(() => game.sellProperty(i)).then(refresh);
    },
  };
  hud.showManage(game, handlers);
}

// ------------------------------------------------------------------ tương tác chuột / phím

function setupPicking() {
  const canvas = stage.renderer.domElement;
  const ray = new THREE.Raycaster();
  const ndc = new THREE.Vector2();
  let down = null;
  const pick = (ev) => {
    const r = canvas.getBoundingClientRect();
    ndc.set(((ev.clientX - r.left) / r.width) * 2 - 1, -((ev.clientY - r.top) / r.height) * 2 + 1);
    ray.setFromCamera(ndc, stage.camera);
    const hit = ray.intersectObjects(board.pickables, false)[0];
    return hit ? hit.object.userData.tileIndex : null;
  };
  canvas.addEventListener('pointermove', (ev) => {
    if (ev.pointerType !== 'mouse' || ev.buttons) return;
    const i = pick(ev);
    board.setHover(i);
    canvas.style.cursor = i === null ? 'grab' : 'pointer';
    if (!game) return;
    if (i === null) hud.hideTileCard();
    else hud.showTileCard(i, game);
  });
  canvas.addEventListener('pointerdown', (ev) => { down = { x: ev.clientX, y: ev.clientY }; });
  canvas.addEventListener('pointerup', (ev) => {
    if (!down || Math.hypot(ev.clientX - down.x, ev.clientY - down.y) > 6 || !game) return;
    const i = pick(ev);
    if (i === null) hud.hideTileCard(true);
    else hud.showTileCard(i, game, true);
  });
}

function setupToolbar() {
  const followBtn = $('btn-follow');
  followBtn.addEventListener('click', () => {
    follow = !follow;
    followBtn.classList.toggle('on', follow);
    if (follow && game) stage.follow(tokens[game.current.id].group.position);
    else stage.follow(null);
  });
  $('btn-view').addEventListener('click', () => {
    follow = false;
    followBtn.classList.remove('on');
    stage.resetView();
  });
  const soundBtn = $('btn-sound');
  const syncSound = () => { soundBtn.textContent = sound.muted ? '🔇' : '🔊'; };
  syncSound();
  soundBtn.addEventListener('click', () => { sound.toggle(); syncSound(); });
  $('btn-help').addEventListener('click', () => { $('help').hidden = false; });
  $('help').querySelector('[data-close]').addEventListener('click', () => { $('help').hidden = true; });
  $('btn-menu').addEventListener('click', openSetup);
  $('setup').addEventListener('click', (ev) => {
    if (ev.target === $('setup') && game) $('setup').hidden = true;
  });
}

function setupKeyboard() {
  window.addEventListener('keydown', (ev) => {
    if (ev.target.closest && ev.target.closest('input, select, textarea')) return;
    if (ev.key === 'Escape') {
      if (!$('help').hidden) $('help').hidden = true;
      else if (!hud.modal.hidden && !hud.cardResolve) hud.closeModal();
      else if (!$('setup').hidden && game) $('setup').hidden = true;
      return;
    }
    if (ev.code !== 'Space' && ev.key !== 'Enter') return;
    if (!$('setup').hidden || !$('help').hidden || !$('gameover').hidden) return;
    ev.preventDefault();
    // tránh để nút đang được focus (vd. nút cũ) tự kích hoạt khi nhả phím
    if (ev.target.blur && ev.target !== document.body) ev.target.blur();
    if (hud.cardResolve) hud.cardResolve();
    else if (hud.modal.hidden && hud.primaryAction) hud.primaryAction();
  });
}

boot();
