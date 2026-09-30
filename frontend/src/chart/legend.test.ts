import { describe, expect, it } from 'vitest'
import { formatAxisPrice } from './chartAdapter'
import { extendRange, legendText, rangeOf } from './legend'

describe('chart legend (task 31)', () => {
  const range = { high: 84120, low: 83990.5 }

  it('shows the chart range when nothing is hovered (and after the crosshair leaves)', () => {
    expect(legendText(range, null)).toBe('圖表區間 高 84,120.00 · 低 83,990.50')
    expect(legendText(null, null)).toBe('')
  })

  it("shows the hovered candle's open / high / low / close", () => {
    expect(legendText(range, { kind: 'candle', time: 1790668800, open: 84000, high: 84120, low: 83990.5, close: 84045.5 }))
      .toBe('開 84,000.00 · 高 84,120.00 · 低 83,990.50 · 收 84,045.50')
  })

  it("shows only the hovered trend point's price and time", () => {
    const at = Date.UTC(2026, 8, 29, 8, 0, 9) / 1000
    const localTime = new Date(at * 1000).toLocaleTimeString('zh-TW', { hour12: false })
    expect(legendText(range, { kind: 'line', time: at, value: 84100 })).toBe(`價格 84,100.00 · 時間 ${localTime}`)
  })

  it('computes the range of candles (high / low) and of line points', () => {
    expect(rangeOf([])).toBeNull()
    expect(rangeOf([
      { time: 1, open: 10, high: 15, low: 9, close: 12 },
      { time: 2, open: 12, high: 13, low: 7, close: 8 },
    ])).toEqual({ high: 15, low: 7 })
    expect(rangeOf([{ time: 1, value: 5 }, { time: 2, value: 3 }])).toEqual({ high: 5, low: 3 })
  })

  it('extends the range with live prices, keeping the same object when inside it', () => {
    const r = { high: 10, low: 5 }
    expect(extendRange(r, 7)).toBe(r)
    expect(extendRange(r, 11)).toEqual({ high: 11, low: 5 })
    expect(extendRange(r, 4)).toEqual({ high: 10, low: 4 })
    expect(extendRange(null, 3)).toEqual({ high: 3, low: 3 })
  })

  it('formats the price axis: round ticks without decimals, other prices with exactly 2', () => {
    expect(formatAxisPrice(83500)).toBe('83,500')
    expect(formatAxisPrice(83236.1)).toBe('83,236.10')
    expect(formatAxisPrice(83236.123)).toBe('83,236.12')
  })
})
