// Mô hình 3D low-poly dựng hoàn toàn bằng code cho từng công trình / thắng cảnh.
// Mỗi hàm trả về THREE.Group có đáy tại y = 0, chiếm khoảng x ∈ [-0.9, 0.9], z ∈ [-0.8, 0.8].
// Đối tượng cần chuyển động gắn hàm `userData.tick(time, dt)`.
import * as THREE from 'three';
import {
  mat, box, cyl, cone, sphere, gableRoof, pagodaRoof, water, tree, palm, canvasTexture, enableShadows,
} from './helpers.js';

const C = {
  wood: 0x7a4a2a, darkWood: 0x4a2a15, roofTile: 0x8a3b24, darkRoof: 0x3e3430,
  brick: 0xa8492f, stone: 0x9d988c, grass: 0x6cad4f, darkGrass: 0x3f7f3a, sand: 0xe9c88a,
  gold: 0xd9a520, red: 0xd7262e, yellow: 0xf2c94c, white: 0xf3efe6, rock: 0x6e6a60,
  water: 0x3aa0c8, black: 0x222222,
};

function rand(seed) {
  let s = seed;
  return () => {
    s = (s * 9301 + 49297) % 233280;
    return s / 233280;
  };
}

const ground = (color, w = 1.7, d = 1.5) => box(w, 0.04, d, mat(color), 0, 0, 0);

// ---------------------------------------------------------------- Tây Nguyên

function woodenChurch() {
  const g = new THREE.Group();
  g.add(ground(C.grass));
  const wood = mat(0x5a3218, { roughness: 0.9 });
  g.add(box(0.55, 0.4, 0.85, wood, 0, 0.04, -0.1));
  const roof = gableRoof(0.95, 0.42, 0.72, mat(0x2e1d12), 0, 0.44, -0.1);
  roof.rotation.y = Math.PI / 2;
  g.add(roof);
  // tháp chuông phía trước
  g.add(box(0.28, 0.78, 0.28, wood, 0, 0.04, 0.42));
  const spire = cone(0.21, 0.5, mat(0x2e1d12), 0, 0.82, 0.42, 4);
  spire.rotation.y = Math.PI / 4;
  g.add(spire);
  g.add(box(0.02, 0.16, 0.02, mat(C.white), 0, 1.3, 0.42));
  g.add(box(0.1, 0.02, 0.02, mat(C.white), 0, 1.4, 0.42));
  // cửa + cửa sổ
  g.add(box(0.12, 0.2, 0.02, mat(0x1a0f08), 0, 0.04, 0.565));
  g.add(sphere(0.05, mat(0xe8d48a, { emissive: 0x6b5a20 }), 0, 0.55, 0.565, 10, 8));
  for (const z of [-0.35, -0.1, 0.15]) {
    g.add(box(0.02, 0.14, 0.08, mat(0xe8d48a, { emissive: 0x3a3010 }), 0.285, 0.18, z));
    g.add(box(0.02, 0.14, 0.08, mat(0xe8d48a, { emissive: 0x3a3010 }), -0.285, 0.18, z));
  }
  g.add(tree(-0.65, 0.4, 1.3), tree(0.65, -0.45, 1.5), tree(0.7, 0.45, 1.1));
  return g;
}

function waterfall() {
  const g = new THREE.Group();
  const rock = mat(0x5f6f55, { flat: true });
  g.add(box(1.7, 0.85, 0.55, rock, 0, 0, -0.48));
  g.add(box(1.7, 0.08, 0.55, mat(C.darkGrass), 0, 0.85, -0.48));
  g.add(water(1.7, 0.95, 0, 0.27, 0.03, 0x4fb1c9));
  const tex = canvasTexture(64, 128, (ctx, w, h) => {
    ctx.fillStyle = '#bfe9f5';
    ctx.fillRect(0, 0, w, h);
    for (let i = 0; i < 40; i++) {
      ctx.fillStyle = `rgba(255,255,255,${0.4 + Math.random() * 0.6})`;
      ctx.fillRect(Math.random() * w, Math.random() * h, 2 + Math.random() * 3, 10 + Math.random() * 30);
    }
  });
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  const fall = new THREE.Mesh(new THREE.PlaneGeometry(1.2, 0.88),
    new THREE.MeshStandardMaterial({ map: tex, transparent: true, opacity: 0.92, roughness: 0.2 }));
  fall.position.set(0, 0.47, -0.2);
  g.add(fall);
  const mist = sphere(0.55, mat(0xffffff, { opacity: 0.35 }), 0, 0.05, -0.05, 16, 8);
  mist.scale.set(1.3, 0.25, 0.5);
  g.add(mist);
  const r = rand(7);
  for (let i = 0; i < 6; i++) {
    const s = new THREE.Mesh(new THREE.DodecahedronGeometry(0.06 + r() * 0.06, 0), mat(C.rock, { flat: true }));
    s.position.set(-0.75 + r() * 1.5, 0.05, 0.25 + r() * 0.45);
    s.castShadow = true;
    g.add(s);
  }
  g.add(tree(-0.7, -0.6, 1.2, C.darkGrass, 0.9), tree(0.65, -0.55, 1.4, C.darkGrass, 0.9));
  g.userData.tick = (t, dt) => { tex.offset.y += dt * 0.8; mist.scale.x = 1.3 + Math.sin(t * 2) * 0.05; };
  return g;
}

// ---------------------------------------------------------------- Miền núi phía Bắc

function flagTexture() {
  return canvasTexture(180, 120, (ctx, w, h) => {
    ctx.fillStyle = '#da251d';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#ffde00';
    ctx.beginPath();
    const cx = w / 2, cy = h / 2, R = h * 0.3, r = R * 0.38;
    for (let i = 0; i < 10; i++) {
      const a = -Math.PI / 2 + (i * Math.PI) / 5;
      const rr = i % 2 === 0 ? R : r;
      ctx.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
    }
    ctx.closePath();
    ctx.fill();
  });
}

/** Lá cờ phấp phới (mép trái gắn vào cột). */
export function wavingFlag(width, height, texture = flagTexture()) {
  const geo = new THREE.PlaneGeometry(width, height, 12, 2);
  geo.translate(width / 2, 0, 0);
  const base = geo.attributes.position.array.slice();
  const m = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ map: texture, side: THREE.DoubleSide, roughness: 0.8 }));
  m.castShadow = true;
  m.userData.tick = (t) => {
    const p = geo.attributes.position.array;
    for (let i = 0; i < p.length; i += 3) {
      const x = base[i];
      p[i + 2] = Math.sin(x * 9 - t * 5) * 0.06 * (x / width);
    }
    geo.attributes.position.needsUpdate = true;
    geo.computeVertexNormals();
  };
  return m;
}

function flagpole() {
  const g = new THREE.Group();
  g.add(cone(0.85, 0.45, mat(0x5e9a4a, { flat: true }), 0, 0, 0, 7));
  g.add(cyl(0.2, 0.24, 0.42, mat(0xc9c1ad), 0, 0.4, 0, 8));
  g.add(cyl(0.22, 0.22, 0.04, mat(0xb8b09a), 0, 0.82, 0, 8));
  g.add(cyl(0.012, 0.016, 0.85, mat(0xeeeeee, { metalness: 0.6, roughness: 0.3 }), 0, 0.85, 0, 8));
  const flag = wavingFlag(0.5, 0.33);
  flag.position.set(0.01, 1.52, 0);
  g.add(flag);
  // bậc thang lên đỉnh
  for (let i = 0; i < 4; i++) g.add(box(0.16, 0.04, 0.08, mat(0xb8b09a), 0, 0.1 + i * 0.08, 0.62 - i * 0.1));
  g.userData.tick = (t) => flag.userData.tick(t);
  return g;
}

function terraces() {
  const g = new THREE.Group();
  for (let i = 0; i < 7; i++) {
    const color = i % 2 === 0 ? 0xc8b232 : 0x9fc23a;
    const layer = cyl(0.9 - i * 0.12, 0.92 - i * 0.12, 0.08, mat(color, { roughness: 0.9 }), 0, i * 0.08, 0, 28);
    layer.scale.z = 0.82;
    g.add(layer);
  }
  // chòi canh lúa của người Mông
  g.add(box(0.14, 0.1, 0.12, mat(0x8a6a3d), 0, 0.56, 0));
  g.add(gableRoof(0.18, 0.1, 0.16, mat(0xcaa860), 0, 0.66, 0));
  g.add(tree(0.75, -0.6, 1.2), tree(-0.8, 0.55, 1));
  return g;
}

