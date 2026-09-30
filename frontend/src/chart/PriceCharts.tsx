import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { CandleInterval } from '../api/types'
import { useLiveStream } from '../live/liveStreamContext'
import { lightweightChart, type PriceChartAdapter, type PriceChartFactory } from './chartAdapter'
import { livePoint, toCandlePoints, toLinePoints, type ChartTime } from './series'

export type ChartView = CandleInterval | 'trend'

const VIEWS: { id: ChartView; label: string }[] = [
  { id: '1m', label: '1 分 K 線' },
  { id: '5m', label: '5 分 K 線' },
  { id: 'trend', label: '走勢（最近 1 小時）' },
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
          if (!cancelled) chart.current?.showCandles(points)
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

  // The trend line keeps moving with every live price.
  useEffect(() => {
    if (view !== 'trend') return undefined
    return subscribePrices((price) => {
      const point = livePoint(lastLineTime.current, price)
      if (point) {
        chart.current?.appendLine(point)
        lastLineTime.current = point.time
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
