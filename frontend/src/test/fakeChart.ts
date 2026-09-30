import type { PriceChartAdapter, PriceChartFactory } from '../chart/chartAdapter'
import type { ChartHover } from '../chart/legend'
import type { CandlePoint, LinePoint } from '../chart/series'

/** Records what would have been drawn (jsdom cannot render the real canvas chart). */
export class FakeChart implements PriceChartAdapter {
  candles: CandlePoint[][] = []
  candleUpdates: CandlePoint[] = []
  lines: LinePoint[][] = []
  appended: LinePoint[] = []
  disposed = false
  private crosshair: ((hover: ChartHover | null) => void) | null = null

  showCandles(points: CandlePoint[]): void {
    this.candles.push(points)
  }

  updateCandle(point: CandlePoint): void {
    this.candleUpdates.push(point)
  }

  showLine(points: LinePoint[]): void {
    this.lines.push(points)
  }

  appendLine(point: LinePoint): void {
    this.appended.push(point)
  }

  onCrosshair(listener: (hover: ChartHover | null) => void): void {
    this.crosshair = listener
  }

  /** Simulate the crosshair moving onto a data point (or off the data, with null). */
  hover(hover: ChartHover | null): void {
    this.crosshair?.(hover)
  }

  dispose(): void {
    this.disposed = true
  }
}

export function fakeChartFactory(): { factory: PriceChartFactory; charts: FakeChart[] } {
  const charts: FakeChart[] = []
  return {
    charts,
    factory: () => {
      const chart = new FakeChart()
      charts.push(chart)
      return chart
    },
  }
}