function mountain() {
  const g = new THREE.Group();
  const peak = cone(0.8, 1.35, mat(0x587a55, { flat: true }), 0, 0, -0.1, 6);
  g.add(peak);
  g.add(cone(0.55, 0.85, mat(0x6b8f5f, { flat: true }), -0.45, 0, 0.3, 6));
  g.add(cone(0.45, 0.6, mat(0x7aa06b, { flat: true }), 0.5, 0, 0.35, 5));
  // chóp inox 3.143 m
  const marker = cone(0.07, 0.16, mat(0xdddddd, { metalness: 0.9, roughness: 0.2 }), 0, 1.33, -0.1, 3);
  g.add(marker);
  // cáp treo
  const a = new THREE.Vector3(0.12, 1.2, -0.1);
  const b = new THREE.Vector3(0.85, 0.15, 0.6);
  const len = a.distanceTo(b);
  const cable = new THREE.Mesh(new THREE.CylinderGeometry(0.006, 0.006, len, 4), mat(0x333333));
  cable.position.copy(a).lerp(b, 0.5);
  cable.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), b.clone().sub(a).normalize());
  g.add(cable);
  const cabin = box(0.08, 0.07, 0.06, mat(C.red), 0, -0.07, 0);
  const cabinG = new THREE.Group();
  cabinG.add(cabin);
  g.add(cabinG);
  // mây
  const clouds = [];
  for (let i = 0; i < 3; i++) {
    const c = sphere(0.18, mat(0xffffff, { opacity: 0.7 }), -0.5 + i * 0.5, 0.7 + i * 0.12, 0.2 - i * 0.2, 12, 8);
    c.scale.set(1.6, 0.5, 0.9);
    c.castShadow = false;
    clouds.push(c);
    g.add(c);
  }
  g.userData.tick = (t) => {
    const k = (Math.sin(t * 0.35) + 1) / 2;
    cabinG.position.copy(a).lerp(b, k);
    clouds.forEach((c, i) => { c.position.x = Math.sin(t * 0.2 + i * 2) * 0.55; });
  };
  return g;
}

// ---------------------------------------------------------------- Miền Tây

function boat(hullColor = C.darkWood, length = 0.55) {
  const g = new THREE.Group();
  const shape = new THREE.Shape();
  shape.moveTo(-length / 2, 0.1);
  shape.lineTo(length / 2, 0.12);
  shape.lineTo(length * 0.36, 0);
  shape.lineTo(-length * 0.38, 0);
  shape.closePath();
  const geo = new THREE.ExtrudeGeometry(shape, { depth: 0.16, bevelEnabled: false });
  geo.translate(0, 0, -0.08);
  const hull = new THREE.Mesh(geo, mat(hullColor));
  hull.castShadow = true;
  g.add(hull);
  // mắt ghe
  g.add(sphere(0.015, mat(0xffffff), length * 0.38, 0.08, 0.081, 6, 4));
  g.add(sphere(0.015, mat(0xffffff), length * 0.38, 0.08, -0.081, 6, 4));
  return g;
}

function floatingMarket() {
  const g = new THREE.Group();
  g.add(water(1.75, 1.55, 0, 0, 0.02, 0x8a7a4a));
  const fruitColors = [0xff8c1a, 0x6cbf3a, 0x8e3fa0, 0xffd23a, 0xd2302a];
  const boats = [];
  const configs = [[-0.35, -0.4, 0.3], [0.3, 0.05, -0.2], [-0.25, 0.5, 0.1]];
  const r = rand(3);
  configs.forEach(([x, z, rot], k) => {
    const b = boat(k === 1 ? 0x2d5c7a : C.darkWood, 0.7);
    for (let i = 0; i < 9; i++) {
      b.add(sphere(0.045, mat(fruitColors[Math.floor(r() * fruitColors.length)]), -0.15 + r() * 0.3, 0.13 + r() * 0.05, -0.05 + r() * 0.1, 8, 6));
    }
    // mui ghe
    const roof = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.1, 0.18, 10, 1, true, 0, Math.PI), mat(0x5a3d22, { side: THREE.DoubleSide }));
    roof.rotation.z = Math.PI / 2;
    roof.rotation.y = Math.PI / 2;
    roof.position.set(-0.2, 0.12, 0);
    roof.rotation.order = 'YZX';
    b.add(roof);
    // cây bẹo treo hàng
    b.add(cyl(0.008, 0.008, 0.6, mat(0xb08a5a), 0.12, 0.1, 0, 5));
    b.add(sphere(0.05, mat(fruitColors[k]), 0.12, 0.65, 0, 8, 6));
    b.position.set(x, 0.02, z);
    b.rotation.y = rot;
    boats.push(b);
    g.add(b);
  });
  g.add(palm(0.75, -0.6, 1.1), palm(-0.8, -0.65, 0.9));
  g.userData.tick = (t) => boats.forEach((b, i) => {
    b.position.y = 0.02 + Math.sin(t * 1.6 + i) * 0.012;
    b.rotation.x = Math.sin(t * 1.3 + i * 2) * 0.04;
  });
  return g;
}

function mangrove() {
  const g = new THREE.Group();
  g.add(water(1.75, 1.55, 0, 0, 0.02, 0x7fb04a));
  const r = rand(11);
  for (let i = 0; i < 11; i++) {
    const x = -0.75 + r() * 1.5;
    const z = -0.7 + r() * 1.0;
    if (Math.abs(z - 0.15) < 0.12) continue;
    const h = 0.45 + r() * 0.35;
    g.add(cyl(0.015, 0.025, h, mat(0xd9d2c3), x, 0, z, 5));
    const crown = new THREE.Mesh(new THREE.IcosahedronGeometry(0.12 + r() * 0.06, 0), mat(0x3d8b3d, { flat: true }));
    crown.position.set(x, h + 0.06, z);
    crown.scale.y = 1.3;
    crown.castShadow = true;
    g.add(crown);
  }
  // cầu tre
  g.add(box(1.6, 0.03, 0.12, mat(0x9a7a4a), 0, 0.12, 0.15));
  for (let x = -0.7; x <= 0.7; x += 0.35) g.add(cyl(0.012, 0.012, 0.12, mat(0x6b5030), x, 0, 0.15, 5));
  const b = boat(0x6b4b2a, 0.4);
  b.position.set(0.35, 0.02, 0.5);
  g.add(b);
  // cò trắng bay
  const bird = new THREE.Group();
  const body = sphere(0.035, mat(0xffffff), 0, 0, 0, 8, 6);
  body.scale.set(1.8, 0.8, 0.8);
  const wing = box(0.03, 0.005, 0.16, mat(0xffffff), 0, 0, 0);
  bird.add(body, wing);
  g.add(bird);
  g.userData.tick = (t) => {
    bird.position.set(Math.cos(t * 0.6) * 0.55, 0.95 + Math.sin(t * 1.2) * 0.05, Math.sin(t * 0.6) * 0.4 - 0.2);
    bird.rotation.y = -t * 0.6;
    wing.scale.z = 0.6 + Math.abs(Math.sin(t * 8)) * 0.6;
  };
  return g;
}

function shipMonument() {
  const g = new THREE.Group();
  g.add(box(1.7, 0.04, 0.7, mat(0x6b7a3a), 0, 0, -0.4));
  g.add(water(1.7, 0.85, 0, 0.35, 0.02, 0x4f9fb8));
  // biểu tượng mũi tàu
  const shape = new THREE.Shape();
  shape.moveTo(-0.55, 0);
  shape.lineTo(0.35, 0);
  shape.lineTo(0.62, 0.45);
  shape.lineTo(0.3, 0.32);
  shape.lineTo(-0.55, 0.3);
  shape.closePath();
  const geo = new THREE.ExtrudeGeometry(shape, { depth: 0.26, bevelEnabled: true, bevelSize: 0.02, bevelThickness: 0.02, bevelSegments: 1 });
  geo.translate(0, 0, -0.13);
  const ship = new THREE.Mesh(geo, mat(0xf2f0ea, { roughness: 0.5 }));
  ship.castShadow = true;
  ship.rotation.y = -0.5;
  ship.position.set(-0.05, 0.04, 0.0);
  g.add(ship);
  g.add(box(0.18, 0.14, 0.14, mat(0xc9c4b8), -0.3, 0.33, 0.12));
  g.add(cyl(0.01, 0.01, 0.55, mat(0xdddddd), -0.35, 0.47, 0.15, 6));
  const flag = wavingFlag(0.24, 0.16);
  flag.position.set(-0.34, 0.95, 0.15);
  g.add(flag);
  for (const [x, z] of [[-0.75, -0.55], [-0.45, -0.65], [0.55, -0.6], [0.78, -0.45], [0.2, -0.68]]) {
    g.add(tree(x, z, 1.1, 0x2d6b34));
  }
  g.userData.tick = (t) => flag.userData.tick(t);
  return g;
}

