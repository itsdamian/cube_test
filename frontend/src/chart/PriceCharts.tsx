import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { api } from '../api/client'
import type { CandleInterval } from '../api/types'
import { useLiveStream } from '../live/liveStreamContext'
import { lightweightChart, type PriceChartAdapter, type PriceChartFactory } from './chartAdapter'
import { extendRange, legendParts, rangeOf, type ChartHover, type PriceRange } from './legend'
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
  { id: 'trend', label: '1 小時走勢' },
]

export const TREND_MINUTES = 60
export const TREND_POINTS = 300
/** Finalised candles appear a few seconds after each minute; re-read them this often. */
export const CANDLE_REFRESH_MS = 30_000

interface Props {
  chartFactory?: PriceChartFactory
  candleRefreshMs?: number
}

/** Touch screens have no hover: there the legend always shows the range (design direction B). */
const canHover = () => window.matchMedia?.('(hover: hover)').matches ?? true

/**
 * 1-minute / 5-minute candlesticks (finalised candles from Kafka Streams) or the recent price
 * trend (server-side downsampled, then extended live from the stream). The toolbar shows the
 * chart's high / low, or the candle / point under the mouse.
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
  // Which view's data has arrived (a different view = still loading) and the range drawn for it.
  const [loaded, setLoaded] = useState<ChartView | null>(null)
  const [range, setRange] = useState<{ view: ChartView; range: PriceRange | null } | null>(null)
  const [hover, setHover] = useState<ChartHover | null>(null)
  const tabs = useRef<(HTMLButtonElement | null)[]>([])
  const { subscribePrices } = useLiveStream()

  useEffect(() => {
    chart.current = chartFactory(container.current!)
    chart.current.onCrosshair((next) => setHover(next && canHover() ? next : null))
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
              setRange({ view, range: rangeOf(points) })
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
            setRange({ view, range: rangeOf(merged) })
          }
          return points.length
        })
      request
        .then((count) => !cancelled && setMessage(count === 0 ? '尚無資料（K 線在每個時段結束後才會出現）' : null))
        .catch(() => !cancelled && setMessage('無法取得圖表資料'))
        .finally(() => !cancelled && setLoaded(view))
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
          setRange((r) => (r?.view === view ? withPrice(r, point.value) : r))
        }
        return
      }
      const next = applyLivePrice(forming.current ?? lastFinal.current, INTERVAL_SECONDS[view], price)
      if (next) {
        forming.current = next
        chart.current?.updateCandle(next)
        setRange((r) => (r?.view === view ? withPrice(r, price.price) : r))
      }
    })
  }, [view, subscribePrices])

  // Arrow keys move between the tabs (WAI-ARIA tabs pattern, automatic activation).
  const onTabKey = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    const moves: Record<string, number> = { ArrowRight: index + 1, ArrowLeft: index - 1, Home: 0, End: VIEWS.length - 1 }
    if (!(event.key in moves)) return
    event.preventDefault()
    const next = (moves[event.key] + VIEWS.length) % VIEWS.length
    setView(VIEWS[next].id)
    tabs.current[next]?.focus()
  }

  const parts = legendParts(range?.view === view ? range.range : null, hover)
  return (
    <div className="price-charts">
      <h2 id="chart-title" className="visually-hidden">價格圖表</h2>
      <div className="chart-bar">
        <div className="seg" role="tablist" aria-label="圖表類型">
          {VIEWS.map((v, index) => (
            <button key={v.id} type="button" role="tab" aria-selected={view === v.id}
                    tabIndex={view === v.id ? 0 : -1} ref={(el) => { tabs.current[index] = el }}
                    onClick={() => setView(v.id)} onKeyDown={(e) => onTabKey(e, index)}>
              {v.label}
            </button>
          ))}
        </div>
        <span className="range" data-testid="chart-legend">
          {parts.map((p, i) => (
            <span key={p.label}>{i > 0 && ' · '}{p.label} <b>{p.value}</b></span>
          ))}
        </span>
      </div>
      <div className="chart-wrap">
        <div ref={container} className="chart" data-testid="price-chart" />
        {loaded !== view && <p className="chart__loading">載入圖表中…</p>}
      </div>
      {loaded === view && message && <p className="chart__message">{message}</p>}
    </div>
  )
}

function withPrice(r: { view: ChartView; range: PriceRange | null }, value: number) {
  const next = extendRange(r.range, value)
  return next === r.range ? r : { view: r.view, range: next }
}
