// Bàn cờ 3D: đế sơn mài, 40 ô có texture vẽ bằng canvas, mô hình công trình,
// mặt trống đồng Đông Sơn ở giữa, chồng thẻ, khung chủ sở hữu và nhà/khách sạn.
import * as THREE from 'three';
import { TILES, GROUPS, formatMoney } from '../data/board-data.js';
import { buildLandmark } from './landmarks.js';
import {
  mat, box, gableRoof, canvasTexture, enableShadows, tween, easeOutBack, easeInOutCubic,
} from './helpers.js';

export const TILE_W = 2.2;
export const TILE_D = 3.6;
export const HALF = TILE_D + 4.5 * TILE_W; // 13.5
export const TILE_TOP = 0.12;

const FONT = '"Be Vietnam Pro", "Segoe UI", Arial, sans-serif';

const TYPE_BAND = {
  transport: { color: '#37474f', label: 'GIAO THÔNG' },
  utility: { color: '#00695c', label: 'NĂNG LƯỢNG' },
};

/** Vị trí & hướng của ô i (trục +z cục bộ hướng ra ngoài mép bàn). */
export function tileFrame(i) {
  const side = Math.floor(i / 10);
  const k = i % 10;
  let x;
  const z = HALF - TILE_D / 2;
  if (k === 0) x = HALF - TILE_D / 2;
  else x = HALF - TILE_D - TILE_W * (k - 1) - TILE_W / 2;
  const theta = -side * (Math.PI / 2);
  const c = Math.cos(theta);
  const s = Math.sin(theta);
  return { x: x * c + z * s, z: -x * s + z * c, rotY: theta, corner: k === 0 };
}

function wrapText(ctx, text, maxWidth) {
  const words = text.split(' ');
  const lines = [];
  let line = '';
  for (const w of words) {
    const test = line ? `${line} ${w}` : w;
    if (ctx.measureText(test).width > maxWidth && line) {
      lines.push(line);
      line = w;
    } else {
      line = test;
    }
  }
  if (line) lines.push(line);
  return lines;
}

function fitLines(ctx, text, maxWidth, maxLines, size, weight = 800) {
  let s = size;
  let lines;
  do {
    ctx.font = `${weight} ${s}px ${FONT}`;
    lines = wrapText(ctx, text, maxWidth);
    s -= 2;
  } while ((lines.length > maxLines || lines.some((l) => ctx.measureText(l).width > maxWidth)) && s > 14);
  return { lines, size: s + 2 };
}

function drawPaper(ctx, w, h) {
  const grd = ctx.createLinearGradient(0, 0, 0, h);
  grd.addColorStop(0, '#fbf4e2');
  grd.addColorStop(1, '#f1e4c4');
  ctx.fillStyle = grd;
  ctx.fillRect(0, 0, w, h);
  ctx.strokeStyle = 'rgba(122, 74, 26, 0.55)';
  ctx.lineWidth = 6;
  ctx.strokeRect(3, 3, w - 6, h - 6);
}

function drawCentered(ctx, lines, size, cx, cy, color) {
  ctx.fillStyle = color;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  const lh = size * 1.12;
  const start = cy - ((lines.length - 1) * lh) / 2;
  lines.forEach((l, i) => ctx.fillText(l, cx, start + i * lh));
}

function tileTexture(tile) {
  const W = 312;
  const H = 512;
  return canvasTexture(W, H, (ctx) => {
    drawPaper(ctx, W, H);
    const band = tile.group ? { color: GROUPS[tile.group].color, label: GROUPS[tile.group].name } : TYPE_BAND[tile.type];
    if (band) {
      ctx.fillStyle = band.color;
      ctx.fillRect(6, 6, W - 12, 74);
      const { lines, size } = fitLines(ctx, band.label.toUpperCase(), W - 40, 2, 24, 700);
      drawCentered(ctx, lines, size, W / 2, 43, 'rgba(255,255,255,0.95)');
    }
    // vùng giữa để mô hình đứng — hoa văn mờ
    ctx.strokeStyle = 'rgba(160, 110, 50, 0.12)';
    ctx.lineWidth = 2;
    for (let r = 30; r < 120; r += 18) {
      ctx.beginPath();
      ctx.arc(W / 2, 200, r, 0, Math.PI * 2);
      ctx.stroke();
    }
    let title = tile.name;
    let titleColor = '#3b2412';
    if (tile.type === 'chance') { title = 'CƠ HỘI'; titleColor = '#e06a00'; }
    if (tile.type === 'community') { title = 'KHÍ VẬN'; titleColor = '#c8102e'; }
    const { lines, size } = fitLines(ctx, title, W - 34, 3, 40);
    drawCentered(ctx, lines, size, W / 2, 380, titleColor);
    let footer = '';
    if (tile.price) footer = formatMoney(tile.price);
    if (tile.type === 'tax') footer = `Nộp ${formatMoney(tile.amount)}`;
    if (footer) {
      ctx.font = `700 30px ${FONT}`;
      ctx.fillStyle = '#7a4a1a';
      ctx.textAlign = 'center';
      ctx.fillText(footer, W / 2, 476);
    }
  });
}