// ---------------------------------------------------------------- Nam Trung Bộ

function chamTower(s, brick) {
  const g = new THREE.Group();
  const m = mat(brick, { flat: true, roughness: 0.95 });
  g.add(box(0.34 * s, 0.5 * s, 0.34 * s, m, 0, 0, 0));
  g.add(box(0.29 * s, 0.18 * s, 0.29 * s, m, 0, 0.5 * s, 0));
  g.add(box(0.23 * s, 0.14 * s, 0.23 * s, m, 0, 0.68 * s, 0));
  g.add(box(0.17 * s, 0.12 * s, 0.17 * s, m, 0, 0.82 * s, 0));
  const top = cone(0.1 * s, 0.2 * s, m, 0, 0.94 * s, 0, 4);
  top.rotation.y = Math.PI / 4;
  g.add(top);
  g.add(box(0.1 * s, 0.22 * s, 0.02, mat(0x2a1a12), 0, 0, 0.17 * s + 0.005));
  // trụ ốp tường
  for (const x of [-0.12, 0.12]) g.add(box(0.04 * s, 0.5 * s, 0.02, m, x * s, 0, 0.17 * s + 0.01));
  return g;
}

function chamTowers() {
  const g = new THREE.Group();
  g.add(box(1.7, 0.1, 1.2, mat(C.stone, { flat: true }), 0, 0, -0.15));
  g.add(box(0.5, 0.06, 0.3, mat(C.stone), 0, 0, 0.6));
  const main = chamTower(1.45, 0xb0573a);
  main.position.set(0, 0.1, -0.25);
  const left = chamTower(1.0, 0xa24e34);
  left.position.set(-0.55, 0.1, -0.1);
  const right = chamTower(0.9, 0xa85438);
  right.position.set(0.55, 0.1, -0.05);
  g.add(main, left, right);
  g.add(palm(0.75, 0.55, 0.9), palm(-0.78, 0.5, 1));
  return g;
}

function dunes() {
  const g = new THREE.Group();
  g.add(box(1.75, 0.04, 1.55, mat(C.sand), 0, 0, 0));
  const configs = [[-0.4, -0.35, 0.6, 0xe39a52], [0.35, -0.3, 0.55, 0xf1d6a8], [0.0, 0.25, 0.45, 0xe8a85e], [-0.6, 0.45, 0.3, 0xf4e0b8]];
  for (const [x, z, r, c] of configs) {
    const d = sphere(r, mat(c, { roughness: 1 }), x, 0.02, z, 24, 12);
    d.scale.set(1.3, 0.42, 1);
    g.add(d);
  }
  // xe địa hình
  const atv = new THREE.Group();
  atv.add(box(0.16, 0.06, 0.1, mat(0xd63a2a), 0, 0.04, 0));
  for (const [x, z] of [[-0.06, 0.06], [0.06, 0.06], [-0.06, -0.06], [0.06, -0.06]]) {
    const w = cyl(0.03, 0.03, 0.03, mat(C.black), x, 0, z, 8);
    w.rotation.x = Math.PI / 2;
    w.position.y = 0.03;
    atv.add(w);
  }
  g.add(atv);
  g.add(palm(0.75, 0.5, 1.1));
  g.userData.tick = (t) => {
    const a = t * 0.4;
    atv.position.set(Math.cos(a) * 0.55, 0.05 + Math.max(0, Math.sin(a * 2)) * 0.05, 0.25 + Math.sin(a) * 0.4);
    atv.rotation.y = -a - Math.PI / 2;
  };
  return g;
}

function basalt() {
  const g = new THREE.Group();
  g.add(water(1.75, 1.55, 0, 0, 0.02, 0x2f8fb0));
  const r = rand(5);
  for (let row = 0; row < 7; row++) {
    for (let col = 0; col < 8; col++) {
      const x = -0.72 + col * 0.19 + (row % 2) * 0.095;
      const z = -0.6 + row * 0.165;
      const dist = Math.hypot(x * 0.8, z + 0.2);
      const h = Math.max(0.06, 0.65 - dist * 0.55 + r() * 0.12);
      const shade = 0x34 + Math.floor(r() * 0x18);
      const color = (shade << 16) | (shade << 8) | (shade + 6);
      const m = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.1, h, 6), mat(color, { flat: true, roughness: 0.6 }));
      m.position.set(x, h / 2, z);
      m.castShadow = true;
      m.receiveShadow = true;
      g.add(m);
    }
  }
  return g;
}

// ---------------------------------------------------------------- Di sản miền Trung

function mySon() {
  const g = new THREE.Group();
  g.add(ground(0x4f8a3c));
  const t1 = chamTower(1.25, 0x8f4a30);
  t1.position.set(-0.2, 0.04, -0.2);
  t1.children[t1.children.length - 4].visible = false; // chóp đã đổ
  const t2 = chamTower(0.85, 0x7f4430);
  t2.position.set(0.45, 0.04, -0.35);
  g.add(t1, t2);
  // tường đổ nát + rêu
  g.add(box(0.4, 0.12, 0.08, mat(0x8a4a33, { flat: true }), 0.35, 0.04, 0.25));
  g.add(box(0.2, 0.07, 0.08, mat(0x8a4a33, { flat: true }), -0.45, 0.04, 0.35));
  g.add(box(0.12, 0.03, 0.12, mat(0x5f8f3a), -0.2, 1.06, -0.2));
  const r = rand(19);
  for (let i = 0; i < 8; i++) {
    const a = r() * Math.PI * 2;
    g.add(tree(Math.cos(a) * (0.6 + r() * 0.2), Math.sin(a) * (0.55 + r() * 0.1), 1.2 + r() * 0.8, i % 2 ? C.darkGrass : 0x2e6b2e));
  }
  return g;
}

function lanternMesh(color) {
  const geo = new THREE.SphereGeometry(0.045, 10, 8);
  geo.scale(1, 1.25, 1);
  return new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 0.9, roughness: 0.6 }));
}

function hoiAnHouse(x, z, wall, rot = 0) {
  const g = new THREE.Group();
  g.add(box(0.46, 0.34, 0.38, mat(wall, { roughness: 0.95 }), 0, 0, 0));
  g.add(gableRoof(0.54, 0.18, 0.46, mat(0x6b3a26), 0, 0.34, 0));
  g.add(box(0.12, 0.2, 0.02, mat(0x4a2a15), 0, 0, 0.195));
  g.add(box(0.1, 0.08, 0.02, mat(0x3a5a3a), -0.15, 0.17, 0.195));
  g.add(box(0.1, 0.08, 0.02, mat(0x3a5a3a), 0.15, 0.17, 0.195));
  g.position.set(x, 0.04, z);
  g.rotation.y = rot;
  return g;
}

