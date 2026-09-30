import type { CandlePoint, ChartTime, LinePoint } from './series'

/** What the crosshair is on, as forwarded by the chart adapter (null = not over a data point). */
export type ChartHover =
  | { kind: 'candle'; time: ChartTime; open: number; high: number; low: number; close: number }
  | { kind: 'line'; time: ChartTime; value: number }

/** Highest / lowest price currently drawn in the chart. */
export interface PriceRange {
  high: number
  low: number
}

/** One "label value" pair of the legend; the value is shown in bold, tabular digits. */
export interface LegendPart {
  label: string
  value: string
}

const price = new Intl.NumberFormat('zh-TW', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const time = (seconds: ChartTime) => new Date(seconds * 1000).toLocaleTimeString('zh-TW', { hour12: false })

/** Range of the points being drawn (candles use their high / low), or null when there are none. */
export function rangeOf(points: CandlePoint[] | LinePoint[]): PriceRange | null {
  let range: PriceRange | null = null
  for (const p of points) {
    const [hi, lo] = 'value' in p ? [p.value, p.value] : [p.high, p.low]
    range = range ? { high: Math.max(range.high, hi), low: Math.min(range.low, lo) } : { high: hi, low: lo }
  }
  return range
}

/** The range after a new live price; the same object when nothing changed (so React skips a render). */
export function extendRange(range: PriceRange | null, value: number): PriceRange | null {
  if (!range) return { high: value, low: value }
  if (value <= range.high && value >= range.low) return range
  return { high: Math.max(range.high, value), low: Math.min(range.low, value) }
}

/**
 * Chart toolbar legend: the candle under the crosshair (開 / 高 / 低 / 收), the trend point under
 * it (價格 / 時間), or - when nothing is hovered - the range of the chart (圖表區間 高 / 低).
 */
export function legendParts(range: PriceRange | null, hover: ChartHover | null): LegendPart[] {
  if (hover?.kind === 'candle') {
    return [
      { label: '開', value: price.format(hover.open) },
      { label: '高', value: price.format(hover.high) },
      { label: '低', value: price.format(hover.low) },
      { label: '收', value: price.format(hover.close) },
    ]
  }
  if (hover?.kind === 'line') {
    return [
      { label: '價格', value: price.format(hover.value) },
      { label: '時間', value: time(hover.time) },
    ]
  }
  if (!range) return []
  return [
    { label: '圖表區間 高', value: price.format(range.high) },
    { label: '低', value: price.format(range.low) },
  ]
}

/** The legend as one string, e.g. "圖表區間 高 84,120.00 · 低 83,990.50". */
export function legendText(range: PriceRange | null, hover: ChartHover | null): string {
  return legendParts(range, hover).map((p) => `${p.label} ${p.value}`).join(' · ')
}