function cornerTexture(tile) {
  const S = 512;
  return canvasTexture(S, S, (ctx) => {
    drawPaper(ctx, S, S);
    const colors = { go: '#c62828', jail: '#455a64', parking: '#2e7d32', gotojail: '#1565c0' };
    const subs = {
      go: 'Nhận 200 tr₫ mỗi vòng',
      jail: 'Chỉ ghé thăm',
      parking: 'Nhận Quỹ chung',
      gotojail: 'Đi thẳng vào Nhà tạm giữ',
    };
    ctx.fillStyle = colors[tile.type];
    ctx.font = `900 54px ${FONT}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(tile.name.toUpperCase(), S / 2, S - 92);
    ctx.font = `600 28px ${FONT}`;
    ctx.fillStyle = '#5a3a1a';
    ctx.fillText(subs[tile.type], S / 2, S - 44);
    if (tile.type === 'go') {
      ctx.fillStyle = 'rgba(198, 40, 40, 0.18)';
      ctx.beginPath();
      ctx.moveTo(40, 120);
      ctx.lineTo(160, 50);
      ctx.lineTo(160, 90);
      ctx.lineTo(470, 90);
      ctx.lineTo(470, 150);
      ctx.lineTo(160, 150);
      ctx.lineTo(160, 190);
      ctx.closePath();
      ctx.fill();
    }
  });
}

/** Mặt trống đồng Đông Sơn + tên game ở giữa bàn. */
function centerTexture() {
  const S = 2048;
  return canvasTexture(S, S, (ctx) => {
    const c = S / 2;
    const bg = ctx.createRadialGradient(c, c, 100, c, c, S * 0.72);
    bg.addColorStop(0, '#7a1c14');
    bg.addColorStop(1, '#2a0907');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, S, S);

    const bronze = 'rgba(232, 178, 92, 0.55)';
    const bronzeSoft = 'rgba(232, 178, 92, 0.28)';
    ctx.strokeStyle = bronze;
    ctx.fillStyle = bronze;
    // ngôi sao 14 cánh
    ctx.beginPath();
    for (let i = 0; i < 28; i++) {
      const a = (i * Math.PI) / 14;
      const r = i % 2 === 0 ? 210 : 70;
      ctx.lineTo(c + Math.cos(a) * r, c + Math.sin(a) * r);
    }
    ctx.closePath();
    ctx.fill();
    // các vành hoa văn
    const rings = [240, 270, 330, 360, 470, 500, 640, 670, 800, 830, 940, 970];
    ctx.lineWidth = 6;
    rings.forEach((r) => { ctx.beginPath(); ctx.arc(c, c, r, 0, Math.PI * 2); ctx.stroke(); });
    // vành chấm tròn
    ctx.fillStyle = bronzeSoft;
    for (const [r, n] of [[300, 48], [815, 140], [955, 160]]) {
      for (let i = 0; i < n; i++) {
        const a = (i / n) * Math.PI * 2;
        ctx.beginPath();
        ctx.arc(c + Math.cos(a) * r, c + Math.sin(a) * r, 7, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    // vành vạch xiên
    ctx.lineWidth = 5;
    ctx.strokeStyle = bronzeSoft;
    for (let i = 0; i < 90; i++) {
      const a = (i / 90) * Math.PI * 2;
      ctx.beginPath();
      ctx.moveTo(c + Math.cos(a) * 365, c + Math.sin(a) * 365);
      ctx.lineTo(c + Math.cos(a + 0.05) * 465, c + Math.sin(a + 0.05) * 465);
      ctx.stroke();
    }
    // chim Lạc cách điệu bay quanh vành ngoài
    ctx.fillStyle = bronze;
    for (let i = 0; i < 18; i++) {
      const a = (i / 18) * Math.PI * 2;
      ctx.save();
      ctx.translate(c + Math.cos(a) * 735, c + Math.sin(a) * 735);
      ctx.rotate(a + Math.PI / 2);
      ctx.beginPath();
      ctx.moveTo(-50, 8);
      ctx.quadraticCurveTo(-10, -30, 40, -8);
      ctx.lineTo(70, -26);
      ctx.lineTo(48, 4);
      ctx.quadraticCurveTo(0, 24, -50, 8);
      ctx.fill();
      ctx.beginPath();
      ctx.moveTo(-10, -4);
      ctx.lineTo(-40, -50);
      ctx.lineTo(10, -10);
      ctx.fill();
      ctx.restore();
    }
    // tiêu đề
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.shadowColor = 'rgba(0,0,0,0.6)';
    ctx.shadowBlur = 24;
    const gold = ctx.createLinearGradient(0, c - 200, 0, c + 200);
    gold.addColorStop(0, '#fff1b8');
    gold.addColorStop(0.5, '#f2c14e');
    gold.addColorStop(1, '#c98a1b');
    ctx.fillStyle = gold;
    ctx.font = `900 190px ${FONT}`;
    ctx.fillText('CỜ TỶ PHÚ', c, c - 80);
    ctx.font = `900 150px ${FONT}`;
    ctx.fillText('VIỆT NAM', c, c + 110);
    ctx.shadowBlur = 0;
    ctx.font = `600 52px ${FONT}`;
    ctx.fillStyle = 'rgba(255, 236, 190, 0.85)';
    ctx.fillText('Hành trình qua di sản & thắng cảnh', c, c + 250);
  });
}

function deckTexture(label, color) {
  return canvasTexture(256, 360, (ctx, w, h) => {
    ctx.fillStyle = color;
    ctx.fillRect(0, 0, w, h);
    ctx.strokeStyle = '#ffe08a';
    ctx.lineWidth = 10;
    ctx.strokeRect(14, 14, w - 28, h - 28);
    ctx.fillStyle = '#ffe08a';
    ctx.font = `900 46px ${FONT}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, w / 2, h / 2);
  });
}

