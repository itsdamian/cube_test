import type { Candle, PriceEvent, Trend } from '../api/types'

/** Chart time = whole seconds since the epoch (UTC), as lightweight-charts expects. */
export type ChartTime = number

export interface CandlePoint {
  time: ChartTime
  open: number
  high: number
  low: number
  close: number
}

export interface LinePoint {
  time: ChartTime
  value: number
}

export function toChartTime(iso: string): ChartTime {
  return Math.floor(Date.parse(iso) / 1000)
}

/**
 * API candles -> chart candles, one per open time, ascending (the chart library requires strictly
 * increasing times; a duplicate keeps the later element).
 */
export function toCandlePoints(candles: Candle[]): CandlePoint[] {
  const byTime = new Map<ChartTime, CandlePoint>()
  for (const c of candles) {
    const time = toChartTime(c.openTime)
    byTime.set(time, { time, open: c.open, high: c.high, low: c.low, close: c.close })
  }
  return [...byTime.values()].sort((a, b) => a.time - b.time)
}

/** Downsampled trend -> line points at the time of each bucket's last trade, ascending, unique. */
export function toLinePoints(trend: Trend): LinePoint[] {
  const byTime = new Map<ChartTime, LinePoint>()
  for (const p of trend.points) {
    const time = toChartTime(p.eventTime)
    byTime.set(time, { time, value: p.price })
  }
  return [...byTime.values()].sort((a, b) => a.time - b.time)
}

/**
 * A live price as the next line point, or null if it is older than the line's last point
 * (the chart can only append or replace the last point). Same second -> replaces it.
 */
export function livePoint(lastTime: ChartTime | null, price: PriceEvent): LinePoint | null {
  const time = toChartTime(price.eventTime)
  if (lastTime !== null && time < lastTime) {
    return null
  }
  return { time, value: price.price }
}
