import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Game } from '../js/core/game.js';
import { botDecide, botJailChoice, botNextBuild } from '../js/core/bot.js';
import { TILES, START_MONEY, BOARD_SIZE } from '../js/data/board-data.js';

const PLAYERS = [
  { name: 'An', color: '#e53935', token: 'hat' },
  { name: 'Bình', color: '#1e88e5', token: 'lantern' },
];

function seeded(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

function newGame(players = PLAYERS, opts = {}) {
  return new Game({ players, rng: seeded(42), ...opts });
}

test('bàn cờ có 40 ô với 22 ô đất, 4 giao thông, 2 tiện ích', () => {
  assert.equal(BOARD_SIZE, 40);
  assert.equal(TILES.filter((t) => t.type === 'property').length, 22);
  assert.equal(TILES.filter((t) => t.type === 'transport').length, 4);
  assert.equal(TILES.filter((t) => t.type === 'utility').length, 2);
  assert.equal(TILES[0].type, 'go');
  assert.equal(TILES[10].type, 'jail');
  assert.equal(TILES[20].type, 'parking');
  assert.equal(TILES[30].type, 'gotojail');
});

test('mua đất và trả tiền thuê', () => {
  const g = newGame();
  g.roll([1, 2]); // An tới ô 3 Thác Dray Nur
  assert.equal(g.state.phase, 'decide');
  assert.equal(g.state.pending.kind, 'buy');
  g.decide(true);
  assert.equal(g.player(0).money, START_MONEY - 60);
  assert.equal(g.state.tiles[3].owner, 0);
  g.endTurn();

  g.roll([2, 1]); // Bình cũng tới ô 3
  const rent = TILES[3].rent[0];
  assert.equal(g.player(1).money, START_MONEY - rent);
  assert.equal(g.player(0).money, START_MONEY - 60 + rent);
});

test('sở hữu trọn vùng nhân đôi tiền thuê đất trống và cho phép xây tự do', () => {
  const g = newGame();
  g.state.tiles[1].owner = 0;
  g.state.tiles[3].owner = 0;
  assert.equal(g.rentFor(3), TILES[3].rent[0] * 2);
  assert.ok(g.canBuildFreely(0, 1));
  g.buildOn(1);
  assert.equal(g.state.tiles[1].level, 1);
  assert.equal(g.player(0).money, START_MONEY - 50);
  assert.equal(g.rentFor(1), TILES[1].rent[1]);
});

test('dừng trên đất của mình được nâng 1 cấp', () => {
  const g = newGame();
  g.state.tiles[3].owner = 0;
  g.roll([1, 2]);
  assert.equal(g.state.pending.kind, 'build');
  assert.deepEqual(g.state.pending.options, [{ level: 1, cost: 50 }]);
  g.decide(1);
  assert.equal(g.state.tiles[3].level, 1);
});

test('đi qua Xuất phát nhận lương', () => {
  const g = newGame();
  g.player(0).pos = 35;
  const ev = g.roll([2, 4]); // 35 -> 1, đi ngang qua ô 0
  assert.equal(g.player(0).pos, 1);
  assert.ok(ev.some((e) => e.type === 'money' && e.reason === 'Qua ô Xuất phát' && e.amount === 200));
  assert.equal(g.player(0).money, START_MONEY + 200);
});

test('thẻ lùi bước không nhận lương, thẻ tiến tới ô có chủ thì trả tiền thuê', () => {
  const g = newGame();
  g.player(0).pos = 1;
  const back = [];
  g._moveBy(g.player(0), -3, back);
  assert.equal(g.player(0).pos, 38);
  assert.ok(!back.some((e) => e.type === 'money'));
  g.state.tiles[39].owner = 1;
  const ev = [];
  g._moveTo(g.player(0), 39, ev);
  g._resolveLanding(g.player(0), ev, { diceTotal: 7 });
  assert.ok(ev.some((e) => e.type === 'rent' && e.amount === TILES[39].rent[0]));
});

test('ô Vào tù đưa người chơi vào Nhà tạm giữ, ra tù sau 3 lượt bằng nộp phạt', () => {
  const g = newGame();
  g.player(0).pos = 25;
  g.roll([2, 3]); // -> 30
  assert.equal(g.player(0).pos, 10);
  assert.equal(g.player(0).inJail, true);
  g.endTurn();
  g.roll([1, 2]); g.state.phase === 'decide' && g.decide(false); g.endTurn();
  g.roll([1, 2]); assert.equal(g.player(0).inJail, true); g.endTurn();
  g.roll([1, 2]); g.state.phase === 'decide' && g.decide(false); g.endTurn();
  g.roll([1, 2]); assert.equal(g.player(0).inJail, true); g.endTurn();
  g.roll([1, 2]); g.state.phase === 'decide' && g.decide(false); g.endTurn();
  const ev = g.roll([1, 2]); // lượt thứ 3: buộc nộp phạt rồi đi
  assert.ok(ev.some((e) => e.type === 'jailForcedFine'));
  assert.equal(g.player(0).inJail, false);
  assert.equal(g.player(0).pos, 13);
});

test('đổ đôi 3 lần liên tiếp thì vào tù', () => {
  const g = newGame();
  g.roll([1, 1]); if (g.state.phase === 'decide') g.decide(false); g.endTurn();
  assert.equal(g.state.current, 0, 'đổ đôi được đi tiếp');
  g.roll([2, 2]); if (g.state.phase === 'decide') g.decide(false); g.endTurn();
  const ev = g.roll([3, 3]);
  assert.ok(ev.some((e) => e.type === 'threeDoubles'));
  assert.equal(g.player(0).inJail, true);
  g.endTurn();
  assert.equal(g.state.current, 1);
});

test('thuế vào Quỹ chung, Bến Ninh Kiều nhận Quỹ', () => {
  const g = newGame();
  g.roll([1, 3]); // ô 4 thuế 200
  assert.equal(g.state.pot, 200);
  g.endTurn();
  g.player(1).pos = 17;
  g.roll([1, 2]); // ô 20
  assert.equal(g.state.pot, 0);
  assert.equal(g.player(1).money, START_MONEY + 200);
});

test('phá sản: tự thanh lý tài sản, hết tiền thì thua và kết thúc game', () => {
  const g = newGame();
  g.state.tiles[39].owner = 1;
  g.state.tiles[39].level = 4;
  g.state.tiles[1].owner = 0;
  g.state.tiles[1].level = 2;
  g.player(0).money = 100;
  g.player(0).pos = 36;
  const ev = g.roll([1, 2]); // 36 -> 39
  assert.ok(ev.some((e) => e.type === 'sellBuilding'));
  assert.ok(ev.some((e) => e.type === 'bankrupt' && e.player === 0));
  assert.equal(g.state.phase, 'over');
  assert.equal(g.state.winner, 1);
  assert.equal(g.state.tiles[1].owner, null);
});

test('giới hạn số vòng: người giàu nhất thắng', () => {
  const g = newGame(PLAYERS, { maxRounds: 1 });
  g.player(1).money = 5000;
  g.roll([1, 3]); g.endTurn();
  g.roll([1, 3]); g.endTurn();
  assert.equal(g.state.phase, 'over');
  assert.equal(g.state.winner, 1);
});

test('snapshot khôi phục đúng trạng thái', () => {
  const g = newGame();
  g.roll([1, 2]); g.decide(true); g.endTurn();
  const g2 = new Game({ snapshot: g.snapshot() });
  assert.deepEqual(g2.state, g.state);
  g2.roll([2, 1]);
  assert.notDeepEqual(g2.state, g.state);
});

test('mô phỏng 300 ván bot: bảo toàn bất biến và luôn kết thúc', () => {
  const four = [
    { name: 'A', color: '#f00', token: 'hat', isBot: true },
    { name: 'B', color: '#0f0', token: 'lantern', isBot: true },
    { name: 'C', color: '#00f', token: 'drum', isBot: true },
    { name: 'D', color: '#ff0', token: 'lotus', isBot: true },
  ];
  let finished = 0;
  for (let seed = 1; seed <= 300; seed++) {
    const g = new Game({ players: four.slice(0, 2 + (seed % 3)), rng: seeded(seed), maxRounds: 150 });
    let steps = 0;
    while (g.state.phase !== 'over' && steps < 20000) {
      steps++;
      const s = g.state;
      if (s.phase === 'roll') {
        const c = botJailChoice(g);
        if (c === 'card') g.useJailCard();
        else if (c === 'fine') g.payJailFine();
        g.roll();
      } else if (s.phase === 'decide') {
        g.decide(botDecide(g));
      } else if (s.phase === 'end') {
        let b;
        while ((b = botNextBuild(g)) !== null) g.buildOn(b);
        g.endTurn();
      }
      for (const p of s.players) {
        assert.ok(p.money >= 0, `tiền âm (seed ${seed})`);
        assert.ok(p.pos >= 0 && p.pos < 40);
        if (p.bankrupt) assert.ok(s.tiles.every((t) => t.owner !== p.id));
      }
      s.tiles.forEach((t, i) => {
        if (t.owner === null) assert.equal(t.level, 0);
        if (t.level > 0) assert.equal(TILES[i].type, 'property');
        assert.ok(t.level <= 4);
      });
    }
    assert.equal(g.state.phase, 'over', `ván seed ${seed} không kết thúc`);
    assert.ok(g.state.winner !== null);
    finished++;
  }
  assert.equal(finished, 300);
});
