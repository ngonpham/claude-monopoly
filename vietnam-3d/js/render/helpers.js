// Tiện ích dựng hình low-poly dùng chung cho mô hình công trình, quân cờ, nhà.
import * as THREE from 'three';

const matCache = new Map();

/** Vật liệu MeshStandard được cache theo màu + tuỳ chọn. */
export function mat(color, opts = {}) {
  const key = `${color}|${JSON.stringify(opts)}`;
  if (!matCache.has(key)) {
    matCache.set(key, new THREE.MeshStandardMaterial({
      color,
      roughness: opts.roughness ?? 0.75,
      metalness: opts.metalness ?? 0.0,
      flatShading: opts.flat ?? false,
      transparent: opts.opacity !== undefined,
      opacity: opts.opacity ?? 1,
      emissive: opts.emissive ?? 0x000000,
      emissiveIntensity: opts.emissiveIntensity ?? 1,
      side: opts.side ?? THREE.FrontSide,
    }));
  }
  return matCache.get(key);
}

function place(mesh, x = 0, y = 0, z = 0) {
  mesh.position.set(x, y, z);
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  return mesh;
}

/** Hộp với đáy nằm tại y (thay vì tâm). */
export function box(w, h, d, material, x = 0, y = 0, z = 0) {
  return place(new THREE.Mesh(new THREE.BoxGeometry(w, h, d), material), x, y + h / 2, z);
}

export function cyl(rTop, rBottom, h, material, x = 0, y = 0, z = 0, seg = 16) {
  return place(new THREE.Mesh(new THREE.CylinderGeometry(rTop, rBottom, h, seg), material), x, y + h / 2, z);
}

export function cone(r, h, material, x = 0, y = 0, z = 0, seg = 16) {
  return place(new THREE.Mesh(new THREE.ConeGeometry(r, h, seg), material), x, y + h / 2, z);
}

export function sphere(r, material, x = 0, y = 0, z = 0, wSeg = 16, hSeg = 12) {
  return place(new THREE.Mesh(new THREE.SphereGeometry(r, wSeg, hSeg), material), x, y, z);
}

/** Mái ngói kiểu chữ A (lăng trụ tam giác) chạy dọc trục x. */
export function gableRoof(w, h, d, material, x = 0, y = 0, z = 0) {
  const shape = new THREE.Shape();
  shape.moveTo(-d / 2, 0);
  shape.lineTo(d / 2, 0);
  shape.lineTo(0, h);
  shape.closePath();
  const geo = new THREE.ExtrudeGeometry(shape, { depth: w, bevelEnabled: false });
  geo.translate(0, 0, -w / 2);
  geo.rotateY(Math.PI / 2);
  return place(new THREE.Mesh(geo, material), x, y, z);
}

/** Mái chùa cong: chóp 4 mặt + 4 đầu đao vểnh. */
export function pagodaRoof(size, h, material, x = 0, y = 0, z = 0) {
  const g = new THREE.Group();
  const roof = new THREE.Mesh(new THREE.ConeGeometry(size * 0.75, h, 4), material);
  roof.rotation.y = Math.PI / 4;
  roof.position.y = h / 2;
  roof.castShadow = true;
  g.add(roof);
  for (let k = 0; k < 4; k++) {
    const a = Math.PI / 4 + (k * Math.PI) / 2;
    const tip = new THREE.Mesh(new THREE.ConeGeometry(0.035, size * 0.28, 5), material);
    tip.position.set(Math.cos(a) * size * 0.56, h * 0.12, Math.sin(a) * size * 0.56);
    tip.rotation.z = -Math.cos(a) * 1.0;
    tip.rotation.x = Math.sin(a) * 1.0;
    g.add(tip);
  }
  g.position.set(x, y, z);
  return g;
}

/** Tấm nước phẳng. */
export function water(w, d, x = 0, z = 0, y = 0.02, color = 0x3aa0c8) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, 0.04, d),
    mat(color, { roughness: 0.15, metalness: 0.2, opacity: 0.88 }));
  m.position.set(x, y, z);
  m.receiveShadow = true;
  return m;
}

/** Cây tán tròn đơn giản. */
export function tree(x, z, s = 1, color = 0x2f8f46, y = 0) {
  const g = new THREE.Group();
  g.add(cyl(0.025 * s, 0.035 * s, 0.18 * s, mat(0x6b4423), 0, 0, 0, 6));
  const crown = new THREE.Mesh(new THREE.IcosahedronGeometry(0.13 * s, 0), mat(color, { flat: true }));
  crown.position.y = 0.26 * s;
  crown.castShadow = true;
  g.add(crown);
  g.position.set(x, y, z);
  return g;
}

/** Cây dừa. */
export function palm(x, z, s = 1, y = 0) {
  const g = new THREE.Group();
  const trunk = cyl(0.02 * s, 0.035 * s, 0.5 * s, mat(0x8a6a3d), 0, 0, 0, 6);
  trunk.rotation.z = 0.12;
  g.add(trunk);
  for (let k = 0; k < 6; k++) {
    const leaf = new THREE.Mesh(new THREE.ConeGeometry(0.04 * s, 0.3 * s, 4), mat(0x3c9a45, { flat: true }));
    leaf.position.set(0.03 * s, 0.5 * s, 0);
    leaf.rotation.z = Math.PI / 2.4;
    const pivot = new THREE.Group();
    pivot.add(leaf);
    pivot.rotation.y = (k * Math.PI * 2) / 6;
    pivot.position.y = 0;
    g.add(pivot);
  }
  g.position.set(x, y, z);
  return g;
}

/** Texture canvas tiện dụng. */
export function canvasTexture(w, h, draw) {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  draw(c.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 8;
  return t;
}

export function enableShadows(obj) {
  obj.traverse((o) => {
    if (o.isMesh) {
      const transparent = Array.isArray(o.material) ? false : o.material.transparent;
      o.castShadow = !transparent;
      o.receiveShadow = true;
    }
  });
  return obj;
}

export const easeOutCubic = (t) => 1 - (1 - t) ** 3;
export const easeInOutCubic = (t) => (t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2);
export const easeOutBack = (t) => {
  const c1 = 1.70158;
  const c3 = c1 + 1;
  return 1 + c3 * (t - 1) ** 3 + c1 * (t - 1) ** 2;
};

/** Chạy một tween theo requestAnimationFrame, trả về Promise. */
export function tween(duration, onUpdate) {
  return new Promise((resolve) => {
    const start = performance.now();
    const step = (now) => {
      const t = Math.min(1, (now - start) / duration);
      onUpdate(t);
      if (t < 1) requestAnimationFrame(step);
      else resolve();
    };
    requestAnimationFrame(step);
  });
}

export const wait = (ms) => new Promise((r) => setTimeout(r, ms));
