import { CandlestickSeries, createChart, LineSeries, type IChartApi, type ISeriesApi, type UTCTimestamp } from 'lightweight-charts'
import type { CandlePoint, LinePoint } from './series'

/**
 * The few chart operations the page needs. The real implementation draws on a <canvas> with
 * TradingView's lightweight-charts; tests (jsdom has no canvas) pass a recording fake.
 */
export interface PriceChartAdapter {
  showCandles(points: CandlePoint[]): void
  showLine(points: LinePoint[]): void
  appendLine(point: LinePoint): void
  dispose(): void
}

export type PriceChartFactory = (container: HTMLElement) => PriceChartAdapter

const localTime = (seconds: number) =>
  new Date(seconds * 1000).toLocaleTimeString('zh-TW', { hour12: false, hour: '2-digit', minute: '2-digit' })

export const lightweightChart: PriceChartFactory = (container) => {
  const chart: IChartApi = createChart(container, {
    autoSize: true,
    height: 320,
    // Chart times are UTC seconds; show them in the viewer's local time.
    localization: { locale: 'zh-TW', timeFormatter: (t: number) => new Date(t * 1000).toLocaleString('zh-TW', { hour12: false }) },
    timeScale: { timeVisible: true, secondsVisible: false, tickMarkFormatter: (t: number) => localTime(t) },
  })
  let series: ISeriesApi<'Candlestick'> | ISeriesApi<'Line'> | null = null

  const replace = <T extends ISeriesApi<'Candlestick'> | ISeriesApi<'Line'>>(next: T): T => {
    if (series) chart.removeSeries(series)
    series = next
    return next
  }

  return {
    showCandles(points) {
      replace(chart.addSeries(CandlestickSeries, { upColor: '#1a7f37', downColor: '#cf222e', wickUpColor: '#1a7f37', wickDownColor: '#cf222e', borderVisible: false }))
        .setData(points.map((p) => ({ ...p, time: p.time as UTCTimestamp })))
      chart.timeScale().fitContent()
    },
    showLine(points) {
      replace(chart.addSeries(LineSeries, { color: '#0969da', lineWidth: 2 }))
        .setData(points.map((p) => ({ ...p, time: p.time as UTCTimestamp })))
      chart.timeScale().fitContent()
    },
    appendLine(point) {
      ;(series as ISeriesApi<'Line'> | null)?.update({ ...point, time: point.time as UTCTimestamp })
    },
    dispose() {
      chart.remove()
    },
  }
}