function houseMesh(ownerColor) {
  const g = new THREE.Group();
  g.add(box(0.36, 0.4, 0.34, mat(0xf4ead2), 0, 0, 0));
  g.add(gableRoof(0.42, 0.2, 0.4, mat(ownerColor, { roughness: 0.5 }), 0, 0.4, 0));
  g.add(box(0.1, 0.16, 0.02, mat(0x5a3a22), 0, 0, 0.175));
  g.add(box(0.12, 0.08, 0.02, mat(0x8fc4e8, { emissive: 0x223344 }), 0, 0.24, 0.175));
  return enableShadows(g);
}

function hotelMesh(ownerColor) {
  const g = new THREE.Group();
  g.add(box(1.4, 0.75, 0.44, mat(0xf7f3ea), 0, 0, 0));
  for (let y = 0.12; y < 0.7; y += 0.14) g.add(box(1.41, 0.04, 0.45, mat(ownerColor, { roughness: 0.4 }), 0, y, 0));
  g.add(box(1.5, 0.06, 0.5, mat(0x9a7a3a, { metalness: 0.5 }), 0, 0.75, 0));
  g.add(box(0.6, 0.16, 0.05, mat(0xf2c14e, { metalness: 0.7, roughness: 0.25, emissive: 0x4a3200 }), 0, 0.81, 0.18));
  for (let i = 0; i < 5; i++) {
    const star = new THREE.Mesh(new THREE.OctahedronGeometry(0.035, 0), mat(0xffe08a, { emissive: 0x806000 }));
    star.position.set(-0.2 + i * 0.1, 0.89, 0.21);
    g.add(star);
  }
  return enableShadows(g);
}

export class Board3D {
  constructor(scene) {
    this.scene = scene;
    this.group = new THREE.Group();
    scene.add(this.group);
    this.tiles = [];
    this.tickers = [];
    this.pickables = [];
    this._buildBase();
    this._buildTiles();
    this._buildCenter();
    this.group.updateMatrixWorld(true);
  }

