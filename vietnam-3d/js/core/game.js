// Engine luật chơi Cờ Tỷ Phú Việt Nam — không phụ thuộc DOM/Three.js.
// Mọi hành động trả về một mảng "events" để tầng hiển thị diễn hoạt lần lượt.
// Trạng thái là JSON thuần nên lưu/khôi phục được qua localStorage.

import {
  TILES, GROUPS, BOARD_SIZE, START_MONEY, START_SALARY, JAIL_FINE,
  JAIL_INDEX, MAX_JAIL_TURNS, MAX_LEVEL, groupTiles,
} from '../data/board-data.js';
import { CHANCE_CARDS, COMMUNITY_CARDS } from '../data/cards.js';

const DECKS = { chance: CHANCE_CARDS, community: COMMUNITY_CARDS };

function shuffled(n, rng) {
  const a = Array.from({ length: n }, (_, i) => i);
  for (let i = n - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

export class Game {
  /**
   * @param {object} opts
   * @param {Array<{name:string,color:string,token:string,isBot?:boolean}>} [opts.players]
   * @param {number} [opts.maxRounds] 0 = chơi đến khi chỉ còn một người.
   * @param {() => number} [opts.rng]
   * @param {object} [opts.snapshot] trạng thái đã lưu để khôi phục.
   */
  constructor({ players, maxRounds = 0, rng = Math.random, snapshot = null } = {}) {
    this.rng = rng;
    if (snapshot) {
      this.state = JSON.parse(JSON.stringify(snapshot));
      return;
    }
    if (!players || players.length < 2 || players.length > 4) {
      throw new Error('Cần từ 2 đến 4 người chơi');
    }
    this.state = {
      players: players.map((p, id) => ({
        id,
        name: p.name,
        color: p.color,
        token: p.token,
        isBot: !!p.isBot,
        money: START_MONEY,
        pos: 0,
        inJail: false,
        jailTurns: 0,
        jailFree: [],
        bankrupt: false,
      })),
      tiles: TILES.map(() => ({ owner: null, level: 0 })),
      current: 0,
      phase: 'roll', // roll | decide | end | over
      pending: null,
      doubles: 0,
      rollAgain: false,
      pot: 0,
      round: 1,
      maxRounds,
      dice: [1, 1],
      decks: {
        chance: { order: shuffled(CHANCE_CARDS.length, rng), next: 0 },
        community: { order: shuffled(COMMUNITY_CARDS.length, rng), next: 0 },
      },
      winner: null,
    };
  }

  snapshot() {
    return JSON.parse(JSON.stringify(this.state));
  }

  // ---------- truy vấn ----------

  get current() {
    return this.state.players[this.state.current];
  }

  player(id) {
    return this.state.players[id];
  }

  activePlayers() {
    return this.state.players.filter((p) => !p.bankrupt);
  }

  ownsGroup(playerId, group) {
    return groupTiles(group).every((i) => this.state.tiles[i].owner === playerId);
  }

  countOwned(playerId, type) {
    return TILES.filter((t, i) => t.type === type && this.state.tiles[i].owner === playerId).length;
  }

  rentFor(index, diceTotal = this.state.dice[0] + this.state.dice[1], multiplier = 1) {
    const tile = TILES[index];
    const { owner, level } = this.state.tiles[index];
    if (owner === null) return 0;
    let rent = 0;
    if (tile.type === 'property') {
      rent = tile.rent[level];
      if (level === 0 && this.ownsGroup(owner, tile.group)) rent *= 2;
    } else if (tile.type === 'transport') {
      rent = 25 * 2 ** (this.countOwned(owner, 'transport') - 1);
    } else if (tile.type === 'utility') {
      rent = diceTotal * (this.countOwned(owner, 'utility') === 2 ? 10 : 4);
    }
    return rent * multiplier;
  }

  buildCost(index) {
    return GROUPS[TILES[index].group].buildCost;
  }

  netWorth(playerId) {
    const p = this.player(playerId);
    let total = p.money;
    this.state.tiles.forEach((t, i) => {
      if (t.owner !== playerId) return;
      total += TILES[i].price;
      if (t.level > 0) total += t.level * this.buildCost(i);
    });
    return total;
  }

  ownedTiles(playerId) {
    return this.state.tiles
      .map((t, i) => (t.owner === playerId ? i : -1))
      .filter((i) => i >= 0);
  }

  /** Ô có thể xây thêm qua bảng quản lý (cần sở hữu trọn vùng). */
  canBuildFreely(playerId, index) {
    const tile = TILES[index];
    const t = this.state.tiles[index];
    return tile.type === 'property' && t.owner === playerId && t.level < MAX_LEVEL &&
      this.ownsGroup(playerId, tile.group) && this.player(playerId).money >= this.buildCost(index);
  }

  /** Hành động quản lý tài sản chỉ được phép trong lượt của mình, khi không chờ quyết định. */
  canManage(playerId) {
    const s = this.state;
    return s.current === playerId && (s.phase === 'roll' || s.phase === 'end');
  }

  // ---------- hành động của người chơi ----------

  roll(forced = null) {
    const s = this.state;
    if (s.phase !== 'roll') throw new Error(`Không thể đổ xúc xắc ở pha "${s.phase}"`);
    const p = this.current;
    const d1 = forced ? forced[0] : 1 + Math.floor(this.rng() * 6);
    const d2 = forced ? forced[1] : 1 + Math.floor(this.rng() * 6);
    const isDouble = d1 === d2;
    const total = d1 + d2;
    s.dice = [d1, d2];
    const events = [{ type: 'dice', player: p.id, dice: [d1, d2] }];

    if (p.inJail) {
      s.rollAgain = false;
      if (isDouble) {
        p.inJail = false;
        p.jailTurns = 0;
        events.push({ type: 'jailRelease', player: p.id, reason: 'doubles' });
      } else {
        p.jailTurns += 1;
        if (p.jailTurns < MAX_JAIL_TURNS) {
          events.push({ type: 'jailStay', player: p.id, turnsLeft: MAX_JAIL_TURNS - p.jailTurns });
          s.phase = 'end';
          return events;
        }
        events.push({ type: 'jailForcedFine', player: p.id, amount: JAIL_FINE });
        this._pay(p, JAIL_FINE, 'pot', events, 'Nộp phạt ra tù');
        if (p.bankrupt) return this._finishAction(events);
        p.inJail = false;
        p.jailTurns = 0;
        events.push({ type: 'jailRelease', player: p.id, reason: 'fine' });
      }
    } else if (isDouble) {
      s.doubles += 1;
      if (s.doubles >= 3) {
        events.push({ type: 'threeDoubles', player: p.id });
        this._sendToJail(p, events);
        s.rollAgain = false;
        return this._finishAction(events);
      }
      s.rollAgain = true;
    } else {
      s.rollAgain = false;
    }

    this._moveBy(p, total, events);
    this._resolveLanding(p, events, { diceTotal: total });
    return this._finishAction(events);
  }

  payJailFine() {
    const p = this.current;
    if (this.state.phase !== 'roll' || !p.inJail) throw new Error('Không ở trong tù');
    if (p.money < JAIL_FINE) throw new Error('Không đủ tiền nộp phạt');
    const events = [];
    this._pay(p, JAIL_FINE, 'pot', events, 'Nộp phạt ra tù');
    p.inJail = false;
    p.jailTurns = 0;
    events.push({ type: 'jailRelease', player: p.id, reason: 'fine' });
    return events;
  }

  useJailCard() {
    const p = this.current;
    if (this.state.phase !== 'roll' || !p.inJail || p.jailFree.length === 0) {
      throw new Error('Không thể dùng thẻ ân xá');
    }
    p.jailFree.shift();
    p.inJail = false;
    p.jailTurns = 0;
    return [{ type: 'jailRelease', player: p.id, reason: 'card' }];
  }

  /**
   * Trả lời quyết định đang chờ.
   * - buy: true/false
   * - build: cấp mục tiêu (số) hoặc false
   */
  decide(choice) {
    const s = this.state;
    if (s.phase !== 'decide' || !s.pending) throw new Error('Không có quyết định nào đang chờ');
    const p = this.current;
    const pending = s.pending;
    const events = [];
    s.pending = null;

    if (pending.kind === 'buy' && choice) {
      const tile = TILES[pending.tile];
      if (p.money >= tile.price) {
        p.money -= tile.price;
        s.tiles[pending.tile].owner = p.id;
        events.push({ type: 'money', player: p.id, amount: -tile.price, reason: `Mua ${tile.name}` });
        events.push({ type: 'buy', player: p.id, tile: pending.tile });
      }
    } else if (pending.kind === 'build' && choice) {
      const opt = pending.options.find((o) => o.level === choice);
      if (opt && p.money >= opt.cost) {
        this._build(p, pending.tile, opt.level, opt.cost, events);
      }
    }
    if (events.length === 0) events.push({ type: 'decline', player: p.id, kind: pending.kind, tile: pending.tile });
    s.phase = 'end';
    return events;
  }

  /** Xây thêm 1 cấp khi sở hữu trọn vùng (bảng quản lý tài sản). */
  buildOn(index) {
    const p = this.current;
    if (!this.canManage(p.id) || !this.canBuildFreely(p.id, index)) throw new Error('Không thể xây ở đây');
    const events = [];
    const level = this.state.tiles[index].level + 1;
    this._build(p, index, level, this.buildCost(index), events);
    return events;
  }

  /** Bán bớt 1 cấp công trình, nhận lại 50% chi phí xây. */
  sellBuilding(index) {
    const p = this.current;
    const t = this.state.tiles[index];
    if (!this.canManage(p.id) || t.owner !== p.id || t.level === 0) throw new Error('Không có công trình để bán');
    const events = [];
    this._sellOneLevel(p, index, events);
    return events;
  }

  /** Bán đất (chưa có công trình) cho ngân hàng, nhận lại 50% giá. */
  sellProperty(index) {
    const p = this.current;
    const t = this.state.tiles[index];
    if (!this.canManage(p.id) || t.owner !== p.id || t.level > 0) throw new Error('Không thể bán ô đất này');
    const events = [];
    this._sellLand(p, index, events);
    return events;
  }

  endTurn() {
    const s = this.state;
    if (s.phase !== 'end') throw new Error(`Không thể kết thúc lượt ở pha "${s.phase}"`);
    const events = [];
    const p = this.current;
    if (s.rollAgain && !p.bankrupt && !p.inJail) {
      s.rollAgain = false;
      s.phase = 'roll';
      events.push({ type: 'rollAgain', player: p.id });
      return events;
    }
    s.doubles = 0;
    s.rollAgain = false;
    let next = s.current;
    do {
      next = (next + 1) % s.players.length;
      if (next === 0) s.round += 1;
    } while (s.players[next].bankrupt);
    if (s.maxRounds > 0 && s.round > s.maxRounds) {
      this._endGameByWorth(events);
      return events;
    }
    s.current = next;
    s.phase = 'roll';
    events.push({ type: 'turn', player: next, round: s.round });
    return events;
  }

  // ---------- nội bộ ----------

  _finishAction(events) {
    const s = this.state;
    if (s.phase === 'over') return events;
    if (this.current.bankrupt) {
      s.pending = null;
      s.rollAgain = false;
      s.phase = 'end';
    } else {
      s.phase = s.pending ? 'decide' : 'end';
    }
    return events;
  }

  _moveBy(p, steps, events, { collectGo = true } = {}) {
    const from = p.pos;
    const dir = Math.sign(steps);
    const path = [];
    let pos = from;
    let passedGo = false;
    for (let k = 0; k < Math.abs(steps); k++) {
      pos = (pos + dir + BOARD_SIZE) % BOARD_SIZE;
      path.push(pos);
      if (dir > 0 && pos === 0) passedGo = true;
    }
    p.pos = pos;
    events.push({ type: 'move', player: p.id, from, to: pos, path });
    if (passedGo && collectGo) {
      p.money += START_SALARY;
      events.push({ type: 'money', player: p.id, amount: START_SALARY, reason: 'Qua ô Xuất phát' });
    }
  }

  _moveTo(p, target, events) {
    const steps = (target - p.pos + BOARD_SIZE) % BOARD_SIZE;
    this._moveBy(p, steps, events);
  }

  _sendToJail(p, events) {
    p.inJail = true;
    p.jailTurns = 0;
    this.state.doubles = 0;
    this.state.rollAgain = false;
    const from = p.pos;
    p.pos = JAIL_INDEX;
    events.push({ type: 'jail', player: p.id, from, to: JAIL_INDEX });
  }

  _resolveLanding(p, events, ctx) {
    const s = this.state;
    const index = p.pos;
    const tile = TILES[index];
    const ownable = tile.type === 'property' || tile.type === 'transport' || tile.type === 'utility';

    if (ownable) {
      const t = s.tiles[index];
      if (t.owner === null) {
        if (p.money >= tile.price) {
          s.pending = { kind: 'buy', tile: index, price: tile.price };
        } else {
          events.push({ type: 'cantAfford', player: p.id, tile: index, price: tile.price });
        }
      } else if (t.owner === p.id) {
        if (tile.type === 'property' && t.level < MAX_LEVEL) {
          const cost = this.buildCost(index);
          if (p.money >= cost) {
            s.pending = { kind: 'build', tile: index, options: [{ level: t.level + 1, cost }] };
          }
        }
      } else {
        const owner = this.player(t.owner);
        const rent = this.rentFor(index, ctx.diceTotal, ctx.rentMultiplier || 1);
        events.push({ type: 'rent', player: p.id, owner: owner.id, tile: index, amount: rent });
        this._pay(p, rent, owner.id, events, `Thuê ${tile.name}`);
      }
      return;
    }

    switch (tile.type) {
      case 'tax':
        this._pay(p, tile.amount, 'pot', events, tile.name);
        break;
      case 'chance':
      case 'community':
        this._drawCard(p, tile.type, events, ctx);
        break;
      case 'gotojail':
        this._sendToJail(p, events);
        break;
      case 'parking':
        if (s.pot > 0) {
          const amount = s.pot;
          s.pot = 0;
          p.money += amount;
          events.push({ type: 'money', player: p.id, amount, reason: 'Nhận Quỹ chung' });
          events.push({ type: 'pot', pot: 0 });
        }
        break;
      default:
        break;
    }
  }

  _drawCard(p, deckName, events, ctx) {
    const s = this.state;
    const deck = s.decks[deckName];
    const cards = DECKS[deckName];
    let cardIndex;
    // Bỏ qua thẻ ân xá đang nằm trong tay người chơi khác.
    for (let tries = 0; tries < cards.length; tries++) {
      cardIndex = deck.order[deck.next];
      deck.next = (deck.next + 1) % deck.order.length;
      if (deck.next === 0) deck.order = shuffled(cards.length, this.rng);
      const held = s.players.some((q) => q.jailFree.some((c) => c.deck === deckName && c.card === cardIndex));
      if (!held) break;
    }
    const card = cards[cardIndex];
    events.push({ type: 'card', player: p.id, deck: deckName, text: card.text });
    const a = card.action;
    switch (a.kind) {
      case 'money':
        if (a.amount >= 0) {
          p.money += a.amount;
          events.push({ type: 'money', player: p.id, amount: a.amount, reason: 'Thẻ ' + (deckName === 'chance' ? 'Cơ hội' : 'Khí vận') });
        } else {
          this._pay(p, -a.amount, 'pot', events, 'Thẻ ' + (deckName === 'chance' ? 'Cơ hội' : 'Khí vận'));
        }
        break;
      case 'moveTo':
        this._moveTo(p, a.target, events);
        this._resolveLanding(p, events, ctx);
        break;
      case 'moveBy':
        this._moveBy(p, a.steps, events);
        this._resolveLanding(p, events, ctx);
        break;
      case 'nearest': {
        let target = p.pos;
        do { target = (target + 1) % BOARD_SIZE; } while (TILES[target].type !== a.type);
        this._moveTo(p, target, events);
        this._resolveLanding(p, events, { ...ctx, rentMultiplier: 2 });
        break;
      }
      case 'jail':
        this._sendToJail(p, events);
        break;
      case 'jailFree':
        p.jailFree.push({ deck: deckName, card: cardIndex });
        events.push({ type: 'jailFreeCard', player: p.id });
        break;
      case 'repairs': {
        let cost = 0;
        s.tiles.forEach((t) => {
          if (t.owner !== p.id || t.level === 0) return;
          cost += t.level === MAX_LEVEL ? a.hotel : t.level * a.house;
        });
        if (cost > 0) this._pay(p, cost, 'pot', events, 'Sửa chữa công trình');
        break;
      }
      case 'payEach':
        for (const q of this.activePlayers()) {
          if (q.id === p.id || p.bankrupt) continue;
          this._pay(p, a.amount, q.id, events, `Bao ${q.name}`);
        }
        break;
      case 'collectEach':
        for (const q of this.activePlayers()) {
          if (q.id === p.id) continue;
          this._pay(q, a.amount, p.id, events, `Tặng ${p.name}`);
        }
        break;
      default:
        break;
    }
  }

  _build(p, index, level, cost, events) {
    p.money -= cost;
    this.state.tiles[index].level = level;
    events.push({ type: 'money', player: p.id, amount: -cost, reason: `Xây dựng tại ${TILES[index].name}` });
    events.push({ type: 'build', player: p.id, tile: index, level });
  }

  _sellOneLevel(p, index, events) {
    const t = this.state.tiles[index];
    const refund = Math.floor(this.buildCost(index) / 2);
    t.level -= 1;
    p.money += refund;
    events.push({ type: 'sellBuilding', player: p.id, tile: index, level: t.level });
    events.push({ type: 'money', player: p.id, amount: refund, reason: `Bán công trình ${TILES[index].name}` });
  }

  _sellLand(p, index, events) {
    const t = this.state.tiles[index];
    const refund = Math.floor(TILES[index].price / 2);
    t.owner = null;
    t.level = 0;
    p.money += refund;
    events.push({ type: 'sellProperty', player: p.id, tile: index });
    events.push({ type: 'money', player: p.id, amount: refund, reason: `Bán đất ${TILES[index].name}` });
  }

  /** Tự động thanh lý tài sản để trả nợ: bán công trình trước, rồi bán đất rẻ trước. */
  _liquidate(p, needed, events) {
    const owned = () => this.ownedTiles(p.id);
    while (p.money < needed) {
      const built = owned().filter((i) => this.state.tiles[i].level > 0)
        .sort((a, b) => this.state.tiles[b].level - this.state.tiles[a].level);
      if (built.length === 0) break;
      this._sellOneLevel(p, built[0], events);
    }
    while (p.money < needed) {
      const lands = owned().sort((a, b) => TILES[a].price - TILES[b].price);
      if (lands.length === 0) break;
      this._sellLand(p, lands[0], events);
    }
  }

  _pay(p, amount, to, events, reason) {
    if (amount <= 0 || p.bankrupt) return;
    if (p.money < amount) this._liquidate(p, amount, events);
    const paid = Math.min(amount, p.money);
    p.money -= paid;
    events.push({ type: 'money', player: p.id, amount: -paid, reason });
    if (to === 'pot') {
      this.state.pot += paid;
      events.push({ type: 'pot', pot: this.state.pot });
    } else if (to !== null && to !== undefined) {
      const q = this.player(to);
      q.money += paid;
      events.push({ type: 'money', player: q.id, amount: paid, reason });
    }
    if (paid < amount) this._bankrupt(p, events);
  }

  _bankrupt(p, events) {
    p.bankrupt = true;
    p.money = 0;
    p.jailFree = [];
    p.inJail = false;
    this.state.tiles.forEach((t, i) => {
      if (t.owner === p.id) {
        t.owner = null;
        t.level = 0;
        events.push({ type: 'sellProperty', player: p.id, tile: i });
      }
    });
    events.push({ type: 'bankrupt', player: p.id });
    const alive = this.activePlayers();
    if (alive.length === 1) {
      this.state.phase = 'over';
      this.state.pending = null;
      this.state.winner = alive[0].id;
      events.push({ type: 'gameOver', winner: alive[0].id, reason: 'last' });
    }
  }

  _endGameByWorth(events) {
    const ranking = this.activePlayers()
      .map((p) => ({ id: p.id, worth: this.netWorth(p.id) }))
      .sort((a, b) => b.worth - a.worth);
    this.state.phase = 'over';
    this.state.winner = ranking[0].id;
    events.push({ type: 'gameOver', winner: ranking[0].id, reason: 'rounds', ranking });
  }
}
