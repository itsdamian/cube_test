// Fill redesign.md's token and contrast tables from tokens.mjs (so the doc can't drift from the checked values).
import { readFileSync, writeFileSync } from 'node:fs'
import { PALETTES, PAIRS, report } from './tokens.mjs'
const here = new URL('.', import.meta.url).pathname
const USE = { bg: '頁面底色', panel: '面板 / 卡片', panel2: '次層（hover、tile、表單區）', line: '裝飾性分隔線', lineStrong: '輸入框、按鈕邊框（≥3:1）',
  text: '主要文字', muted: '次要文字', accent: '互動色：連結、主要按鈕、focus、走勢線', onAccent: '主要按鈕上的文字', brand: '₿ 品牌標誌（不當文字色）',
  up: '上漲 / 即時', upSoft: '上漲淡底', down: '下跌 / 斷線 / 錯誤', downSoft: '下跌淡底', warn: '延遲 / 未讀警示', warnSoft: '警告淡底',
  danger: '危險按鈕底色', onDanger: '危險按鈕文字', chartGrid: '圖表格線（canvas 讀取）', chartText: '圖表座標文字（canvas 讀取）' }
const kebab = (k) => k.replace(/([A-Z])/g, '-$1').replace(/(\d)/g, '-$1').toLowerCase()
const { rows, fails } = report()
let md = readFileSync(`${here}redesign.src.md`, 'utf8')
for (const d of ['a', 'b']) {
  const P = PALETTES[d]
  md = md.replace(`<!--TOKENS_${d.toUpperCase()}-->`, Object.keys(P.dark).map((k) => `| \`--${kebab(k)}\` | \`${P.dark[k]}\` | \`${P.light[k]}\` | ${USE[k]} |`).join('\n'))
  const t = [`**計算出的 WCAG 對比度**（\`node src/tokens.mjs\`；圖表底色 = \`--panel\`）`, '', '| 用途 | 前景 / 背景 | 深色 | 淺色 | 門檻 |', '|---|---|---|---|---|']
  for (const [fg, bg, min, what] of PAIRS) {
    const g = (th) => rows.find((x) => x.dir === d && x.theme === th && x.fg === fg && x.bg === bg).r
    t.push(`| ${what} | \`${kebab(fg)}\` / \`${kebab(bg)}\` | ${g('dark')}:1 | ${g('light')}:1 | ${min}:1 |`)
  }
  md = md.replace(`<!--CONTRAST_${d.toUpperCase()}-->`, t.join('\n'))
}
if (fails) throw new Error(`${fails} contrast failures`)
writeFileSync(`${here}../redesign.md`, md)
console.log('wrote redesign.md')
