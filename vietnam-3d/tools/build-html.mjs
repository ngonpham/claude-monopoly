// Đóng gói toàn bộ game thành MỘT file HTML tự chứa (JS, CSS, Three.js, âm thanh),
// mở được bằng cách nhấp đúp (file://) mà không cần web server.
// Chạy: npm install && npm run build:html  →  dist/co-ty-phu-viet-nam-3d.html
// Thêm `--artifact <đường-dẫn>` để xuất thêm bản không có khung doctype/html/head/body
// (dùng khi đăng lên claude.ai Artifact, nơi khung trang được bọc tự động).
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
