// Quân cờ mang biểu tượng văn hoá Việt: nón lá, đèn lồng, trống đồng, hoa sen, thuyền thúng.
import * as THREE from 'three';
import { mat, cyl, cone, sphere, canvasTexture, enableShadows, tween, easeInOutCubic } from './helpers.js';

export const TOKENS = {
  hat: 'Nón lá',
  lantern: 'Đèn lồng',
  drum: 'Trống đồng',
  lotus: 'Hoa sen',
  basket: 'Thuyền thúng',
};

function base(color) {
  const g = new THREE.Group();
  g.add(cyl(0.22, 0.25, 0.06, mat(color, { roughness: 0.35, metalness: 0.2 }), 0, 0, 0, 24));
  return g;
}

function hat(color) {
  const g = base(color);
  const straw = mat(0xe8d4a0, { roughness: 0.9 });
  const c = cone(0.3, 0.28, straw, 0, 0.1, 0, 28);
  g.add(c);
  for (let i = 1; i <= 3; i++) {
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.3 - i * 0.07, 0.006, 4, 28), mat(0xb89a5a));
    ring.rotation.x = Math.PI / 2;
    ring.position.y = 0.1 + i * 0.07;
    g.add(ring);
  }
  // quai nón theo màu người chơi
  const strap = new THREE.Mesh(new THREE.TorusGeometry(0.16, 0.012, 4, 20, Math.PI), mat(color));
  strap.position.set(0, 0.1, 0);
  strap.rotation.x = Math.PI;
  g.add(strap);
  return g;
}

function lantern(color) {
  const g = base(color);
  const pts = [];
  for (let i = 0; i <= 10; i++) {
    const t = i / 10;
    pts.push(new THREE.Vector2(0.06 + Math.sin(t * Math.PI) * 0.15, t * 0.36));
  }
  const silk = new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 0.55, roughness: 0.6 });
  const body = new THREE.Mesh(new THREE.LatheGeometry(pts, 16), silk);
  body.position.y = 0.14;
  g.add(body);
  const gold = mat(0xd9a520, { metalness: 0.7, roughness: 0.3 });
  g.add(cyl(0.07, 0.07, 0.04, gold, 0, 0.11, 0, 12), cyl(0.07, 0.07, 0.04, gold, 0, 0.5, 0, 12));
  g.add(cyl(0.006, 0.006, 0.12, gold, 0, 0.54, 0, 4));
  for (let k = 0; k < 6; k++) {
    const a = (k / 6) * Math.PI * 2;
    g.add(cyl(0.004, 0.004, 0.1, mat(0xd9a520), Math.cos(a) * 0.05, 0.02, Math.sin(a) * 0.05, 3));
  }
  g.userData.glow = silk;
  return g;
}

function drumTexture() {
  return canvasTexture(256, 256, (ctx, w) => {
    const c = w / 2;
    ctx.fillStyle = '#b8833a';
    ctx.fillRect(0, 0, w, w);
    ctx.fillStyle = '#f2cf7a';
    ctx.beginPath();
    for (let i = 0; i < 28; i++) {
      const a = (i * Math.PI) / 14;
      const r = i % 2 === 0 ? 52 : 18;
      ctx.lineTo(c + Math.cos(a) * r, c + Math.sin(a) * r);
    }
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = '#f2cf7a';
    ctx.lineWidth = 4;
    for (const r of [62, 76, 96, 112, 124]) {
      ctx.beginPath();
      ctx.arc(c, c, r, 0, Math.PI * 2);
      ctx.stroke();
    }
  });
}

function drum(color) {
  const g = base(color);
  const bronze = mat(0xa8752f, { metalness: 0.75, roughness: 0.35 });
  const top = new THREE.MeshStandardMaterial({ map: drumTexture(), metalness: 0.6, roughness: 0.4 });
  const body = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.18, 0.22, 24), [bronze, top, bronze]);
  body.position.y = 0.17;
  g.add(body);
  const waist = cyl(0.2, 0.22, 0.08, bronze, 0, 0.04, 0, 24);
  g.add(waist);
  const band = new THREE.Mesh(new THREE.TorusGeometry(0.205, 0.015, 6, 24), mat(color));
  band.rotation.x = Math.PI / 2;
  band.position.y = 0.2;
  g.add(band);
  return g;
}