  _buildBase() {
    const size = HALF * 2;
    const lacquer = new THREE.MeshStandardMaterial({ color: 0x5a1410, roughness: 0.35, metalness: 0.1 });
    const base = new THREE.Mesh(new THREE.BoxGeometry(size + 1.2, 0.8, size + 1.2), lacquer);
    base.position.y = -0.4;
    base.receiveShadow = true;
    base.castShadow = true;
    this.group.add(base);
    const goldTrim = mat(0xd4a24a, { metalness: 0.8, roughness: 0.3 });
    const trimH = 0.12;
    for (const [w, d, x, z] of [
      [size + 1.3, 0.18, 0, (size + 1.2) / 2], [size + 1.3, 0.18, 0, -(size + 1.2) / 2],
      [0.18, size + 1.3, (size + 1.2) / 2, 0], [0.18, size + 1.3, -(size + 1.2) / 2, 0],
    ]) {
      const t = new THREE.Mesh(new THREE.BoxGeometry(w, trimH, d), goldTrim);
      t.position.set(x, -0.02, z);
      this.group.add(t);
    }
    // bệ đá chìm dưới nước
    const pedestal = new THREE.Mesh(new THREE.CylinderGeometry(size * 0.56, size * 0.64, 2.4, 8),
      mat(0x4a4038, { flat: true, roughness: 1 }));
    pedestal.position.y = -2.0;
    pedestal.rotation.y = Math.PI / 8;
    pedestal.receiveShadow = true;
    this.group.add(pedestal);
  }

  _buildTiles() {
    const sideMat = mat(0xe9dcbc, { roughness: 0.8 });
    TILES.forEach((tile, i) => {
      const f = tileFrame(i);
      const w = f.corner ? TILE_D : TILE_W;
      const g = new THREE.Group();
      g.position.set(f.x, 0, f.z);
      g.rotation.y = f.rotY;
      const body = new THREE.Mesh(new THREE.BoxGeometry(w - 0.06, TILE_TOP, TILE_D - 0.06), sideMat);
      body.position.y = TILE_TOP / 2;
      body.receiveShadow = true;
      g.add(body);
      const topMat = new THREE.MeshStandardMaterial({
        map: f.corner ? cornerTexture(tile) : tileTexture(tile), roughness: 0.85, emissive: 0x000000,
      });
      const top = new THREE.Mesh(new THREE.PlaneGeometry(w - 0.06, TILE_D - 0.06), topMat);
      top.rotation.x = -Math.PI / 2;
      top.position.y = TILE_TOP + 0.002;
      top.receiveShadow = true;
      top.userData.tileIndex = i;
      g.add(top);
      this.pickables.push(top);

      const model = buildLandmark(tile.model);
      model.position.set(0, TILE_TOP, f.corner ? 0 : -0.38);
      g.add(model);
      if (model.userData.tick) this.tickers.push(model.userData.tick);

      const buildings = new THREE.Group();
      buildings.position.set(0, TILE_TOP, -TILE_D / 2 + 0.3);
      g.add(buildings);

      this.group.add(g);
      this.tiles.push({ group: g, top, topMat, model, buildings, frame: null, level: 0, corner: f.corner });
    });
  }

  _buildCenter() {
    const inner = (HALF - TILE_D) * 2;
    const centerMat = new THREE.MeshStandardMaterial({ map: centerTexture(), roughness: 0.4, metalness: 0.15 });
    const center = new THREE.Mesh(new THREE.PlaneGeometry(inner - 0.1, inner - 0.1), centerMat);
    center.rotation.x = -Math.PI / 2;
    center.position.y = 0.01;
    center.receiveShadow = true;
    this.group.add(center);

    this.decks = {};
    const makeDeck = (name, label, color, x, z, rot) => {
      const tex = deckTexture(label, color);
      const side = mat(0xfff6e0);
      const deck = new THREE.Mesh(new THREE.BoxGeometry(2.0, 0.36, 2.8), [
        side, side, new THREE.MeshStandardMaterial({ map: tex, roughness: 0.5 }), side, side, side,
      ]);
      deck.position.set(x, 0.19, z);
      deck.rotation.y = rot;
      deck.castShadow = true;
      deck.receiveShadow = true;
      this.group.add(deck);
      this.decks[name] = { mesh: deck, tex, color };
    };
    makeDeck('chance', 'CƠ HỘI', '#e06a00', -5.6, 4.4, Math.PI / 4);
    makeDeck('community', 'KHÍ VẬN', '#b0102a', 5.6, -4.4, Math.PI / 4);
  }

  tick(time, dt) {
    for (const t of this.tickers) t(time, dt);
  }

  /** Toạ độ thế giới cho quân cờ thứ slot trên ô index. */
  tokenSlot(index, slot, jailed = false) {
    const t = this.tiles[index];
    let local;
    if (t.corner) {
      const visit = [[0.85, 0.85], [1.4, 0.85], [0.85, 1.4], [1.4, 1.4]];
      const inJail = [[0.05, 0.05], [0.05, -0.75], [-0.75, 0.05], [-0.33, 0.05]];
      local = (jailed ? inJail : visit)[slot % 4];
    } else {
      local = [[-0.5, 0.85], [0.5, 0.85], [-0.5, 1.4], [0.5, 1.4]][slot % 4];
    }
    return t.group.localToWorld(new THREE.Vector3(local[0], TILE_TOP, local[1]));
  }

