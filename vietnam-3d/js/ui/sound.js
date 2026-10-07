// Âm thanh: dùng lại các file mp3 của game gốc (monopoly/static/sound).
const FILES = { dice: 'sound/dice.mp3', move: 'sound/move.mp3', cash: 'sound/cash.mp3', build: 'sound/build.mp3' };
const KEY = 'cotyphu-vn-muted';

export class Sound {
  constructor() {
    this.base = {};
    for (const [k, src] of Object.entries(FILES)) {
      const a = new Audio(src);
      a.preload = 'auto';
      this.base[k] = a;
    }
    try { this.muted = localStorage.getItem(KEY) === '1'; } catch { this.muted = false; }
  }

  play(name, volume = 0.6) {
    if (this.muted || !this.base[name]) return;
    const a = this.base[name].cloneNode();
    a.volume = volume;
    a.play().catch(() => {});
  }

  toggle() {
    this.muted = !this.muted;
    try { localStorage.setItem(KEY, this.muted ? '1' : '0'); } catch { /* bỏ qua */ }
    return this.muted;
  }
}