function lotus(color) {
  const g = base(color);
  const petal = mat(0xf7a8c4, { roughness: 0.5 });
  const inner = mat(0xfbd1e0, { roughness: 0.5 });
  for (let layer = 0; layer < 2; layer++) {
    const n = layer === 0 ? 8 : 6;
    for (let k = 0; k < n; k++) {
      const a = (k / n) * Math.PI * 2 + layer * 0.3;
      const p = sphere(0.1, layer ? inner : petal, 0, 0, 0, 10, 8);
      p.scale.set(0.45, 1.1, 0.25);
      const pivot = new THREE.Group();
      p.position.set(0, 0.1, 0.09 - layer * 0.03);
      p.rotation.x = -0.5 + layer * 0.25;
      pivot.add(p);
      pivot.rotation.y = a;
      pivot.position.y = 0.08 + layer * 0.04;
      g.add(pivot);
    }
  }
  g.add(cyl(0.05, 0.06, 0.05, mat(0xf2d04a), 0, 0.2, 0, 12));
  const leaf = cyl(0.21, 0.21, 0.02, mat(0x3f9a4a), 0, 0.06, 0, 18);
  g.add(leaf);
  g.add(new THREE.Mesh(new THREE.TorusGeometry(0.21, 0.012, 4, 20), mat(color)).translateY(0.08).rotateX(Math.PI / 2));
  return g;
}

function basket(color) {
  const g = base(color);
  const weave = mat(0x8a6a3a, { roughness: 0.95 });
  const bowl = new THREE.Mesh(new THREE.SphereGeometry(0.24, 20, 10, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2), weave);
  bowl.material.side = THREE.DoubleSide;
  bowl.position.y = 0.3;
  bowl.scale.y = 0.7;
  g.add(bowl);
  const rim = new THREE.Mesh(new THREE.TorusGeometry(0.24, 0.02, 6, 24), mat(color));
  rim.rotation.x = Math.PI / 2;
  rim.position.y = 0.3;
  g.add(rim);
  const oar = cyl(0.01, 0.01, 0.45, mat(0x6b4a2a), 0.1, 0.2, 0, 5);
  oar.rotation.z = 0.5;
  g.add(oar);
  return g;
}

const BUILD = { hat, lantern, drum, lotus, basket };

export class Token {
  constructor(scene, { token, color }) {
    this.scene = scene;
    this.group = enableShadows((BUILD[token] || hat)(color));
    this.group.scale.setScalar(1.35);
    this.color = color;
    // vòng sáng khi đến lượt
    this.ring = new THREE.Mesh(new THREE.RingGeometry(0.34, 0.42, 32),
      new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.0, side: THREE.DoubleSide, depthWrite: false }));
    this.ring.rotation.x = -Math.PI / 2;
    this.ring.position.y = 0.01;
    this.group.add(this.ring);
    scene.add(this.group);
    this.active = false;
  }

  setPosition(v) {
    this.group.position.copy(v);
  }

  setActive(on) {
    this.active = on;
  }

  setVisible(v) {
    this.group.visible = v;
  }

  tick(time) {
    const target = this.active ? 0.85 : 0;
    const m = this.ring.material;
    m.opacity += (target - m.opacity) * 0.15;
    const s = 1 + Math.sin(time * 4) * 0.08;
    this.ring.scale.set(s, s, s);
    if (this.active && !this.moving) this.group.rotation.y += 0.01;
  }

  /** Nhảy lần lượt qua các điểm (mỗi bước một đường parabol). */
  async hopThrough(points, { stepMs = 210, height = 0.9, onStep } = {}) {
    this.moving = true;
    for (let i = 0; i < points.length; i++) {
      const from = this.group.position.clone();
      const to = points[i];
      const dir = to.clone().sub(from);
      if (dir.lengthSq() > 0.0001) this.group.rotation.y = Math.atan2(dir.x, dir.z);
      await tween(stepMs, (k) => {
        const e = easeInOutCubic(k);
        this.group.position.lerpVectors(from, to, e);
        this.group.position.y = from.y + (to.y - from.y) * e + Math.sin(Math.PI * k) * height;
        const squash = 1 + Math.sin(Math.PI * k) * 0.12;
        this.group.scale.set(1.35 / Math.sqrt(squash), 1.35 * squash, 1.35 / Math.sqrt(squash));
      });
      if (onStep) onStep(i);
    }
    this.group.scale.setScalar(1.35);
    this.moving = false;
  }

  /** Bay thẳng (dùng khi bị đưa vào tù). */
  async flyTo(to, ms = 900) {
    this.moving = true;
    const from = this.group.position.clone();
    await tween(ms, (k) => {
      const e = easeInOutCubic(k);
      this.group.position.lerpVectors(from, to, e);
      this.group.position.y += Math.sin(Math.PI * k) * 4;
      this.group.rotation.y += 0.25;
    });
    this.moving = false;
  }

  async slideTo(to, ms = 250) {
    const from = this.group.position.clone();
    await tween(ms, (k) => this.group.position.lerpVectors(from, to, easeInOutCubic(k)));
  }

  dispose() {
    this.scene.remove(this.group);
  }
}
