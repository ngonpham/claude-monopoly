// Sân khấu Three.js: renderer, camera, ánh sáng, bầu trời, biển và các đảo đá vôi xa xa.
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/OrbitControls.js';
import { canvasTexture } from './helpers.js';
import { karst, junkBoat } from './landmarks.js';

export class Stage {
  constructor(container) {
    this.container = container;
    this.renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
    // Trong iframe 720×1280 của Astrocade (bị thu nhỏ khi hiển thị) không cần render quá 1,5×.
    const maxDpr = document.documentElement.classList.contains('astro') ? 1.5 : 2;
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, maxDpr));
    this.renderer.setSize(container.clientWidth, container.clientHeight);
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.05;
    container.appendChild(this.renderer.domElement);

    this.scene = new THREE.Scene();
    this.scene.background = canvasTexture(16, 512, (ctx, w, h) => {
      const g = ctx.createLinearGradient(0, 0, 0, h);
      g.addColorStop(0, '#3d7fc4');
      g.addColorStop(0.55, '#a9d3ec');
      g.addColorStop(0.75, '#f6dcb4');
      g.addColorStop(1, '#f6dcb4');
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, w, h);
    });
    this.scene.fog = new THREE.Fog(0xcfe3ee, 70, 190);

    this.camera = new THREE.PerspectiveCamera(42, container.clientWidth / container.clientHeight, 0.1, 500);
    this.homePosition = new THREE.Vector3(0, 30, 31);
    this.camera.position.copy(this.homePosition);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.08;
    this.controls.minDistance = 8;
    this.controls.maxDistance = 75;
    this.controls.maxPolarAngle = 1.38;
    this.controls.target.set(0, 0, 0);
    this.controls.addEventListener('start', () => { this.userInteracting = true; });
    this.controls.addEventListener('end', () => { this.userInteracting = false; });

    this._lights();
    this._ocean();
    this._scenery();

    this.tickers = [];
    this.followTarget = null;
    this.clock = new THREE.Clock();
    window.addEventListener('resize', () => this.resize());
    this.renderer.setAnimationLoop(() => this._frame());
  }

  _lights() {
    this.scene.add(new THREE.HemisphereLight(0xdcefff, 0x8a6a45, 1.25));
    const sun = new THREE.DirectionalLight(0xfff1d6, 2.6);
    sun.position.set(22, 34, 14);
    sun.castShadow = true;
    sun.shadow.mapSize.set(2048, 2048);
    const sc = sun.shadow.camera;
    sc.left = -19; sc.right = 19; sc.top = 19; sc.bottom = -19; sc.near = 5; sc.far = 90;
    sun.shadow.bias = -0.0004;
    sun.shadow.normalBias = 0.03;
    this.scene.add(sun);
    this.sun = sun;
    const fill = new THREE.DirectionalLight(0xbfd8ff, 0.5);
    fill.position.set(-20, 15, -18);
    this.scene.add(fill);
  }

  _ocean() {
    const geo = new THREE.PlaneGeometry(420, 420, 64, 64);
    geo.rotateX(-Math.PI / 2);
    this.oceanBase = geo.attributes.position.array.slice();
    this.ocean = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({
      color: 0x2a86a8, roughness: 0.28, metalness: 0.15, flatShading: true,
    }));
    this.ocean.position.y = -1.6;
    this.ocean.receiveShadow = true;
    this.scene.add(this.ocean);
  }

  _scenery() {
    // quần đảo đá vôi kiểu Hạ Long bao quanh bàn cờ
    this.boats = [];
    const ring = new THREE.Group();
    let seed = 30;
    for (let i = 0; i < 26; i++) {
      const a = (i / 26) * Math.PI * 2 + Math.sin(i * 7.3) * 0.12;
      const r = 34 + ((i * 37) % 23);
      const s = 7 + ((i * 13) % 9);
      const isl = karst(0, 0, 1, 0.3, seed++);
      isl.scale.set(s, s * (0.9 + ((i * 5) % 7) / 6), s);
      isl.position.set(Math.cos(a) * r, -1.6, Math.sin(a) * r);
      ring.add(isl);
    }
    for (let i = 0; i < 4; i++) {
      const b = junkBoat();
      b.scale.setScalar(5);
      b.userData.phase = (i / 4) * Math.PI * 2;
      b.userData.radius = 22 + i * 2;
      this.boats.push(b);
      ring.add(b);
    }
    this.scene.add(ring);
  }

  onTick(fn) {
    this.tickers.push(fn);
  }

  /** Camera đi theo một điểm (Vector3) — null để thả tự do. */
  follow(vec) {
    this.followTarget = vec ? vec.clone() : null;
  }

  resetView() {
    this.followTarget = new THREE.Vector3(0, 0, 0);
    this._flyCamera = { to: this.homePosition.clone() };
  }

  resize() {
    const w = this.container.clientWidth;
    const h = this.container.clientHeight;
    this.camera.aspect = w / h;
    // màn hình dọc: lùi camera ra để thấy đủ bàn
    this.camera.fov = w < h ? 58 : 42;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(w, h);
  }

  project(vec) {
    const v = vec.clone().project(this.camera);
    return {
      x: (v.x * 0.5 + 0.5) * this.container.clientWidth,
      y: (-v.y * 0.5 + 0.5) * this.container.clientHeight,
      visible: v.z < 1,
    };
  }

  _frame() {
    const dt = Math.min(this.clock.getDelta(), 0.1);
    const t = this.clock.elapsedTime;

    // sóng biển nhẹ
    const p = this.ocean.geometry.attributes.position.array;
    const b = this.oceanBase;
    for (let i = 0; i < p.length; i += 3) {
      p[i + 1] = Math.sin(b[i] * 0.08 + t * 0.9) * 0.25 + Math.cos(b[i + 2] * 0.07 + t * 0.7) * 0.25;
    }
    this.ocean.geometry.attributes.position.needsUpdate = true;
    this.ocean.geometry.computeVertexNormals();

    for (const boat of this.boats) {
      const a = boat.userData.phase + t * 0.03;
      const r = boat.userData.radius;
      boat.position.set(Math.cos(a) * r, -1.5 + Math.sin(t + r) * 0.08, Math.sin(a) * r);
      boat.rotation.y = -a - Math.PI / 2;
    }

    if (this.followTarget && !this.userInteracting) {
      const delta = this.followTarget.clone().sub(this.controls.target).multiplyScalar(0.06);
      this.controls.target.add(delta);
      this.camera.position.add(delta);
    }
    if (this._flyCamera) {
      this.camera.position.lerp(this._flyCamera.to, 0.06);
      if (this.camera.position.distanceTo(this._flyCamera.to) < 0.05) this._flyCamera = null;
    }
    this.controls.update();
    for (const fn of this.tickers) fn(t, dt);
    this.renderer.render(this.scene, this.camera);
  }
}
