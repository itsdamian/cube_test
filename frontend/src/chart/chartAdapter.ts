import {
  CandlestickSeries,
  createChart,
  LineSeries,
  type DeepPartial,
  type ChartOptions,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts'
import type { CandlePoint, LinePoint } from './series'

/**
 * The few chart operations the page needs. The real implementation draws on a <canvas> with
 * TradingView's lightweight-charts; tests (jsdom has no canvas) pass a recording fake.
 */
export interface PriceChartAdapter {
  showCandles(points: CandlePoint[]): void
  /** Replace the last candle or append a newer one (the candle still forming). */
  updateCandle(point: CandlePoint): void
  showLine(points: LinePoint[]): void
  appendLine(point: LinePoint): void
  dispose(): void
}

export type PriceChartFactory = (container: HTMLElement) => PriceChartAdapter

const localTime = (seconds: number) =>
  new Date(seconds * 1000).toLocaleTimeString('zh-TW', { hour12: false, hour: '2-digit', minute: '2-digit' })

/** The canvas cannot use CSS, so read the theme's design tokens (index.css) at draw time. */
function token(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function themeOptions(): DeepPartial<ChartOptions> {
  const grid = token('--chart-grid', '#1f2a36')
  return {
    layout: {
      background: { color: token('--chart-bg', '#131a22') },
      textColor: token('--chart-text', '#9aa7b4'),
      fontFamily: token('--font-sans', 'system-ui'),
    },
    grid: { vertLines: { color: grid }, horzLines: { color: grid } },
    rightPriceScale: { borderColor: grid },
    timeScale: { borderColor: grid },
  }
}

function candleColors() {
  const up = token('--up', '#3fb950')
  const down = token('--down', '#ff6b6b')
  return { upColor: up, downColor: down, wickUpColor: up, wickDownColor: down, borderVisible: false }
}

export const lightweightChart: PriceChartFactory = (container) => {
  const chart: IChartApi = createChart(container, {
    autoSize: true,
    ...themeOptions(),
    // Chart times are UTC seconds; show them in the viewer's local time.
    localization: { locale: 'zh-TW', timeFormatter: (t: number) => new Date(t * 1000).toLocaleString('zh-TW', { hour12: false }) },
    timeScale: { ...themeOptions().timeScale, timeVisible: true, secondsVisible: false, tickMarkFormatter: (t: number) => localTime(t) },
  })
  let series: ISeriesApi<'Candlestick'> | ISeriesApi<'Line'> | null = null
  let kind: 'candles' | 'line' | null = null

  const replace = <T extends ISeriesApi<'Candlestick'> | ISeriesApi<'Line'>>(next: T): T => {
    if (series) chart.removeSeries(series)
    series = next
    return next
  }

  // Follow the operating system's light/dark switch while the page is open.
  const scheme = window.matchMedia?.('(prefers-color-scheme: light)')
  const onSchemeChange = () => {
    chart.applyOptions(themeOptions())
    if (kind === 'candles') (series as ISeriesApi<'Candlestick'>).applyOptions(candleColors())
    if (kind === 'line') (series as ISeriesApi<'Line'>).applyOptions({ color: token('--chart-line', '#58a6ff') })
  }
  scheme?.addEventListener('change', onSchemeChange)

  return {
    showCandles(points) {
      kind = 'candles'
      replace(chart.addSeries(CandlestickSeries, candleColors()))
        .setData(points.map((p) => ({ ...p, time: p.time as UTCTimestamp })))
      chart.timeScale().fitContent()
    },
    updateCandle(point) {
      if (kind === 'candles') {
        ;(series as ISeriesApi<'Candlestick'>).update({ ...point, time: point.time as UTCTimestamp })
      }
    },
    showLine(points) {
      kind = 'line'
      replace(chart.addSeries(LineSeries, { color: token('--chart-line', '#58a6ff'), lineWidth: 2 }))
        .setData(points.map((p) => ({ ...p, time: p.time as UTCTimestamp })))
      chart.timeScale().fitContent()
    },
    appendLine(point) {
      ;(series as ISeriesApi<'Line'> | null)?.update({ ...point, time: point.time as UTCTimestamp })
    },
    dispose() {
      scheme?.removeEventListener('change', onSchemeChange)
      chart.remove()
    },
  }
}
