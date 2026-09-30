import { describe, expect, it } from 'vitest'
import { samples } from '../test/msw/samples'
import { applyLivePrice, livePoint, mergeForming, toCandlePoints, toChartTime, toLinePoints } from './series'

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

  describe('forming candle from live prices (task 29 item 6)', () => {
    const at = (eventTime: string, value: number) => ({ ...samples.ssePrice, eventTime, price: value })
    const last = { time: 1790668800, open: 100, high: 110, low: 95, close: 105 }   // 08:00:00 1m

    it('updates high / low / close inside the same bucket', () => {
      expect(applyLivePrice(last, 60, at('2026-09-29T08:00:30Z', 120)))
        .toEqual({ time: 1790668800, open: 100, high: 120, low: 95, close: 120 })
      expect(applyLivePrice(last, 60, at('2026-09-29T08:00:59.999Z', 90)))
        .toEqual({ time: 1790668800, open: 100, high: 110, low: 90, close: 90 })
    })

    it('opens a new candle when the next bucket starts', () => {
      expect(applyLivePrice(last, 60, at('2026-09-29T08:01:00Z', 101)))
        .toEqual({ time: 1790668860, open: 101, high: 101, low: 101, close: 101 })
    })

    it('uses 5-minute buckets for 5m and ignores prices older than the last candle', () => {
      const five = { ...last, time: 1790668800 }            // 08:00-08:05
      expect(applyLivePrice(five, 300, at('2026-09-29T08:04:59Z', 99))?.time).toBe(1790668800)
      expect(applyLivePrice(five, 300, at('2026-09-29T08:05:00Z', 99))?.time).toBe(1790669100)
      expect(applyLivePrice(last, 60, at('2026-09-29T07:59:59Z', 1))).toBeNull()
    })

    it('starts a candle when nothing was loaded yet', () => {
      expect(applyLivePrice(null, 60, at('2026-09-29T08:00:42Z', 7)))
        .toEqual({ time: 1790668800, open: 7, high: 7, low: 7, close: 7 })
    })

    it('keeps the local forming candle only while it is newer than every finalised one', () => {
      const forming = { time: 1790668860, open: 1, high: 2, low: 1, close: 2 }
      expect(mergeForming([last], forming)).toEqual([last, forming])
      const finalisedSame = { ...forming, open: 9, close: 9 }
      expect(mergeForming([last, finalisedSame], forming)).toEqual([last, finalisedSame])   // backend wins
      expect(mergeForming([], forming)).toEqual([forming])
      expect(mergeForming([last], null)).toEqual([last])
    })
  })
})