function hoiAn() {
  const g = new THREE.Group();
  g.add(box(1.75, 0.04, 0.9, mat(0xc9b48a), 0, 0, -0.32));
  g.add(water(1.75, 0.65, 0, 0.45, 0.02, 0x4d8a78));
  g.add(hoiAnHouse(-0.5, -0.45, 0xf2c94c), hoiAnHouse(0.02, -0.5, 0xe8b93a), hoiAnHouse(0.55, -0.42, 0xf0cf6a));
  // Chùa Cầu
  const bridge = new THREE.Group();
  bridge.add(box(0.5, 0.06, 0.2, mat(0x9a5a3a), 0, 0.08, 0));
  bridge.add(box(0.46, 0.14, 0.18, mat(0xc0603f), 0, 0.14, 0));
  const br = gableRoof(0.56, 0.12, 0.26, mat(0x6b3a26), 0, 0.28, 0);
  bridge.add(br);
  bridge.position.set(0.5, 0.0, 0.25);
  bridge.rotation.y = Math.PI / 2;
  g.add(bridge);
  // dây đèn lồng
  const colors = [0xff3b2f, 0xffb21a, 0xff6a1a, 0xd61f5a, 0xffd23a];
  const lanterns = [];
  for (let i = 0; i < 9; i++) {
    const l = lanternMesh(colors[i % colors.length]);
    const x = -0.75 + i * 0.16;
    l.position.set(x, 0.5 - Math.sin((i / 8) * Math.PI) * 0.08, -0.18);
    lanterns.push(l);
    g.add(l);
  }
  const b = boat(0x3a2a1a, 0.42);
  const bl = lanternMesh(0xff3b2f);
  bl.position.set(0, 0.2, 0);
  b.add(bl);
  b.position.set(-0.35, 0.02, 0.45);
  g.add(b);
  g.userData.tick = (t) => {
    lanterns.forEach((l, i) => { l.material.emissiveIntensity = 0.7 + Math.sin(t * 3 + i) * 0.3; });
    b.position.x = -0.35 + Math.sin(t * 0.3) * 0.35;
  };
  return g;
}

function hipRoof(w, h, d, material, x, y, z) {
  // xoay trước trong geometry để phép scale kéo dãn theo trục x/z mà không làm méo mái
  const geo = new THREE.ConeGeometry(0.5, h, 4);
  geo.rotateY(Math.PI / 4);
  const r = new THREE.Mesh(geo, material);
  r.scale.set(w / 0.707, 1, d / 0.707);
  const g = new THREE.Group();
  r.position.y = h / 2;
  r.castShadow = true;
  g.add(r);
  g.position.set(x, y, z);
  return g;
}

function hueCitadel() {
  const g = new THREE.Group();
  g.add(water(1.75, 0.35, 0, 0.6, 0.02, 0x4d8a78));
  g.add(box(1.75, 0.04, 1.2, mat(C.grass), 0, 0, -0.18));
  const stone = mat(0x77736a, { roughness: 0.95 });
  g.add(box(1.4, 0.36, 0.5, stone, 0, 0.04, -0.2));
  g.add(box(0.3, 0.36, 0.4, stone, -0.55, 0.04, 0.2));
  g.add(box(0.3, 0.36, 0.4, stone, 0.55, 0.04, 0.2));
  for (const x of [-0.25, 0, 0.25]) g.add(box(0.13, 0.22, 0.02, mat(0x221a14), x, 0.04, 0.055));
  // Lầu Ngũ Phụng
  g.add(box(1.0, 0.04, 0.4, mat(0xb8a98a), 0, 0.4, -0.2));
  for (const x of [-0.4, -0.2, 0, 0.2, 0.4]) {
    g.add(cyl(0.018, 0.018, 0.2, mat(C.red), x, 0.44, -0.05, 6));
    g.add(cyl(0.018, 0.018, 0.2, mat(C.red), x, 0.44, -0.35, 6));
  }
  const yellowTile = mat(0xe8b82a, { roughness: 0.5 });
  g.add(hipRoof(1.1, 0.16, 0.5, yellowTile, 0, 0.62, -0.2));
  g.add(box(0.5, 0.12, 0.25, mat(0xa0302a), 0, 0.7, -0.2));
  g.add(hipRoof(0.65, 0.16, 0.36, yellowTile, 0, 0.82, -0.2));
  for (const x of [-0.55, 0.55]) g.add(hipRoof(0.28, 0.12, 0.28, yellowTile, x, 0.4, 0.2));
  return g;
}

// ---------------------------------------------------------------- Đà Nẵng – Quảng Bình

function dragonBridge() {
  const g = new THREE.Group();
  g.add(water(1.75, 1.55, 0, 0, 0.02, 0x3a8fb8));
  g.add(box(1.75, 0.05, 0.28, mat(0x9a9a9a), 0, 0.24, 0));
  for (const x of [-0.6, 0, 0.6]) g.add(box(0.08, 0.24, 0.2, mat(0x8a8a8a), x, 0, 0));
  const pts = [];
  for (let i = 0; i <= 24; i++) {
    const x = -0.85 + (i / 24) * 1.5;
    pts.push(new THREE.Vector3(x, 0.32 + Math.abs(Math.sin((i / 24) * Math.PI * 2.5)) * 0.3, 0));
  }
  const curve = new THREE.CatmullRomCurve3(pts);
  const gold = mat(0xf0b72a, { metalness: 0.6, roughness: 0.35 });
  const body = new THREE.Mesh(new THREE.TubeGeometry(curve, 80, 0.045, 8, false), gold);
  body.castShadow = true;
  g.add(body);
  const end = pts[pts.length - 1];
  const head = new THREE.Group();
  head.add(box(0.16, 0.1, 0.1, gold, 0, -0.05, 0));
  const snout = cone(0.05, 0.1, gold, 0.11, -0.05, 0, 6);
  snout.rotation.z = -Math.PI / 2;
  head.add(snout);
  head.add(sphere(0.015, mat(C.red, { emissive: 0xff0000 }), 0.05, 0.03, 0.05, 6, 4));
  head.add(sphere(0.015, mat(C.red, { emissive: 0xff0000 }), 0.05, 0.03, -0.05, 6, 4));
  for (const z of [-0.035, 0.035]) {
    const horn = cone(0.015, 0.09, gold, -0.04, 0.03, z, 5);
    horn.rotation.z = 0.7;
    head.add(horn);
  }
  head.position.copy(end).add(new THREE.Vector3(0.06, 0.02, 0));
  g.add(head);
  const fire = new THREE.Mesh(new THREE.ConeGeometry(0.06, 0.3, 8),
    new THREE.MeshStandardMaterial({ color: 0xff7a1a, emissive: 0xff5a00, emissiveIntensity: 1.5, transparent: true, opacity: 0.85 }));
  fire.rotation.z = Math.PI / 2;
  fire.position.set(0.3, -0.05, 0);
  head.add(fire);
  g.userData.tick = (t) => {
    const cycle = t % 7;
    const on = cycle > 5.5;
    fire.visible = on;
    if (on) fire.scale.set(1, 0.8 + Math.random() * 0.5, 1);
  };
  return g;
}

function hand(side) {
  const g = new THREE.Group();
  const stoneMoss = mat(0x7d8073, { flat: true, roughness: 1 });
  const arm = cyl(0.07, 0.1, 0.55, stoneMoss, 0, 0, 0, 7);
  g.add(arm);
  const palmMesh = box(0.2, 0.06, 0.24, stoneMoss, 0, 0.55, 0);
  g.add(palmMesh);
  for (let i = 0; i < 4; i++) {
    const f = cyl(0.022, 0.026, 0.2, stoneMoss, -0.075 + i * 0.05, 0.58, 0.1, 6);
    f.rotation.x = 0.5;
    g.add(f);
  }
  const thumb = cyl(0.025, 0.03, 0.14, stoneMoss, side * 0.12, 0.57, -0.02, 6);
  thumb.rotation.z = -side * 0.8;
  g.add(thumb);
  return g;
}

function goldenBridge() {
  const g = new THREE.Group();
  const hill = sphere(0.9, mat(0x3f7f3a, { flat: true }), 0, -0.2, -0.45, 10, 8);
  hill.scale.set(1, 1, 0.55);
  g.add(hill);
  g.add(ground(0x4f8f3f));
  const deck = new THREE.Mesh(new THREE.TorusGeometry(0.62, 0.05, 6, 32, Math.PI), mat(0xe8b52a, { metalness: 0.7, roughness: 0.3 }));
  deck.rotation.x = Math.PI / 2;
  deck.scale.set(1, 1, 0.45);
  deck.position.set(0, 0.74, -0.1);
  deck.castShadow = true;
  g.add(deck);
  const rail = new THREE.Mesh(new THREE.TorusGeometry(0.66, 0.008, 4, 32, Math.PI), mat(0xd4a020, { metalness: 0.7 }));
  rail.rotation.x = Math.PI / 2;
  rail.position.set(0, 0.84, -0.1);
  g.add(rail);
  const h1 = hand(-1);
  h1.position.set(-0.2, 0.0, 0.42);
  h1.rotation.y = 0.3;
  const h2 = hand(1);
  h2.position.set(0.2, 0.0, 0.42);
  h2.rotation.y = -0.3;
  g.add(h1, h2);
  g.add(tree(-0.75, 0.55, 1.3, 0x2e6b2e), tree(0.78, 0.5, 1.1, 0x2e6b2e));
  return g;
}

