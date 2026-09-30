import {
  AreaSeries,
  CandlestickSeries,
  createChart,
  type CandlestickData,
  type ChartOptions,
  type DeepPartial,
  type IChartApi,
  type ISeriesApi,
  type MouseEventParams,
  type SingleValueData,
  type Time,
  type UTCTimestamp,
} from 'lightweight-charts'
import type { ChartHover } from './legend'
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
  /**
   * Forward crosshair moves: the data point under it, or null when it leaves the data. The adapter
   * only translates events; what the legend shows is decided by legend.ts (unit tested).
   */
  onCrosshair(listener: (hover: ChartHover | null) => void): void
  dispose(): void
}

export type PriceChartFactory = (container: HTMLElement) => PriceChartAdapter

const localTime = (seconds: number) =>
  new Date(seconds * 1000).toLocaleTimeString('zh-TW', { hour12: false, hour: '2-digit', minute: '2-digit' })

/**
 * Price axis with thousands separators (design direction B). Round ticks stay short ("83,500");
 * any other price - e.g. the last-price label - always gets 2 decimals ("83,236.10"), like every
 * other amount on the page.
 */
const axisTick = new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 0 })
const axisPrice = new Intl.NumberFormat('zh-TW', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const formatAxisPrice = (p: number) => (Number.isInteger(p) ? axisTick : axisPrice).format(p)

/** The canvas cannot use CSS, so read the theme's design tokens (index.css) at draw time. */
function token(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

/** "#9aa8ff" + 0.28 -> "rgba(154, 168, 255, 0.28)" (for the area gradient). */
export function withAlpha(hex: string, alpha: number): string {
  const m = /^#?([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(hex)
  if (!m) return hex
  const [r, g, b] = m.slice(1).map((h) => parseInt(h, 16))
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

function themeOptions(): DeepPartial<ChartOptions> {
  const grid = token('--chart-grid', '#1c202a')
  const crosshair = token('--line-strong', '#6b7385')
  return {
    layout: {
      background: { color: token('--chart-bg', '#161922') },
      textColor: token('--chart-text', '#a3aab8'),
      fontFamily: token('--font-sans', 'system-ui'),
      // TradingView's attribution is given as text in the page footer instead (their licence
      // allows either); the in-chart logo would sit on top of the latest candles.
      attributionLogo: false,
    },
    grid: { vertLines: { visible: false }, horzLines: { color: grid } },
    crosshair: { vertLine: { color: crosshair, labelBackgroundColor: crosshair }, horzLine: { color: crosshair, labelBackgroundColor: crosshair } },
    rightPriceScale: { borderVisible: false },
    timeScale: { borderVisible: false },
  }
}

function candleColors() {
  const up = token('--up', '#3ddc97')
  const down = token('--down', '#ff7a8a')
  return { upColor: up, downColor: down, wickUpColor: up, wickDownColor: down, borderVisible: false }
}

function areaColors() {
  const line = token('--chart-line', '#9aa8ff')
  return { lineColor: line, topColor: withAlpha(line, 0.28), bottomColor: withAlpha(line, 0), lineWidth: 2 as const }
}

/** lightweight-charts crosshair event -> the hovered data point (or null). */
function toHover(param: MouseEventParams<Time>, series: ISeriesApi<'Candlestick'> | ISeriesApi<'Area'> | null): ChartHover | null {
  if (!series || param.time === undefined || !param.point) return null
  const data = param.seriesData.get(series)
  if (!data) return null
  const time = param.time as number
  if ('open' in data) {
    const c = data as CandlestickData<Time>
    return { kind: 'candle', time, open: c.open, high: c.high, low: c.low, close: c.close }
  }
  if ('value' in data) return { kind: 'line', time, value: (data as SingleValueData<Time>).value }
  return null
}

export const lightweightChart: PriceChartFactory = (container) => {
  const chart: IChartApi = createChart(container, {
    autoSize: true,
    ...themeOptions(),
    // Chart times are UTC seconds; show them in the viewer's local time.
    localization: {
      locale: 'zh-TW',
      timeFormatter: (t: number) => new Date(t * 1000).toLocaleString('zh-TW', { hour12: false }),
      priceFormatter: formatAxisPrice,
    },
    timeScale: { borderVisible: false, timeVisible: true, secondsVisible: false, tickMarkFormatter: (t: number) => localTime(t) },
  })
  let series: ISeriesApi<'Candlestick'> | ISeriesApi<'Area'> | null = null
  let kind: 'candles' | 'line' | null = null
  let crosshairListener: ((hover: ChartHover | null) => void) | null = null

  const replace = <T extends ISeriesApi<'Candlestick'> | ISeriesApi<'Area'>>(next: T): T => {
    if (series) chart.removeSeries(series)
    series = next
    return next
  }

  const onCrosshairMove = (param: MouseEventParams<Time>) => crosshairListener?.(toHover(param, series))
  chart.subscribeCrosshairMove(onCrosshairMove)

  // Follow the operating system's light/dark switch while the page is open.
  const scheme = window.matchMedia?.('(prefers-color-scheme: light)')
  const onSchemeChange = () => {
    chart.applyOptions(themeOptions())
    if (kind === 'candles') (series as ISeriesApi<'Candlestick'>).applyOptions(candleColors())
    if (kind === 'line') (series as ISeriesApi<'Area'>).applyOptions(areaColors())
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
      // The 1-hour trend is an area: the line plus a soft accent gradient underneath.
      replace(chart.addSeries(AreaSeries, areaColors()))
        .setData(points.map((p) => ({ ...p, time: p.time as UTCTimestamp })))
      chart.timeScale().fitContent()
    },
    appendLine(point) {
      if (kind === 'line') {
        ;(series as ISeriesApi<'Area'>).update({ ...point, time: point.time as UTCTimestamp })
      }
    },
    onCrosshair(listener) {
      crosshairListener = listener
    },
    dispose() {
      chart.unsubscribeCrosshairMove(onCrosshairMove)
      scheme?.removeEventListener('change', onSchemeChange)
      chart.remove()
    },
  }
}
