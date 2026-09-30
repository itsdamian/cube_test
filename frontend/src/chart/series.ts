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

export const INTERVAL_SECONDS: Record<'1m' | '5m', number> = { '1m': 60, '5m': 300 }

/**
 * The candle that is still forming, updated with one live price.
 *
 * - price inside the last candle's bucket -> same candle, high/low/close follow the price;
 * - price in a later bucket                -> a new candle opened at this price;
 * - price older than the last candle       -> null (ignored).
 * The backend's finalised candle for a bucket always replaces this local estimate when it arrives
 * (see mergeForming), so a slightly different "open" is corrected within a minute.
 */
export function applyLivePrice(last: CandlePoint | null, intervalSeconds: number, price: PriceEvent): CandlePoint | null {
  const time = toChartTime(price.eventTime)
  const bucket = time - (time % intervalSeconds)
  const value = price.price
  if (last && bucket < last.time) {
    return null
  }
  if (last && bucket === last.time) {
    return { ...last, high: Math.max(last.high, value), low: Math.min(last.low, value), close: value }
  }
  return { time: bucket, open: value, high: value, low: value, close: value }
}

/**
 * Finalised candles from the API plus the locally forming one: the forming candle is kept only
 * if its bucket is newer than every finalised candle; otherwise the backend's version wins.
 */
export function mergeForming(finalised: CandlePoint[], forming: CandlePoint | null): CandlePoint[] {
  const last = finalised.length ? finalised[finalised.length - 1].time : null
  return forming && (last === null || forming.time > last) ? [...finalised, forming] : finalised
}
