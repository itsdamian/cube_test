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

interface Props {
  /** Tests inject a fake EventSource; the browser's is used otherwise. */
  eventSourceFactory?: EventSourceFactory
  /** Tests inject a fake chart (jsdom has no canvas). */
  chartFactory?: PriceChartFactory
}

/**
 * Dashboard layout: header with the feed state; on desktop two columns (left: live price + chart,
 * the largest area; right: conversion table + alerts) and currency management across the bottom;
 * a single column on phones.
 */
export default function App({ eventSourceFactory, chartFactory }: Props) {
  return (
    <LiveStreamProvider eventSourceFactory={eventSourceFactory}>
      <div className="app">
        <header className="app-header">
          <h1><span className="brand-mark" aria-hidden="true">₿</span>比特幣即時價格</h1>
          <StatusPill />
        </header>
        <main className="layout">
          <div className="layout__main">
            <section className="card" aria-labelledby="live-title">
              <h2 id="live-title">即時價格</h2>
              <LivePrice />
            </section>
            <section className="card" aria-labelledby="chart-title">
              <h2 id="chart-title">價格圖表</h2>
              <PriceCharts chartFactory={chartFactory} />
            </section>
          </div>
          <div className="layout__side">
            <section className="card" aria-labelledby="converted-title">
              <h2 id="converted-title">多幣別換算</h2>
              <ConvertedPrices />
            </section>
            <section className="card" aria-labelledby="alert-title">
              <h2 id="alert-title">價格警示</h2>
              <UnreadAlerts />
              <AlertManager />
            </section>
          </div>
          <section className="card layout__full" aria-labelledby="currency-title">
            <h2 id="currency-title">幣別管理</h2>
            <CurrencyManager />
          </section>
        </main>
        <AlertToasts />
      </div>
    </LiveStreamProvider>
  )
}