function cave() {
  const g = new THREE.Group();
  g.add(ground(0x4a6a3a));
  const rock = mat(0x5d5a52, { flat: true, roughness: 1 });
  const dome = new THREE.Mesh(new THREE.SphereGeometry(0.85, 9, 6, 0, Math.PI * 2, 0, Math.PI / 2), rock);
  dome.scale.set(1, 0.95, 0.7);
  dome.position.set(0, 0.02, -0.25);
  dome.castShadow = true;
  g.add(dome);
  const arch = new THREE.Mesh(new THREE.TorusGeometry(0.42, 0.13, 5, 12, Math.PI), rock);
  arch.position.set(0, 0.02, 0.28);
  arch.castShadow = true;
  g.add(arch);
  const mouth = new THREE.Mesh(new THREE.CircleGeometry(0.42, 16, 0, Math.PI), mat(0x15120f));
  mouth.position.set(0, 0.03, 0.22);
  g.add(mouth);
  // rừng trong hang + tia nắng
  g.add(tree(-0.15, 0.35, 1.0, 0x3f9a3f), tree(0.15, 0.4, 0.8, 0x3f9a3f));
  const beam = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.22, 0.9, 12, 1, true),
    new THREE.MeshBasicMaterial({ color: 0xfff2b0, transparent: true, opacity: 0.22, depthWrite: false }));
  beam.position.set(0.1, 0.5, 0.15);
  g.add(beam);
  const tent = cone(0.07, 0.08, mat(0xff7a1a), 0.32, 0.03, 0.52, 3);
  g.add(tent);
  g.userData.tick = (t) => { beam.material.opacity = 0.16 + Math.sin(t * 1.5) * 0.06; };
  return g;
}

// ---------------------------------------------------------------- Thăng Long

function vanMieu() {
  const g = new THREE.Group();
  g.add(ground(0x6aa04c));
  // giếng Thiên Quang
  g.add(box(0.9, 0.05, 0.42, mat(C.stone), 0, 0.02, 0.48));
  g.add(water(0.8, 0.34, 0, 0.48, 0.06, 0x4d8a9a));
  const plaster = mat(0xe9e2d0);
  for (const [x, z] of [[-0.2, -0.38], [0.2, -0.38], [-0.2, 0.02], [0.2, 0.02]]) g.add(box(0.12, 0.38, 0.12, plaster, x, 0.04, z));
  g.add(box(0.6, 0.04, 0.6, plaster, 0, 0.42, -0.18));
  g.add(pagodaRoof(0.95, 0.14, mat(0x5a3a2a), 0, 0.4, -0.18));
  g.add(box(0.36, 0.26, 0.36, mat(0x9a2f20), 0, 0.5, -0.18));
  const sunMat = mat(0xf2e3b0, { emissive: 0x3a3010 });
  for (const z of [0.005, -0.365]) {
    const sun = new THREE.Mesh(new THREE.CircleGeometry(0.09, 16), sunMat);
    sun.position.set(0, 0.63, -0.18 + (z > 0 ? 0.185 : -0.185));
    if (z < 0) sun.rotation.y = Math.PI;
    g.add(sun);
  }
  g.add(pagodaRoof(0.7, 0.28, mat(0x4a3028), 0, 0.76, -0.18));
  g.add(cone(0.025, 0.1, mat(C.gold, { metalness: 0.6 }), 0, 1.03, -0.18, 6));
  g.add(tree(-0.7, -0.4, 1.4), tree(0.7, -0.4, 1.4), tree(-0.7, 0.2, 1.1), tree(0.7, 0.2, 1.1));
  return g;
}

function onePillar() {
  const g = new THREE.Group();
  g.add(ground(0x6aa04c));
  g.add(box(1.3, 0.06, 1.1, mat(0xb8b0a0), 0, 0.02, 0));
  g.add(water(1.16, 0.96, 0, 0, 0.06, 0x3f8f8a));
  g.add(cyl(0.065, 0.075, 0.5, mat(0xb0a898), 0, 0.06, 0, 10));
  g.add(box(0.5, 0.04, 0.5, mat(0x6b3a22), 0, 0.54, 0));
  for (let k = 0; k < 4; k++) {
    const brace = box(0.03, 0.2, 0.03, mat(0x6b3a22), 0, 0.38, 0);
    brace.position.x = Math.cos((k * Math.PI) / 2) * 0.1;
    brace.position.z = Math.sin((k * Math.PI) / 2) * 0.1;
    brace.rotation.z = -Math.cos((k * Math.PI) / 2) * 0.6;
    brace.rotation.x = Math.sin((k * Math.PI) / 2) * 0.6;
    g.add(brace);
  }
  g.add(box(0.34, 0.22, 0.34, mat(0x8a2f1f), 0, 0.58, 0));
  g.add(pagodaRoof(0.7, 0.26, mat(0x3e3430), 0, 0.8, 0));
  g.add(sphere(0.03, mat(C.gold, { metalness: 0.6 }), 0, 1.08, 0, 8, 6));
  // cầu thang
  const stairs = box(0.16, 0.03, 0.55, mat(0x8a7a6a), 0, 0.3, 0.42);
  stairs.rotation.x = 0.85;
  g.add(stairs);
  const r = rand(13);
  for (let i = 0; i < 7; i++) {
    const a = r() * Math.PI * 2;
    const rr = 0.3 + r() * 0.2;
    const pad = cyl(0.06, 0.06, 0.012, mat(0x3f8f3a), Math.cos(a) * rr, 0.08, Math.sin(a) * rr * 0.8, 10);
    g.add(pad);
    if (i % 2 === 0) g.add(cone(0.025, 0.05, mat(0xf28cb0), Math.cos(a) * rr, 0.09, Math.sin(a) * rr * 0.8, 6));
  }
  return g;
}

function turtleTower() {
  const g = new THREE.Group();
  g.add(water(1.75, 1.55, 0, 0, 0.02, 0x4f9a86));
  const islet = cyl(0.42, 0.46, 0.06, mat(0x5a9a46), 0, 0.02, -0.1, 18);
  islet.scale.z = 0.75;
  g.add(islet);
  const stone = mat(0xd9d2bf, { roughness: 0.9 });
  const dark = mat(0x3a3530);
  g.add(box(0.34, 0.22, 0.26, stone, 0, 0.08, -0.1));
  g.add(box(0.26, 0.15, 0.2, stone, 0, 0.3, -0.1));
  g.add(box(0.18, 0.12, 0.14, stone, 0, 0.45, -0.1));
  g.add(sphere(0.06, stone, 0, 0.6, -0.1, 10, 8));
  for (const x of [-0.09, 0, 0.09]) g.add(box(0.05, 0.1, 0.01, dark, x, 0.1, 0.035));
  for (const x of [-0.06, 0.06]) g.add(box(0.04, 0.07, 0.01, dark, x, 0.33, 0.005));
  g.add(box(0.04, 0.06, 0.01, dark, 0, 0.47, -0.025));
  g.add(tree(0.28, -0.25, 0.9, 0x3f8f3a, 0.06), tree(-0.3, -0.2, 0.8, 0x3f8f3a, 0.06));
  // cầu Thê Húc
  const bridge = new THREE.Mesh(new THREE.TorusGeometry(0.45, 0.04, 6, 24, Math.PI), mat(0xc8202a));
  bridge.scale.set(1, 0.35, 1.5);
  bridge.rotation.y = Math.PI / 2;
  bridge.position.set(-0.62, 0.02, 0.15);
  bridge.castShadow = true;
  g.add(bridge);
  g.add(box(0.15, 0.12, 0.15, mat(0xc8202a), -0.62, 0.02, -0.55));
  const roof = hipRoof(0.22, 0.08, 0.22, mat(0x3e3430), -0.62, 0.14, -0.55);
  g.add(roof);
  return g;
}

// ---------------------------------------------------------------- Biểu tượng quốc gia

