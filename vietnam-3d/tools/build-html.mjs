// Đóng gói toàn bộ game thành MỘT file HTML tự chứa (JS, CSS, Three.js, âm thanh),
// mở được bằng cách nhấp đúp (file://) mà không cần web server.
// Chạy: npm install && npm run build:html  →  dist/co-ty-phu-viet-nam-3d.html
// Thêm `--artifact <đường-dẫn>` để xuất thêm bản không có khung doctype/html/head/body
// (dùng khi đăng lên claude.ai Artifact, nơi khung trang được bọc tự động).
// Thêm `--astrocade <đường-dẫn>` để xuất bản chạy trên Astrocade (xem cuối file).
import { build } from 'esbuild';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const vendor = join(root, 'vendor/three');

// Giải quyết import "three" và "three/addons/..." giống import map trong index.html.
const threeAlias = {
  name: 'three-alias',
  setup(b) {
    b.onResolve({ filter: /^three$/ }, () => ({ path: join(vendor, 'three.module.min.js') }));
    b.onResolve({ filter: /^three\/addons\// }, (args) => ({ path: join(vendor, args.path.slice('three/addons/'.length)) }));
  },
};

const result = await build({
  entryPoints: [join(root, 'js/main.js')],
  bundle: true,
  format: 'esm',
  minify: true,
  target: 'es2020',
  write: false,
  legalComments: 'none',
  plugins: [threeAlias],
});
// Tránh chuỗi "</script" trong mã làm đóng thẻ script sớm.
const js = result.outputFiles[0].text.replace(/<\/script/gi, '<\\/script');

const css = readFileSync(join(root, 'css/style.css'), 'utf8');
const sounds = {};
for (const name of ['dice', 'move', 'cash', 'build']) {
  sounds[name] = `data:audio/mpeg;base64,${readFileSync(join(root, `sound/${name}.mp3`)).toString('base64')}`;
}

let html = readFileSync(join(root, 'index.html'), 'utf8');
const swap = (pattern, replacement) => {
  if (!pattern.test(html)) throw new Error(`Không tìm thấy ${pattern} trong index.html`);
  html = html.replace(pattern, () => replacement);
};
swap(/<link rel="stylesheet" href="css\/style.css">/, `<style>\n${css}</style>`);
swap(/<script type="importmap">[\s\S]*?<\/script>/, `<script>window.COTYPHU_ASSETS = { sounds: ${JSON.stringify(sounds)} };</script>`);
swap(/<script type="module" src="js\/main.js"><\/script>/, `<script type="module">\n${js}</script>`);
html = html.replace('<head>', '<head>\n  <!-- Bản đóng gói một file — sinh tự động bởi tools/build-html.mjs, không sửa tay. Three.js © mrdoob và cộng sự, giấy phép MIT. -->');

const out = join(root, 'dist/co-ty-phu-viet-nam-3d.html');
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, html);
console.log(`Đã tạo ${out} (${(Buffer.byteLength(html) / 1024).toFixed(0)} KB)`);

const flag = process.argv.indexOf('--artifact');
if (flag !== -1) {
  const target = resolve(process.argv[flag + 1] || join(root, 'dist/artifact.html'));
  const body = html
    .replace(/<!doctype html>\s*/i, '')
    .replace(/<html[^>]*>\s*/i, '')
    .replace(/<\/html>\s*$/i, '')
    .replace(/<head>\s*/i, '')
    .replace(/<\/head>\s*/i, '')
    .replace(/<body>\s*/i, '')
    .replace(/<\/body>\s*/i, '')
    .replace(/\s*<meta charset="utf-8">/i, '')
    .replace(/\s*<meta name="viewport"[^>]*>/i, '');
  // <title> phải nằm trong 8 KB đầu tiên của file.
  const titleMatch = body.match(/\s*<title>[^<]*<\/title>/i);
  const page = titleMatch ? `${titleMatch[0].trim()}\n${body.replace(titleMatch[0], '')}` : body;
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, page);
  console.log(`Đã tạo bản Artifact ${target}`);
}

