import { useRef, useState, type MouseEvent } from 'react'
import { AlertManager } from './alert/AlertManager'
import { AlertToasts } from './alert/AlertToasts'
import { UnreadAlerts } from './alert/UnreadAlerts'
import type { PriceChartFactory } from './chart/chartAdapter'
import { PriceCharts } from './chart/PriceCharts'
import { ConvertedPrices } from './converted/ConvertedPrices'
import { CurrencyManager } from './currency/CurrencyManager'
import { LivePrice } from './live/LivePrice'
import { LiveStreamProvider } from './live/LiveStreamProvider'
import { StatusPill } from './live/StatusPill'
import type { EventSourceFactory } from './live/liveStream'
import { useLiveStream } from './live/liveStreamContext'

interface Props {
  /** Tests inject a fake EventSource; the browser's is used otherwise. */
  eventSourceFactory?: EventSourceFactory
  /** Tests inject a fake chart (jsdom has no canvas). */
  chartFactory?: PriceChartFactory
}

export default function App({ eventSourceFactory, chartFactory }: Props) {
  return (
    <LiveStreamProvider eventSourceFactory={eventSourceFactory}>
      <Dashboard chartFactory={chartFactory} />
    </LiveStreamProvider>
  )
}

/**
 * Design direction B (specs/realtime-btc-kafka-react/design/redesign.md §7): a nav bar with the
 * feed state; on desktop a 12-column grid - the hero card (price + chart, 8 columns) next to the
 * alerts card (4 columns), the conversion tiles across the full width; one column on phones.
 * Currency management opens in a modal <dialog> from the conversion card.
 *
 * The feed state is exposed once as data-feed on the root, so the CSS can mute prices, hide the
 * direction pill and show skeletons without every component re-deriving it.
 */
function Dashboard({ chartFactory }: { chartFactory?: PriceChartFactory }) {
  const { display } = useLiveStream()
  const dialog = useRef<HTMLDialogElement>(null)
  const trigger = useRef<HTMLButtonElement | null>(null)
  const [currencyOpen, setCurrencyOpen] = useState(false)

  const openCurrencies = (button: HTMLButtonElement) => {
    trigger.current = button
    setCurrencyOpen(true)
    dialog.current?.showModal()
  }
  // Fires for the close button, Esc (the browser's "cancel") and a click on the backdrop.
  const onDialogClose = () => {
    setCurrencyOpen(false)
    trigger.current?.focus()
  }
  const onDialogClick = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === dialog.current) dialog.current.close()   // click on the backdrop
  }

  return (
    <div className="app" data-feed={display}>
      <div className="wrap">
        <header className="nav">
          <div className="brand">
            <span className="brand__mark" aria-hidden="true">₿</span>
            <div>
              <h1>比特幣即時價格</h1>
              <p>BTC-USD 即時行情 · 多幣別換算 · 價格警示</p>
            </div>
          </div>
          <span className="nav__spacer" />
          <StatusPill />
        </header>

        <main className="page">
          <section className="card hero" aria-labelledby="live-title">
            <div className="hero__top">
              <h2 id="live-title" className="visually-hidden">即時價格</h2>
              <LivePrice />
            </div>
            <section className="hero__chart" aria-labelledby="chart-title">
              <PriceCharts chartFactory={chartFactory} />
            </section>
          </section>

          <aside className="side">
            <section className="card" aria-labelledby="alert-title">
              <AlertManager>
                <UnreadAlerts />
              </AlertManager>
            </section>
          </aside>

          <section className="card fx" aria-labelledby="converted-title">
            <ConvertedPrices onManage={openCurrencies} />
          </section>
        </main>

        <footer className="footer">
          <span>價格來源：Coinbase（主要）／ Kraken（備援）</span>
          {/* In-chart logo is off (attributionLogo: false); TradingView is credited here instead. */}
          <span>圖表：<a href="https://www.tradingview.com/" target="_blank" rel="noreferrer">TradingView Lightweight Charts™</a></span>
          <span>警示由後端判斷，同一警示觸發後冷卻 5 分鐘</span>
        </footer>
      </div>

      <dialog ref={dialog} className="modal" aria-labelledby="currency-title" onClose={onDialogClose} onClick={onDialogClick}>
        <header className="modal__head">
          <div>
            <h2 id="currency-title">管理幣別</h2>
            <p>換算卡片會列出這裡的所有幣別。</p>
          </div>
          <button className="btn btn--icon btn--quiet" type="button" aria-label="關閉" onClick={() => dialog.current?.close()}>×</button>
        </header>
        {/* Mounted only while open, so the list is re-read every time the dialog opens. */}
        {currencyOpen && <CurrencyManager />}
      </dialog>

      <AlertToasts />
    </div>
  )
}