function skyscraper() {
  const g = new THREE.Group();
  g.add(box(1.75, 0.04, 0.6, mat(0x3a8fb8, { roughness: 0.2 }), 0, 0, 0.47));
  g.add(box(1.75, 0.05, 1.0, mat(0xa0a4a8), 0, 0, -0.3));
  const winTex = canvasTexture(64, 256, (ctx, w, h) => {
    ctx.fillStyle = '#1d3550';
    ctx.fillRect(0, 0, w, h);
    for (let y = 2; y < h; y += 6) {
      for (let x = 2; x < w; x += 8) {
        ctx.fillStyle = Math.random() > 0.55 ? '#ffe7a8' : '#3d6688';
        ctx.fillRect(x, y, 6, 3);
      }
    }
  });
  const glass = new THREE.MeshStandardMaterial({
    color: 0x9cc4e4, metalness: 0.55, roughness: 0.15, emissiveMap: winTex, emissive: 0xffffff, emissiveIntensity: 0.35,
  });
  const parts = [[0, 0, 2.6], [0.17, 0.0, 2.25], [-0.17, 0.0, 2.05], [0, 0.17, 1.85], [0, -0.17, 2.4], [0.17, 0.17, 1.55], [-0.17, -0.17, 1.7], [0.17, -0.17, 1.95], [-0.17, 0.17, 1.35]];
  for (const [x, z, h] of parts) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(0.17, h, 0.17), glass);
    m.position.set(x, 0.05 + h / 2, z - 0.3);
    m.castShadow = true;
    g.add(m);
  }
  g.add(cyl(0.006, 0.012, 0.25, mat(0xdddddd, { metalness: 0.8 }), 0, 2.65, -0.3, 6));
  const beacon = sphere(0.02, new THREE.MeshBasicMaterial({ color: 0xff2020 }), 0, 2.92, -0.3, 6, 4);
  g.add(beacon);
  g.add(box(0.5, 0.18, 0.4, mat(0xcfd6dc), -0.6, 0.05, -0.45), box(0.4, 0.28, 0.35, mat(0xbfc6cc), 0.6, 0.05, -0.5));
  g.add(palm(-0.55, 0.05, 0.8), palm(0.55, 0.05, 0.8));
  g.userData.tick = (t) => { beacon.visible = Math.sin(t * 4) > 0; };
  return g;
}

function karst(x, z, h, r, seed) {
  const rr = rand(seed);
  const geo = new THREE.CylinderGeometry(r * 0.55, r, h, 7, 3);
  const p = geo.attributes.position;
  for (let i = 0; i < p.count; i++) {
    p.setX(i, p.getX(i) * (0.85 + rr() * 0.3));
    p.setZ(i, p.getZ(i) * (0.85 + rr() * 0.3));
  }
  geo.computeVertexNormals();
  const m = new THREE.Mesh(geo, mat(0x5f7f4a, { flat: true, roughness: 1 }));
  m.position.set(x, h / 2, z);
  m.castShadow = true;
  m.receiveShadow = true;
  const g = new THREE.Group();
  g.add(m);
  const top = sphere(r * 0.6, mat(0x4f7a3a, { flat: true }), x, h, z, 7, 4);
  top.scale.y = 0.5;
  g.add(top);
  const base = cyl(r * 1.05, r * 1.1, 0.06, mat(0x8a8670, { flat: true }), x, 0, z, 7);
  g.add(base);
  return g;
}

export function junkBoat() {
  const b = boat(0x5a3418, 0.5);
  const sailMat = mat(0xc8552a, { side: THREE.DoubleSide, roughness: 0.9 });
  for (const [x, h] of [[-0.14, 0.32], [0.0, 0.4], [0.14, 0.28]]) {
    b.add(cyl(0.007, 0.007, h + 0.05, mat(0x3a2410), x, 0.1, 0, 4));
    const sail = new THREE.Mesh(new THREE.PlaneGeometry(0.13, h), sailMat);
    sail.position.set(x + 0.06, 0.12 + h / 2, 0);
    sail.castShadow = true;
    b.add(sail);
  }
  return b;
}

function halong() {
  const g = new THREE.Group();
  g.add(water(1.75, 1.55, 0, 0, 0.02, 0x2f9a9a));
  g.add(karst(-0.62, -0.45, 0.85, 0.22, 1), karst(0.55, -0.5, 1.05, 0.25, 2), karst(-0.05, -0.62, 0.6, 0.18, 3),
    karst(0.72, 0.35, 0.5, 0.15, 4), karst(-0.72, 0.38, 0.65, 0.16, 5));
  const b = junkBoat();
  g.add(b);
  g.userData.tick = (t) => {
    const a = t * 0.25;
    b.position.set(Math.cos(a) * 0.38, 0.03 + Math.sin(t * 1.4) * 0.01, 0.05 + Math.sin(a) * 0.28);
    b.rotation.y = -a - Math.PI / 2;
  };
  return g;
}

// ---------------------------------------------------------------- Ô đặc biệt

function goGate() {
  const g = new THREE.Group();
  const red = mat(0xc62828);
  g.add(box(1.5, 0.04, 1.5, mat(0xe8d9b5), 0, 0, 0));
  for (const x of [-0.5, 0.5]) {
    g.add(cyl(0.07, 0.08, 0.9, red, x, 0.04, 0, 10));
    g.add(box(0.2, 0.06, 0.2, mat(C.gold, { metalness: 0.6 }), x, 0.04, 0));
  }
  g.add(box(1.3, 0.1, 0.12, red, 0, 0.92, 0));
  g.add(box(1.0, 0.18, 0.04, mat(C.gold, { metalness: 0.5, roughness: 0.3 }), 0, 0.72, 0.03));
  g.add(hipRoof(1.4, 0.15, 0.3, mat(0x3e3430), 0, 1.02, 0));
  for (const x of [-0.5, 0.5]) {
    const l = lanternMesh(0xff3b2f);
    l.position.set(x * 0.6, 0.6, 0.08);
    g.add(l);
  }
  // xấp tiền vàng
  for (let i = 0; i < 5; i++) g.add(cyl(0.12, 0.12, 0.04, mat(C.gold, { metalness: 0.7, roughness: 0.3 }), 0.45, 0.04 + i * 0.045, 0.5, 16));
  return g;
}

function envelope() {
  const g = new THREE.Group();
  const tex = canvasTexture(128, 180, (ctx, w, h) => {
    ctx.fillStyle = '#c8102e';
    ctx.fillRect(0, 0, w, h);
    ctx.strokeStyle = '#f5c542';
    ctx.lineWidth = 6;
    ctx.strokeRect(8, 8, w - 16, h - 16);
    ctx.fillStyle = '#f5c542';
    ctx.beginPath();
    ctx.arc(w / 2, h * 0.55, 34, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#c8102e';
    ctx.font = 'bold 30px "Be Vietnam Pro", sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('LỘC', w / 2, h * 0.56);
  });
  const env = new THREE.Mesh(new THREE.BoxGeometry(0.42, 0.6, 0.04), [
    mat(0xc8102e), mat(0xc8102e), mat(0xc8102e), mat(0xc8102e),
    new THREE.MeshStandardMaterial({ map: tex, roughness: 0.5 }), new THREE.MeshStandardMaterial({ map: tex, roughness: 0.5 }),
  ]);
  env.position.y = 0.45;
  env.castShadow = true;
  g.add(env);
  g.userData.tick = (t) => {
    env.rotation.y = Math.sin(t * 0.8) * 0.6;
    env.position.y = 0.45 + Math.sin(t * 1.6) * 0.04;
  };
  return g;
}

function question() {
  const g = new THREE.Group();
  const m = mat(0xff8f1f, { roughness: 0.3, metalness: 0.2 });
  const q = new THREE.Group();
  const hook = new THREE.Mesh(new THREE.TorusGeometry(0.16, 0.05, 10, 20, Math.PI * 1.4), m);
  hook.rotation.z = -Math.PI * 0.4;
  hook.position.y = 0.32;
  const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 0.14, 10), m);
  stem.position.set(0, 0.12, 0);
  const dot = new THREE.Mesh(new THREE.SphereGeometry(0.06, 12, 10), m);
  dot.position.y = -0.06;
  q.add(hook, stem, dot);
  enableShadows(q);
  q.position.y = 0.42;
  g.add(q);
  g.userData.tick = (t) => {
    q.rotation.y = t * 1.2;
    q.position.y = 0.42 + Math.sin(t * 2) * 0.04;
  };
  return g;
}

function coins() {
  const g = new THREE.Group();
  const gold = mat(C.gold, { metalness: 0.75, roughness: 0.3 });
  const stacks = [[-0.25, 0, 7], [0.1, -0.15, 5], [0.3, 0.2, 3]];
  for (const [x, z, n] of stacks) for (let i = 0; i < n; i++) g.add(cyl(0.13, 0.13, 0.045, gold, x, i * 0.05, z, 18));
  const bill = box(0.42, 0.02, 0.22, mat(0x7fbf7f), -0.1, 0, 0.35);
  bill.rotation.y = 0.3;
  g.add(bill);
  return g;
}