// ---------------------------------------------------------------------------------------------
// `--astrocade <đường-dẫn>`: bản cho Astrocade (astrocade.com). Nền tảng chạy game trong iframe
// sandbox 720×1280 không có localStorage, không cho tải tài nguyên ngoài (kể cả Google Fonts) và
// khởi động game bằng window.run(mode). Bản này:
//   • đóng gói dạng IIFE (script thường), hoãn khởi động tới khi run() được gọi;
//   • nhúng font Be Vietnam Pro dạng data URI;
//   • lưu ván / tuỳ chọn qua lib.saveUserGameState / lib.getUserGameState;
//   • bật lớp .astro (phóng to giao diện cho iframe bị thu nhỏ).
const astroFlag = process.argv.indexOf('--astrocade');
if (astroFlag !== -1) {
  const target = resolve(process.argv[astroFlag + 1] || join(root, 'dist/astrocade/game_code.html'));
  const iife = await build({
    entryPoints: [join(root, 'js/main.js')],
    bundle: true,
    format: 'iife',
    minify: true,
    target: 'es2020',
    write: false,
    legalComments: 'none',
    plugins: [threeAlias],
  });
  const appJs = iife.outputFiles[0].text.replace(/<\/script/gi, '<\\/script');
  const weights = { regular: 400, semibold: 600, bold: 700, extrabold: 800, black: 900 };
  const fontCss = Object.entries(weights).map(([name, w]) => {
    const b64 = readFileSync(join(root, `fonts/be_vietnam_pro_${name}.ttf`)).toString('base64');
    return `@font-face{font-family:"Be Vietnam Pro";font-weight:${w};font-style:normal;font-display:block;src:url(data:font/ttf;base64,${b64}) format("truetype")}`;
  }).join('\n');

  // Cầu nối với Astrocade: chạy trước bundle, cung cấp window.run và kho lưu COTYPHU_PLATFORM.
  const prelude = `
window.COTYPHU_DEFER_BOOT = true;
window.COTYPHU_ASSETS = { sounds: ${JSON.stringify(sounds)} };
(function () {
  var started = false;
  async function start(mode) {
    if (started) return;
    started = true;
    document.documentElement.classList.add('astro');
    var lib = window.lib;
    var store = { game: null, prefs: {} };
    if (lib && lib.getUserGameState) {
      try {
        var res = await lib.getUserGameState();
        if (res && res.state) store = Object.assign(store, res.state);
      } catch (e) { /* chưa đăng nhập hoặc chưa có bản lưu */ }
    }
    var timer = null;
    function persist() {
      clearTimeout(timer);
      timer = setTimeout(function () {
        if (lib && lib.saveUserGameState) lib.saveUserGameState(store).catch(function () {});
      }, 600);
    }
    window.COTYPHU_PLATFORM = {
      load: function () { return store.game || null; },
      save: function (data) { store.game = data; persist(); },
      clear: function () { store.game = null; persist(); },
      getPref: function (k) { return store.prefs ? store.prefs[k] : undefined; },
      setPref: function (k, v) { store.prefs = store.prefs || {}; store.prefs[k] = v; persist(); },
      mode: mode || 'play'
    };
    (function boot() { window.COTYPHU_BOOT ? window.COTYPHU_BOOT() : setTimeout(boot, 30); })();
  }
  window.run = function (mode) { return start(mode); };
  // Mở ngoài Astrocade (không có lib): tự khởi động như bản thường.
  window.addEventListener('load', function () { setTimeout(function () { if (!window.lib) start('play'); }, 300); });
})();`;

  let page = readFileSync(join(root, 'index.html'), 'utf8');
  const swapA = (pattern, replacement) => {
    if (!pattern.test(page)) throw new Error(`Không tìm thấy ${pattern} trong index.html`);
    page = page.replace(pattern, () => replacement);
  };
  swapA(/\s*<link rel="preconnect" href="https:\/\/fonts.googleapis.com">/, '');
  swapA(/\s*<link rel="preconnect" href="https:\/\/fonts.gstatic.com" crossorigin>/, '');
  swapA(/\s*<link href="https:\/\/fonts.googleapis.com[^>]*>/, '');
  swapA(/<link rel="stylesheet" href="css\/style.css">/, `<style>\n${fontCss}\n${css}</style>`);
  swapA(/<script type="importmap">[\s\S]*?<\/script>/, `<script>${prelude}\n</script>`);
  swapA(/<script type="module" src="js\/main.js"><\/script>/, `<script>\n${appJs}</script>`);
  page = page.replace('<head>', '<head>\n  <!-- Bản Astrocade — sinh tự động bởi tools/build-html.mjs --astrocade, không sửa tay. Three.js (MIT), Be Vietnam Pro (SIL OFL 1.1). -->');
  if (/https?:\/\/(?!www\.w3\.org)/.test(page.replace(/<!--[\s\S]*?-->/g, '').replace(/data:[^"')]+/g, ''))) {
    console.warn('Cảnh báo: bản Astrocade vẫn còn URL ngoài — kiểm tra lại.');
  }
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, page);
  writeFileSync(join(dirname(target), 'game_config.json'), '{}\n');
  writeFileSync(join(dirname(target), 'asset_map.json'), '{}\n');
  writeFileSync(join(dirname(target), 'persistent_storage_config.json'), '{}\n');
  console.log(`Đã tạo bản Astrocade ${target} (${(Buffer.byteLength(page) / 1024).toFixed(0)} KB)`);
}
