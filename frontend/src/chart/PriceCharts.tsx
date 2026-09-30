import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { CandleInterval } from '../api/types'
import { useLiveStream } from '../live/liveStreamContext'
import { lightweightChart, type PriceChartAdapter, type PriceChartFactory } from './chartAdapter'
import {
  applyLivePrice,
  INTERVAL_SECONDS,
  livePoint,
  mergeForming,
  toCandlePoints,
  toLinePoints,
  type CandlePoint,
  type ChartTime,
} from './series'

export type ChartView = CandleInterval | 'trend'

const VIEWS: { id: ChartView; label: string }[] = [
  { id: '1m', label: '1 分 K 線' },
  { id: '5m', label: '5 分 K 線' },
  { id: 'trend', label: '走勢 1h' },
]

export const TREND_MINUTES = 60
export const TREND_POINTS = 300
/** Finalised candles appear a few seconds after each minute; re-read them this often. */
export const CANDLE_REFRESH_MS = 30_000

interface Props {
  chartFactory?: PriceChartFactory
  candleRefreshMs?: number
}

/**
 * 1-minute / 5-minute candlesticks (finalised candles from Kafka Streams) or the recent price
 * trend (server-side downsampled, then extended live from the stream).
 */
export function PriceCharts({ chartFactory = lightweightChart, candleRefreshMs = CANDLE_REFRESH_MS }: Props) {
  const container = useRef<HTMLDivElement>(null)
  const chart = useRef<PriceChartAdapter | null>(null)
  const lastLineTime = useRef<ChartTime | null>(null)
  // Candle view: last finalised candle from the API, and the one currently forming from live prices.
  const lastFinal = useRef<CandlePoint | null>(null)
  const forming = useRef<CandlePoint | null>(null)
  const [view, setView] = useState<ChartView>('1m')
  const [message, setMessage] = useState<string | null>(null)
  const { subscribePrices } = useLiveStream()

  useEffect(() => {
    chart.current = chartFactory(container.current!)
    return () => {
      chart.current?.dispose()
      chart.current = null
    }
  }, [chartFactory])

  useEffect(() => {
    let cancelled = false
    lastLineTime.current = null
    lastFinal.current = null
    forming.current = null
    const load = () => {
      const now = Date.now()   // read the clock once: from and to must be exactly TREND_MINUTES apart
      const request = view === 'trend'
        ? api.trend(new Date(now - TREND_MINUTES * 60_000).toISOString(), new Date(now).toISOString(), TREND_POINTS)
          .then((trend) => {
            const points = toLinePoints(trend)
            if (!cancelled) {
              chart.current?.showLine(points)
              lastLineTime.current = points.length ? points[points.length - 1].time : null
            }
            return points.length
          })
        : api.candles(view).then((candles) => {
          const points = toCandlePoints(candles)
          if (!cancelled) {
            lastFinal.current = points.length ? points[points.length - 1] : null
            // The backend's finalised candles win; keep the local one only if it is newer.
            const merged = mergeForming(points, forming.current)
            if (merged.length === points.length) forming.current = null
            chart.current?.showCandles(merged)
          }
          return points.length
        })
      request
        .then((count) => !cancelled && setMessage(count === 0 ? '尚無資料（K 線在每個時段結束後才會出現）' : null))
        .catch(() => !cancelled && setMessage('無法取得圖表資料'))
    }
    load()
    const timer = view === 'trend' ? undefined : setInterval(load, candleRefreshMs)
    return () => {
      cancelled = true
      if (timer) clearInterval(timer)
    }
  }, [view, candleRefreshMs])

  // Live prices: the trend line keeps extending; in the candle views the last (still forming)
  // candle follows the price, so the chart never looks behind the header price.
  useEffect(() => {
    return subscribePrices((price) => {
      if (view === 'trend') {
        const point = livePoint(lastLineTime.current, price)
        if (point) {
          chart.current?.appendLine(point)
          lastLineTime.current = point.time
        }
        return
      }
      const next = applyLivePrice(forming.current ?? lastFinal.current, INTERVAL_SECONDS[view], price)
      if (next) {
        forming.current = next
        chart.current?.updateCandle(next)
      }
    })
  }, [view, subscribePrices])

  return (
    <div>
      <div className="tabs" role="tablist" aria-label="圖表類型">
        {VIEWS.map((v) => (
          <button key={v.id} type="button" role="tab" aria-selected={view === v.id}
                  className={view === v.id ? 'tab tab--active' : 'tab'} onClick={() => setView(v.id)}>
            {v.label}
          </button>
        ))}
      </div>
      <div ref={container} className="chart" data-testid="price-chart" />
      {message && <p className="muted">{message}</p>}
    </div>
  )
}
