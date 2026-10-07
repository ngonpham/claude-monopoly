// Hai viên xúc xắc 3D: lăn, nảy và dừng đúng mặt kết quả do engine quyết định.
import * as THREE from 'three';
import { canvasTexture, tween } from './helpers.js';

// Thứ tự mặt của BoxGeometry: +x, -x, +y, -y, +z, -z (các mặt đối diện cộng lại = 7).
const FACE_VALUES = [3, 4, 1, 6, 2, 5];
const FACE_NORMALS = [
  new THREE.Vector3(1, 0, 0), new THREE.Vector3(-1, 0, 0),
  new THREE.Vector3(0, 1, 0), new THREE.Vector3(0, -1, 0),
  new THREE.Vector3(0, 0, 1), new THREE.Vector3(0, 0, -1),
];
const PIPS = {
  1: [[0.5, 0.5]],
  2: [[0.27, 0.27], [0.73, 0.73]],
  3: [[0.25, 0.25], [0.5, 0.5], [0.75, 0.75]],
  4: [[0.27, 0.27], [0.73, 0.27], [0.27, 0.73], [0.73, 0.73]],
  5: [[0.25, 0.25], [0.75, 0.25], [0.5, 0.5], [0.25, 0.75], [0.75, 0.75]],
  6: [[0.27, 0.22], [0.73, 0.22], [0.27, 0.5], [0.73, 0.5], [0.27, 0.78], [0.73, 0.78]],
};

function faceTexture(v) {
  return canvasTexture(128, 128, (ctx, s) => {
    ctx.fillStyle = '#fffaf0';
    ctx.fillRect(0, 0, s, s);
    // kiểu xúc xắc Á Đông: mặt 1 và 4 chấm đỏ
    ctx.fillStyle = v === 1 || v === 4 ? '#d01c1c' : '#1b1b1b';
    const r = v === 1 ? 20 : 11;
    for (const [x, y] of PIPS[v]) {
      ctx.beginPath();
      ctx.arc(x * s, y * s, r, 0, Math.PI * 2);
      ctx.fill();
    }
  });
}

export class Dice3D {
  constructor(scene) {
    this.scene = scene;
    const mats = FACE_VALUES.map((v) => new THREE.MeshStandardMaterial({ map: faceTexture(v), roughness: 0.35 }));
    const geo = new THREE.BoxGeometry(1, 1, 1);
    this.dice = [0, 1].map((i) => {
      const m = new THREE.Mesh(geo, mats);
      m.castShadow = true;
      m.position.set(-0.9 + i * 1.8, 0.5, 5.2);
      scene.add(m);
      return m;
    });
    this.show([5, 6], false);
  }

  /** Quaternion để mặt mang giá trị v hướng lên trên, xoay ngẫu nhiên quanh trục đứng. */
  static orientationFor(v, yaw) {
    const idx = FACE_VALUES.indexOf(v);
    const q = new THREE.Quaternion().setFromUnitVectors(FACE_NORMALS[idx], new THREE.Vector3(0, 1, 0));
    const spin = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), yaw);
    return spin.multiply(q);
  }

  show(values) {
    values.forEach((v, i) => {
      this.dice[i].quaternion.copy(Dice3D.orientationFor(v, 0.3 + i * 0.5));
      this.dice[i].position.y = 0.5;
    });
  }

  async roll(values) {
    const starts = this.dice.map((d, i) => ({
      pos: new THREE.Vector3(-4 + i * 1.2, 4.5, 8.5),
      end: new THREE.Vector3(-0.9 + i * 1.8 + (Math.random() - 0.5) * 0.6, 0.5, 5.2 + (Math.random() - 0.5) * 0.8),
      axis: new THREE.Vector3(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).normalize(),
      spins: 3 + Math.random() * 2,
      final: Dice3D.orientationFor(values[i], Math.random() * Math.PI * 2),
    }));
    const tmp = new THREE.Quaternion();
    await tween(1250, (t) => {
      this.dice.forEach((d, i) => {
        const s = starts[i];
        // vị trí: trượt ngang + nảy với biên độ giảm dần
        const e = 1 - (1 - t) ** 2;
        d.position.lerpVectors(s.pos, s.end, e);
        const bounce = Math.abs(Math.cos(t * Math.PI * 3.2)) * (1 - t) ** 1.5 * 3.2;
        d.position.y = 0.5 + bounce;
        // xoay: quay quanh trục ngẫu nhiên, giảm dần về hướng kết quả
        const remain = (1 - e) * s.spins * Math.PI * 2;
        tmp.setFromAxisAngle(s.axis, remain);
        d.quaternion.copy(tmp).multiply(s.final);
      });
    });
    this.dice.forEach((d, i) => d.quaternion.copy(starts[i].final));
  }
}
