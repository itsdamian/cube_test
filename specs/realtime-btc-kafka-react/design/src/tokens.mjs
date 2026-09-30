// Single source of truth for both directions' colour tokens.
// `node tokens.mjs` prints WCAG contrast ratios (computed, not guessed) and
// build.mjs injects the same values into the mockups.
export const PALETTES = {
  a: {
    dark: {
      bg: '#07090c', panel: '#0e1218', panel2: '#151b23', line: '#222a35', lineStrong: '#5d6b7b',
      text: '#e6ebf0', muted: '#8d99a7', accent: '#4cc2ff', onAccent: '#07090c', brand: '#f7931a',
      up: '#2fd27a', upSoft: '#0c2618', down: '#ff6166', downSoft: '#35131a', warn: '#f5b83d', warnSoft: '#2e230a',
      danger: '#d13438', onDanger: '#ffffff', chartGrid: '#161c25', chartText: '#8d99a7',
    },
    light: {
      bg: '#e9edf2', panel: '#ffffff', panel2: '#f4f6f9', line: '#d9dfe6', lineStrong: '#768392',
      text: '#0d141c', muted: '#4f5b68', accent: '#0060b8', onAccent: '#ffffff', brand: '#b85f00',
      up: '#08773a', upSoft: '#e1f4e8', down: '#c4232b', downSoft: '#fde7e8', warn: '#865600', warnSoft: '#fbefd6',
      danger: '#c4232b', onDanger: '#ffffff', chartGrid: '#eef1f4', chartText: '#4f5b68',
    },
  },
  b: {
    dark: {
      bg: '#0e1016', panel: '#161922', panel2: '#1e222d', line: '#272c38', lineStrong: '#6b7385',
      text: '#f3f4f7', muted: '#a3aab8', accent: '#9aa8ff', onAccent: '#0e1016', brand: '#f7a93b',
      up: '#3ddc97', upSoft: '#11291f', down: '#ff7a8a', downSoft: '#33161d', warn: '#f7c14b', warnSoft: '#2f260f',
      danger: '#d42a4c', onDanger: '#ffffff', chartGrid: '#1c202a', chartText: '#a3aab8',
    },
    light: {
      bg: '#f5f6fa', panel: '#ffffff', panel2: '#f0f2f7', line: '#e2e5ec', lineStrong: '#838b9b',
      text: '#12151c', muted: '#566070', accent: '#4450c8', onAccent: '#ffffff', brand: '#b45f06',
      up: '#067a4f', upSoft: '#e0f5ec', down: '#c0213f', downSoft: '#fde8ec', warn: '#8f4a00', warnSoft: '#fcf0dc',
      danger: '#c0213f', onDanger: '#ffffff', chartGrid: '#f0f2f6', chartText: '#566070',
    },
  },
}

const lum = (hex) => {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
export const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05) }

// [foreground, background, minimum, what it is]
export const PAIRS = [
  ['text', 'bg', 4.5, '內文'], ['text', 'panel', 4.5, '內文'], ['text', 'panel2', 4.5, '內文（hover/raised）'],
  ['muted', 'bg', 4.5, '次要文字'], ['muted', 'panel', 4.5, '次要文字'], ['muted', 'panel2', 4.5, '次要文字'],
  ['accent', 'panel', 4.5, '連結/強調文字'], ['accent', 'bg', 4.5, '連結'], ['onAccent', 'accent', 4.5, '主要按鈕文字'],
  ['up', 'panel', 4.5, '上漲文字'], ['down', 'panel', 4.5, '下跌文字'], ['warn', 'panel', 4.5, '警告文字'],
  ['up', 'upSoft', 4.5, 'LIVE pill'], ['warn', 'warnSoft', 4.5, 'STALE pill / 橫幅'], ['down', 'downSoft', 4.5, 'DISCONNECTED pill / 橫幅'],
  ['text', 'warnSoft', 4.5, '未讀清單內文'], ['text', 'downSoft', 4.5, '錯誤橫幅內文'],
  ['onDanger', 'danger', 4.5, '危險按鈕文字'], ['down', 'panel2', 4.5, '欄位錯誤（raised 上）'],
  ['lineStrong', 'panel', 3, '輸入框/按鈕邊框（UI 元件）'], ['lineStrong', 'bg', 3, '輸入框邊框'],
  ['accent', 'panel', 3, 'focus ring'], ['accent', 'bg', 3, 'focus ring'],
  ['bg', 'up', 4.5, '圖表最新價標籤文字（陽）'], ['bg', 'down', 4.5, '圖表最新價標籤文字（陰）'], ['bg', 'accent', 4.5, '走勢線標籤文字'],
  ['bg', 'warn', 4.5, '未讀數 badge（方向 A）'], ['muted', 'warnSoft', 4.5, '未讀區次要文字'], ['chartText', 'panel', 4.5, '圖表座標文字'],
  ['up', 'chartBg', 3, 'K 線陽線（圖形）'], ['down', 'chartBg', 3, 'K 線陰線（圖形）'],
]

export function report() {
  const rows = []
  let fails = 0
  for (const [dir, themes] of Object.entries(PALETTES)) {
    for (const [theme, p] of Object.entries(themes)) {
      const pal = { ...p, chartBg: p.panel }
      for (const [fg, bg, min, what] of PAIRS) {
        const r = ratio(pal[fg], pal[bg])
        if (r < min) fails++
        rows.push({ dir, theme, fg, bg, fgHex: pal[fg], bgHex: pal[bg], r: r.toFixed(2), min, what, ok: r >= min })
      }
    }
  }
  return { rows, fails }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const { rows, fails } = report()
  const mode = process.argv[2]
  if (mode === 'md') {
    for (const dir of ['a', 'b']) {
      console.log(`\n#### 方向 ${dir.toUpperCase()} 對比度\n\n| 用途 | 前景 / 背景 | 深色 | 淺色 | 門檻 |\n|---|---|---|---|---|`)
      for (const [fg, bg, min, what] of PAIRS) {
        const d = rows.find((x) => x.dir === dir && x.theme === 'dark' && x.fg === fg && x.bg === bg)
        const l = rows.find((x) => x.dir === dir && x.theme === 'light' && x.fg === fg && x.bg === bg)
        console.log(`| ${what} | \`${fg}\` / \`${bg}\` | ${d.r}:1 | ${l.r}:1 | ${min}:1 |`)
      }
    }
  } else {
    for (const x of rows) console.log(`${x.ok ? 'ok  ' : 'FAIL'} ${x.dir} ${x.theme.padEnd(5)} ${x.fg}(${x.fgHex}) on ${x.bg}(${x.bgHex}) = ${x.r} (min ${x.min})`)
  }
  console.error(`failures: ${fails}`)
}
