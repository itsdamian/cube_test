import { ConvertedPrices } from './converted/ConvertedPrices'
import { LivePrice } from './live/LivePrice'
import { LiveStreamProvider } from './live/LiveStreamProvider'
import type { EventSourceFactory } from './live/liveStream'

interface Props {
  /** Tests inject a fake EventSource; the browser's is used otherwise. */
  eventSourceFactory?: EventSourceFactory
}

/**
 * Page layout. Each section is filled in by its own task:
 * live price (21), conversion table (22), charts (23), currency management (24), alerts (25).
 */
export default function App({ eventSourceFactory }: Props) {
  return (
    <LiveStreamProvider eventSourceFactory={eventSourceFactory}>
    <div className="app">
      <header className="app-header">
        <h1>比特幣即時價格</h1>
      </header>
      <main className="app-main">
        <section aria-labelledby="live-title">
          <h2 id="live-title">即時價格</h2>
          <LivePrice />
        </section>
        <section aria-labelledby="converted-title">
          <h2 id="converted-title">多幣別換算</h2>
          <ConvertedPrices />
        </section>
        <section aria-labelledby="chart-title">
          <h2 id="chart-title">價格圖表</h2>
        </section>
        <section aria-labelledby="currency-title">
          <h2 id="currency-title">幣別管理</h2>
        </section>
        <section aria-labelledby="alert-title">
          <h2 id="alert-title">價格警示</h2>
        </section>
      </main>
    </div>
    </LiveStreamProvider>
  )
}
