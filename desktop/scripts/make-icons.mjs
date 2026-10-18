// 자리표시용 앱 아이콘·메뉴바 아이콘을 만든다(외부 의존 없음: PNG를 직접 쓰고, macOS의 sips·iconutil로 .icns를 만든다).
//   node scripts/make-icons.mjs  →  assets/icon.png(1024), assets/icon.icns, assets/trayTemplate.png(18), assets/trayTemplate@2x.png(36)
// 그림: 둥근 사각형 위에 오른쪽 위로 오르는 꺾은선(업로그의 "로그가 쌓여 오른다").
import { deflateSync } from "node:zlib";
import { writeFileSync, mkdirSync, rmSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const assets = join(dirname(fileURLToPath(import.meta.url)), "..", "assets");
mkdirSync(assets, { recursive: true });

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function png(size, rgba) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0;
    rgba.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

// 단위 좌표(0..1) 꺾은선
const LINE = [[0.2, 0.7], [0.4, 0.52], [0.55, 0.6], [0.8, 0.3]];
function distToSegment(px, py, [ax, ay], [bx, by]) {
  const dx = bx - ax, dy = by - ay;
  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}
function onLine(x, y, width) {
  for (let i = 0; i < LINE.length - 1; i++) if (distToSegment(x, y, LINE[i], LINE[i + 1]) <= width / 2) return true;
  return false;
}
function inRoundRect(x, y, inset, radius) {
  const lo = inset, hi = 1 - inset;
  if (x < lo || x > hi || y < lo || y > hi) return false;
  const cx = Math.min(Math.max(x, lo + radius), hi - radius);
  const cy = Math.min(Math.max(y, lo + radius), hi - radius);
  return Math.hypot(x - cx, y - cy) <= radius;
}

// 4x4 슈퍼샘플링으로 가장자리를 부드럽게
function render(size, shade) {
  const out = Buffer.alloc(size * size * 4);
  const S = 4;
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let r = 0, g = 0, b = 0, a = 0;
      for (let sy = 0; sy < S; sy++) {
        for (let sx = 0; sx < S; sx++) {
          const u = (x + (sx + 0.5) / S) / size, v = (y + (sy + 0.5) / S) / size;
          const c = shade(u, v);
          if (c) { r += c[0] * c[3]; g += c[1] * c[3]; b += c[2] * c[3]; a += c[3]; }
        }
      }
      const i = (y * size + x) * 4;
      if (a > 0) { out[i] = r / a; out[i + 1] = g / a; out[i + 2] = b / a; }
      out[i + 3] = (a / (S * S)) * 255;
    }
  }
  return out;
}

// 앱 아이콘: macOS 아이콘 격자(1024 중 가운데 824)에 맞춘 초록 그라데이션 + 흰 꺾은선
const appIcon = render(1024, (u, v) => {
  if (!inRoundRect(u, v, 0.1, 0.18)) return null;
  if (onLine(u, v, 0.075)) return [255, 255, 255, 1];
  const t = v;
  return [Math.round(24 + 10 * t), Math.round(190 - 60 * t), Math.round(120 - 30 * t), 1];
});
writeFileSync(join(assets, "icon.png"), png(1024, appIcon));

// 메뉴바 아이콘: 템플릿 이미지(검은색 + 투명). macOS가 밝기·다크 모드에 맞춰 칠한다.
for (const [size, name] of [[18, "trayTemplate.png"], [36, "trayTemplate@2x.png"]]) {
  const img = render(size, (u, v) => (onLine(u * 1.0, v, 0.13) ? [0, 0, 0, 1] : null));
  writeFileSync(join(assets, name), png(size, img));
}

// .icns
const iconset = join(assets, "icon.iconset");
rmSync(iconset, { recursive: true, force: true });
mkdirSync(iconset);
for (const s of [16, 32, 128, 256, 512]) {
  for (const [scale, suffix] of [[1, ""], [2, "@2x"]]) {
    const px = s * scale;
    execFileSync("sips", ["-z", String(px), String(px), join(assets, "icon.png"), "--out", join(iconset, `icon_${s}x${s}${suffix}.png`)], { stdio: "ignore" });
  }
}
execFileSync("iconutil", ["-c", "icns", iconset, "-o", join(assets, "icon.icns")]);
rmSync(iconset, { recursive: true, force: true });
console.log("아이콘:", assets);