  tileCenter(index) {
    return this.tiles[index].group.localToWorld(new THREE.Vector3(0, TILE_TOP, 0));
  }

  setHover(index) {
    if (this.hovered !== undefined && this.hovered !== null) {
      this.tiles[this.hovered].topMat.emissive.setHex(0x000000);
    }
    this.hovered = index;
    if (index !== null && index !== undefined) this.tiles[index].topMat.emissive.setHex(0x3a2a10);
  }

  setOwner(index, color) {
    const t = this.tiles[index];
    if (t.frame) {
      t.group.remove(t.frame);
      t.frame = null;
    }
    if (color === null || color === undefined) return;
    const w = (t.corner ? TILE_D : TILE_W) - 0.1;
    const d = TILE_D - 0.1;
    const m = mat(color, { emissive: color, emissiveIntensity: 0.35, roughness: 0.4 });
    const th = 0.12;
    const frame = new THREE.Group();
    frame.add(box(w, 0.05, th, m, 0, TILE_TOP, d / 2 - th / 2), box(w, 0.05, th, m, 0, TILE_TOP, -d / 2 + th / 2));
    frame.add(box(th, 0.05, d, m, w / 2 - th / 2, TILE_TOP, 0), box(th, 0.05, d, m, -w / 2 + th / 2, TILE_TOP, 0));
    // cờ đuôi nheo nhỏ đánh dấu chủ
    frame.add(box(0.02, 0.5, 0.02, mat(0xdddddd), w / 2 - 0.2, TILE_TOP, d / 2 - 0.25));
    const pennant = new THREE.Mesh(new THREE.ConeGeometry(0.1, 0.3, 3), m);
    pennant.rotation.z = Math.PI / 2;
    pennant.position.set(w / 2 - 0.35, TILE_TOP + 0.52, d / 2 - 0.25);
    frame.add(pennant);
    t.group.add(frame);
    t.frame = frame;
    frame.scale.set(1, 0.01, 1);
    tween(400, (k) => frame.scale.set(1, Math.max(0.01, easeOutBack(k)), 1));
  }

  /** Cập nhật nhà/khách sạn trên ô; animate=true thì công trình mới "mọc" lên. */
  setLevel(index, level, ownerColor, animate = true) {
    const t = this.tiles[index];
    const prev = t.level;
    t.level = level;
    t.buildings.clear();
    if (level <= 0) return;
    const fresh = [];
    if (level >= 4) {
      const h = hotelMesh(ownerColor);
      t.buildings.add(h);
      if (prev < 4) fresh.push(h);
    } else {
      [-0.66, 0, 0.66].slice(0, level).forEach((x, k) => {
        const h = houseMesh(ownerColor);
        h.position.x = x;
        t.buildings.add(h);
        if (k >= prev) fresh.push(h);
      });
    }
    if (animate) {
      fresh.forEach((h) => h.scale.setScalar(0.01));
      tween(550, (k) => fresh.forEach((h) => h.scale.setScalar(Math.max(0.01, easeOutBack(k)))));
    }
  }

  /** Lá thẻ bay lên từ chồng thẻ rồi lật — trả về Promise khi xong. */
  async drawCard(deckName, camera) {
    const deck = this.decks[deckName];
    const card = new THREE.Mesh(new THREE.BoxGeometry(2.0, 0.03, 2.8), [
      mat(0xfff6e0), mat(0xfff6e0), new THREE.MeshStandardMaterial({ map: deck.tex }), mat(0xfff6e0), mat(0xfff6e0), mat(0xfff6e0),
    ]);
    card.position.copy(deck.mesh.position);
    card.position.y = 0.4;
    card.rotation.copy(deck.mesh.rotation);
    this.group.add(card);
    const start = card.position.clone();
    const target = new THREE.Vector3().lerpVectors(start, camera.position, 0.25);
    target.y = Math.max(target.y, 4);
    await tween(700, (k) => {
      const e = easeInOutCubic(k);
      card.position.lerpVectors(start, target, e);
      card.rotation.x = e * Math.PI * 1.0;
      card.rotation.z = e * 0.4;
    });
    await tween(300, (k) => card.scale.setScalar(1 - k));
    this.group.remove(card);
  }
}
