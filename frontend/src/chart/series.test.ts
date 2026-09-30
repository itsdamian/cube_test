import { describe, expect, it } from 'vitest'
import { samples } from '../test/msw/samples'
import { livePoint, toCandlePoints, toChartTime, toLinePoints } from './series'

describe('API -> chart series', () => {
  it('uses whole UTC seconds for chart time', () => {
    expect(toChartTime('2026-09-29T08:00:00Z')).toBe(1790668800)
    expect(toChartTime('2026-09-29T08:00:00.999Z')).toBe(1790668800)
  })

  it('maps candles by open time with their OHLC values', () => {
    const points = toCandlePoints(samples.candles)
    expect(points).toEqual([
      { time: 1790668800, open: 84040.1, high: 84061, low: 84031.55, close: 84052.3 },
      { time: 1790668860, open: 84052.3, high: 84070, low: 84049.9, close: 84066.12 },
    ])
  })

  it('sorts candles and keeps one per time', () => {
    const [a, b] = samples.candles
    const points = toCandlePoints([b, a, { ...b, close: 1 }])
    expect(points.map((p) => p.time)).toEqual([1790668800, 1790668860])
    expect(points[1].close).toBe(1)
  })

  it('maps the trend to line points at each bucket last trade time', () => {
    expect(toLinePoints(samples.pricesTrend)).toEqual([
      { time: 1790668802, value: 84040.1 },
      { time: 1790668805, value: 84041.7 },
    ])
  })

  it('appends a live price after the last point, replaces within the same second, ignores older ones', () => {
    const price = (eventTime: string, value: number) => ({ ...samples.ssePrice, eventTime, price: value })
    expect(livePoint(1790668805, price('2026-09-29T08:00:07Z', 1))).toEqual({ time: 1790668807, value: 1 })
    expect(livePoint(1790668805, price('2026-09-29T08:00:05.5Z', 2))).toEqual({ time: 1790668805, value: 2 })
    expect(livePoint(1790668805, price('2026-09-29T08:00:04Z', 3))).toBeNull()
    expect(livePoint(null, price('2026-09-29T08:00:04Z', 4))).toEqual({ time: 1790668804, value: 4 })
  })
})
