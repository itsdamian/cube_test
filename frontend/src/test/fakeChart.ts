import type { PriceChartAdapter, PriceChartFactory } from '../chart/chartAdapter'
import type { CandlePoint, LinePoint } from '../chart/series'

/** Records what would have been drawn (jsdom cannot render the real canvas chart). */
export class FakeChart implements PriceChartAdapter {
  candles: CandlePoint[][] = []
  lines: LinePoint[][] = []
  appended: LinePoint[] = []
  disposed = false

  showCandles(points: CandlePoint[]): void {
    this.candles.push(points)
  }

  showLine(points: LinePoint[]): void {
    this.lines.push(points)
  }

  appendLine(point: LinePoint): void {
    this.appended.push(point)
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