function rails(len) {
  const g = new THREE.Group();
  for (let x = -len / 2; x <= len / 2; x += 0.12) g.add(box(0.05, 0.02, 0.3, mat(0x6b5030), x, 0.04, 0));
  for (const z of [-0.09, 0.09]) g.add(box(len, 0.025, 0.02, mat(0x888888, { metalness: 0.7 }), 0, 0.06, z));
  return g;
}

function train() {
  const g = new THREE.Group();
  g.add(box(1.75, 0.04, 1.55, mat(0xb8b0a0), 0, 0, 0));
  const r = rails(1.7);
  r.position.z = 0.15;
  g.add(r);
  const loco = new THREE.Group();
  loco.add(box(0.55, 0.24, 0.24, mat(0x1d4e89), 0, 0.12, 0));
  loco.add(box(0.55, 0.04, 0.245, mat(0xf2c94c), 0, 0.2, 0));
  loco.add(box(0.18, 0.1, 0.22, mat(0x1d4e89), -0.15, 0.36, 0));
  loco.add(box(0.06, 0.08, 0.2, mat(0xbfe3ff, { emissive: 0x203040 }), 0.28, 0.25, 0));
  for (const x of [-0.18, 0, 0.18]) for (const z of [-0.11, 0.11]) {
    const w = cyl(0.05, 0.05, 0.03, mat(C.black), x, 0, z, 10);
    w.rotation.x = Math.PI / 2;
    w.position.y = 0.11;
    loco.add(w);
  }
  const car = new THREE.Group();
  car.add(box(0.6, 0.26, 0.24, mat(0x2d6fb5), 0, 0.12, 0));
  for (let i = 0; i < 4; i++) car.add(box(0.08, 0.06, 0.25, mat(0xbfe3ff, { emissive: 0x203040 }), -0.21 + i * 0.14, 0.25, 0));
  car.position.x = -0.62;
  loco.add(car);
  loco.position.set(0.25, 0.04, 0.15);
  g.add(loco);
  // nhà ga
  g.add(box(1.2, 0.35, 0.35, mat(0xe9d8b0), 0, 0.04, -0.5));
  g.add(hipRoof(1.3, 0.14, 0.42, mat(0x8a3b24), 0, 0.39, -0.5));
  g.add(box(0.2, 0.1, 0.02, mat(0x355f8a), 0, 0.28, -0.32));
  g.userData.tick = (t) => {
    loco.position.x = 0.25 + Math.sin(t * 0.3) * 0.2;
  };
  return g;
}

function containerShip() {
  const g = new THREE.Group();
  g.add(water(1.75, 0.9, 0, 0.3, 0.02, 0x2f7fa8));
  g.add(box(1.75, 0.18, 0.6, mat(0x8a8a8a), 0, 0, -0.47));
  const hull = new THREE.Group();
  hull.add(box(1.2, 0.16, 0.3, mat(0x7a1f1f), 0, 0, 0));
  const bow = cone(0.15, 0.25, mat(0x7a1f1f), 0.72, 0.08, 0, 4);
  bow.rotation.z = -Math.PI / 2;
  bow.position.y = 0.08;
  bow.scale.set(1, 1, 0.75);
  hull.add(bow);
  hull.add(box(0.18, 0.22, 0.24, mat(0xf2f2f2), -0.5, 0.16, 0));
  const cc = [0xd63a2a, 0x2a6fd6, 0x2a9a4a, 0xf2a21a, 0x8a4ad6, 0xe8e8e8];
  let k = 0;
  for (let x = -0.3; x <= 0.5; x += 0.17) {
    for (let y = 0; y < 2; y++) {
      for (const z of [-0.07, 0.07]) hull.add(box(0.15, 0.08, 0.12, mat(cc[k++ % cc.length]), x, 0.16 + y * 0.085, z));
    }
  }
  hull.position.set(-0.05, 0.03, 0.3);
  g.add(hull);
  // cẩu bờ
  const yellow = mat(0xf2b51a);
  for (const x of [-0.4, 0.4]) {
    g.add(box(0.05, 0.75, 0.05, yellow, x, 0.18, -0.55), box(0.05, 0.75, 0.05, yellow, x, 0.18, -0.35));
    g.add(box(0.06, 0.06, 0.9, yellow, x, 0.93, -0.2));
  }
  g.userData.tick = (t) => {
    hull.position.y = 0.03 + Math.sin(t * 1.2) * 0.01;
    hull.rotation.x = Math.sin(t) * 0.02;
  };
  return g;
}

function airplane(tailColor, stripe) {
  const g = new THREE.Group();
  const white = mat(0xf4f4f4, { roughness: 0.4 });
  const body = new THREE.Mesh(new THREE.CapsuleGeometry(0.075, 0.85, 6, 12), white);
  body.rotation.z = Math.PI / 2;
  body.castShadow = true;
  g.add(body);
  g.add(box(0.95, 0.025, 0.155, mat(stripe), 0, 0.0, 0));
  const wing = box(0.24, 0.015, 1.05, white, 0.0, -0.02, 0);
  g.add(wing);
  g.add(box(0.12, 0.012, 0.36, white, -0.42, 0.02, 0));
  g.add(box(0.16, 0.2, 0.015, mat(tailColor), -0.44, 0.07, 0));
  for (const z of [-0.22, 0.22]) {
    const eng = cyl(0.035, 0.035, 0.12, mat(0xcccccc), 0.04, -0.08, z, 10);
    eng.rotation.z = Math.PI / 2;
    eng.position.y = -0.06;
    g.add(eng);
  }
  return g;
}

function plane(tail = 0x1a5fa8, stripe = 0xf2c94c) {
  const g = new THREE.Group();
  const runway = canvasTexture(256, 128, (ctx, w, h) => {
    ctx.fillStyle = '#4a4d52';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#ffffff';
    for (let x = 10; x < w; x += 40) ctx.fillRect(x, h / 2 - 3, 22, 6);
  });
  g.add(box(1.75, 0.04, 1.55, mat(0x7aaa5a), 0, 0, 0));
  const rw = new THREE.Mesh(new THREE.BoxGeometry(1.75, 0.02, 0.55), new THREE.MeshStandardMaterial({ map: runway, roughness: 0.9 }));
  rw.position.set(0, 0.05, 0.25);
  rw.receiveShadow = true;
  g.add(rw);
  const ap = airplane(tail, stripe);
  ap.position.set(0, 0.22, 0.25);
  ap.rotation.y = 0.15;
  g.add(ap);
  // đài kiểm soát
  g.add(cyl(0.07, 0.09, 0.7, mat(0xdddddd), -0.65, 0.04, -0.5, 10));
  g.add(cyl(0.14, 0.1, 0.14, mat(0x6fa8d8, { metalness: 0.4, roughness: 0.2 }), -0.65, 0.74, -0.5, 10));
  g.add(box(0.7, 0.2, 0.3, mat(0xd8dde2), 0.35, 0.04, -0.5));
  g.userData.tick = (t) => {
    ap.position.y = 0.22 + Math.max(0, Math.sin(t * 0.5)) * 0.25;
    ap.rotation.z = Math.max(0, Math.sin(t * 0.5)) * 0.12;
  };
  return g;
}

function dam() {
  const g = new THREE.Group();
  g.add(box(1.75, 0.45, 0.6, mat(0x5f7f4a, { flat: true }), 0, 0, -0.48));
  g.add(water(1.75, 0.6, 0, -0.48, 0.45, 0x2f7fa8));
  g.add(box(1.75, 0.5, 0.14, mat(0xb8b4aa), 0, 0, -0.12));
  g.add(water(1.75, 0.95, 0, 0.32, 0.02, 0x3a8fb8));
  const streams = [];
  for (const x of [-0.45, -0.15, 0.15, 0.45]) {
    g.add(box(0.14, 0.12, 0.02, mat(0x333333), x, 0.28, -0.04));
    const s = new THREE.Mesh(new THREE.PlaneGeometry(0.12, 0.32), new THREE.MeshStandardMaterial({ color: 0xe8f6ff, transparent: true, opacity: 0.8 }));
    s.position.set(x, 0.18, 0.0);
    s.rotation.x = -0.35;
    streams.push(s);
    g.add(s);
  }
  // cột điện
  const steel = mat(0x9a9a9a, { metalness: 0.6 });
  for (const x of [-0.65, 0.65]) {
    for (const dx of [-0.05, 0.05]) {
      const leg = box(0.018, 0.85, 0.018, steel, x + dx, 0.04, 0.6);
      leg.rotation.z = -dx * 1.2;
      g.add(leg);
    }
    g.add(box(0.34, 0.02, 0.02, steel, x, 0.72, 0.6), box(0.24, 0.02, 0.02, steel, x, 0.55, 0.6));
  }
  g.add(box(1.3, 0.004, 0.004, mat(0x333333), 0, 0.73, 0.6));
  g.userData.tick = (t) => streams.forEach((s, i) => { s.material.opacity = 0.6 + Math.sin(t * 8 + i) * 0.2; });
  return g;
}

function turbine() {
  const g = new THREE.Group();
  const white = mat(0xf4f4f4, { roughness: 0.4 });
  g.add(cyl(0.018, 0.035, 1.1, white, 0, 0, 0, 10));
  g.add(box(0.08, 0.07, 0.16, white, 0, 1.08, -0.02));
  const rotor = new THREE.Group();
  rotor.add(sphere(0.035, white, 0, 0, 0, 8, 6));
  for (let k = 0; k < 3; k++) {
    const blade = box(0.035, 0.42, 0.012, white, 0, 0, 0);
    blade.geometry.translate(0, 0, 0);
    const pivot = new THREE.Group();
    blade.position.y = 0.0;
    pivot.add(blade);
    pivot.rotation.z = (k * Math.PI * 2) / 3;
    rotor.add(pivot);
  }
  rotor.position.set(0, 1.115, 0.08);
  g.add(rotor);
  g.userData.rotor = rotor;
  return g;
}

function windTurbine() {
  const g = new THREE.Group();
  g.add(water(1.75, 1.55, 0, 0, 0.02, 0x5a9ab0));
  const turbines = [];
  for (const [x, z, s] of [[-0.4, -0.35, 1], [0.35, -0.1, 0.9], [-0.05, 0.4, 0.75]]) {
    const t = turbine();
    t.position.set(x, 0, z);
    t.scale.setScalar(s);
    turbines.push(t);
    g.add(t);
  }
  g.add(box(1.2, 0.03, 0.06, mat(0x8a8a8a), 0, 0.12, 0.6));
  g.userData.tick = (time, dt) => turbines.forEach((t, i) => { t.userData.rotor.rotation.z -= dt * (1.6 + i * 0.3); });
  return g;
}

function jail() {
  const g = new THREE.Group();
  g.add(box(2.4, 0.04, 2.4, mat(0xd8d0bf), 0, 0, 0));
  // nhà giữ ở góc trong, sân có song sắt phía trước (quân bị giữ đứng trong sân)
  const wall = mat(0x8f9aa3);
  g.add(box(0.8, 0.6, 0.8, wall, -0.75, 0.04, -0.75));
  g.add(box(0.9, 0.06, 0.9, mat(0x5a646c), -0.75, 0.64, -0.75));
  g.add(box(0.14, 0.24, 0.02, mat(0x333a40), -0.75, 0.04, -0.34));
  g.add(box(1.5, 0.01, 1.5, mat(0xa8a090), -0.4, 0.04, -0.4));
  const steel = mat(0x333333, { metalness: 0.8, roughness: 0.4 });
  for (let i = 0; i <= 10; i++) {
    const v = -1.15 + i * 0.135;
    g.add(cyl(0.015, 0.015, 0.45, steel, v, 0.05, 0.35, 6));
    g.add(cyl(0.015, 0.015, 0.45, steel, 0.35, 0.05, v, 6));
  }
  g.add(box(1.5, 0.03, 0.03, steel, -0.4, 0.5, 0.35), box(0.03, 0.03, 1.5, steel, 0.35, 0.5, -0.4));
  const red = sphere(0.05, new THREE.MeshBasicMaterial({ color: 0xff2a2a }), -0.9, 0.75, -0.75, 8, 6);
  const blue = sphere(0.05, new THREE.MeshBasicMaterial({ color: 0x2a6aff }), -0.6, 0.75, -0.75, 8, 6);
  g.add(red, blue);
  g.userData.tick = (t) => {
    const on = Math.sin(t * 6) > 0;
    red.visible = on;
    blue.visible = !on;
  };
  return g;
}

function pier() {
  const g = new THREE.Group();
  g.add(water(2.4, 2.4, 0, 0, 0.02, 0x7a8a5a));
  g.add(box(2.4, 0.1, 0.9, mat(0xc9bfa8), 0, 0, -0.75));
  g.add(box(0.5, 0.05, 1.3, mat(0x9a7a4a), 0.3, 0.14, 0.3));
  for (const z of [-0.2, 0.3, 0.8]) for (const x of [0.1, 0.5]) g.add(cyl(0.025, 0.025, 0.16, mat(0x5a4020), x, 0, z, 6));
  for (const x of [-0.8, -0.2, 0.6]) {
    g.add(cyl(0.012, 0.015, 0.55, mat(0x2a2a2a), x, 0.1, -0.4, 6));
    g.add(sphere(0.045, new THREE.MeshStandardMaterial({ color: 0xfff0b0, emissive: 0xffd060, emissiveIntensity: 1 }), x, 0.67, -0.4, 10, 8));
  }
  const b = boat(0x2a5a7a, 0.6);
  b.add(box(0.22, 0.08, 0.14, mat(0xc0402a), -0.05, 0.12, 0));
  b.position.set(-0.4, 0.02, 0.5);
  g.add(b);
  g.add(palm(-0.9, -0.85, 1.3), palm(0.95, -0.8, 1.1));
  g.userData.tick = (t) => { b.position.y = 0.02 + Math.sin(t * 1.5) * 0.015; };
  return g;
}

function whistle() {
  const g = new THREE.Group();
  g.add(box(2.4, 0.04, 2.4, mat(0xd8d0bf), 0, 0, 0));
  g.add(cyl(0.035, 0.04, 1.0, mat(0x333333), 0, 0.04, -0.2, 8));
  const head = box(0.2, 0.5, 0.16, mat(0x222222), 0, 0.85, -0.2);
  g.add(head);
  const lights = [0xff2a2a, 0xffc21a, 0x2ad14a].map((c, i) => {
    const l = sphere(0.055, new THREE.MeshBasicMaterial({ color: c }), 0, 1.25 - i * 0.15, -0.115, 10, 8);
    g.add(l);
    return l;
  });
  for (let i = 0; i < 6; i++) g.add(box(0.2, 0.08, 0.08, mat(i % 2 ? 0xffffff : 0xd62828), -0.65 + i * 0.2, 0.4, 0.45));
  g.add(cyl(0.04, 0.05, 0.45, mat(0x555555), -0.8, 0.04, 0.45, 8), cyl(0.04, 0.05, 0.45, mat(0x555555), 0.45, 0.04, 0.45, 8));
  g.add(cone(0.1, 0.25, mat(0xff6a1a), 0.6, 0.04, -0.4, 12), cone(0.1, 0.25, mat(0xff6a1a), -0.6, 0.04, -0.5, 12));
  g.userData.tick = (t) => {
    const phase = Math.floor(t / 1.5) % 3;
    lights.forEach((l, i) => { l.material.color.setHex([0xff2a2a, 0xffc21a, 0x2ad14a][i]).multiplyScalar(i === phase ? 1 : 0.15); });
  };
  return g;
}

const BUILDERS = {
  woodenChurch, waterfall, flagpole, terraces, mountain, floatingMarket, mangrove, shipMonument,
  chamTowers, dunes, basalt, mySon, hoiAn, hueCitadel, dragonBridge, goldenBridge, cave,
  vanMieu, onePillar, turtleTower, skyscraper, halong,
  go: goGate, envelope, question, coins, train, ship: containerShip,
  plane: () => plane(0x1a5fa8, 0xf2c94c), plane2: () => plane(0xc8102e, 0x1a5fa8),
  dam, windTurbine, jail, pier, whistle,
};

export function buildLandmark(name) {
  const fn = BUILDERS[name];
  if (!fn) return new THREE.Group();
  return enableShadows(fn());
}

export { karst };
