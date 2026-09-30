import { act, render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { FeedStatus, PriceEvent } from '../api/types'
import { FakeEventSource } from '../test/fakeEventSource'
import { samples } from '../test/msw/samples'
import { server } from '../test/msw/server'
import { LivePrice } from './LivePrice'
import { LiveStreamProvider } from './LiveStreamProvider'
import { StatusPill } from './StatusPill'

// The feed state and active source are shown once, in the header pill (task 29); the price card
// only dims and explains. So the state assertions below read the pill.
function renderLivePrice() {
  render(
    <LiveStreamProvider eventSourceFactory={FakeEventSource.factory} tickMs={1_000}>
      <StatusPill />
      <LivePrice />
    </LiveStreamProvider>,
  )
  return FakeEventSource.latest()
}

const status = (overrides: Partial<FeedStatus>): FeedStatus => ({ ...samples.sseStatus, ...overrides })
const price = (value: number): PriceEvent => ({ ...samples.ssePrice, price: value })

describe('LivePrice', () => {
  beforeEach(() => {
    // Only timers and Date are faked; promises (fetch via MSW) keep working.
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
    // Start empty so each test controls what is shown.
    server.use(http.get('/api/prices/latest', () => new HttpResponse(null, { status: 404 })))
  })

  afterEach(() => {
    vi.useRealTimers()
    FakeEventSource.instances = []
  })

  it('connects to /api/stream and shows each new price without reloading (AC1)', () => {
    const stream = renderLivePrice()
    expect(stream.url).toBe('/api/stream')
    expect(screen.getByTestId('header-feed-state')).toHaveTextContent('連線中')

    act(() => stream.emit('status', status({ state: 'LIVE', activeSource: 'coinbase' })))
    act(() => stream.emit('price', price(84045.5)))
    expect(screen.getByTestId('btc-price')).toHaveTextContent('84,045.50')
    expect(screen.getByTestId('header-feed-state')).toHaveTextContent('即時')

    act(() => stream.emit('price', price(84100.25)))
    expect(screen.getByTestId('btc-price')).toHaveTextContent('84,100.25')
  })

  it('shows the last known price from the API before the first stream event', async () => {
    server.use(http.get('/api/prices/latest', () => HttpResponse.json(samples.pricesLatest)))
    renderLivePrice()
    expect(await screen.findByTestId('btc-price')).toHaveTextContent('84,045.50')
  })

  it('shows 資料延遲 / 已斷線 when the server reports STALE / DISCONNECTED (AC2)', () => {
    const stream = renderLivePrice()
    act(() => stream.emit('price', price(84045.5)))

    act(() => stream.emit('status', status({ state: 'STALE' })))
    expect(screen.getByTestId('header-feed-state')).toHaveTextContent('資料延遲')
    expect(screen.getByRole('alert')).toHaveTextContent('資料延遲')

    act(() => stream.emit('status', status({ state: 'DISCONNECTED' })))
    expect(screen.getByTestId('header-feed-state')).toHaveTextContent('已斷線')
  })

  it('shows 資料延遲 after 15 s without ANY event, and status events count as activity (AC2)', () => {
    const stream = renderLivePrice()
    act(() => stream.emit('status', status({ state: 'LIVE' })))
    act(() => stream.emit('price', price(84045.5)))

    // Quiet market: no new price, but the server's status keeps arriving every 5 s -> still live.
    for (let i = 0; i < 4; i++) {
      act(() => vi.advanceTimersByTime(5_000))
      act(() => stream.emit('status', status({ state: 'LIVE' })))
    }
    expect(screen.getByTestId('header-feed-state')).toHaveTextContent('即時')

    // Nothing at all for 14 s: still live; at 15 s: delayed.
    act(() => vi.advanceTimersByTime(14_000))
    expect(screen.getByTestId('header-feed-state')).toHaveTextContent('即時')
    act(() => vi.advanceTimersByTime(1_000))
    expect(screen.getByTestId('header-feed-state')).toHaveTextContent('資料延遲')

    // Anything arriving again makes it live again.
    act(() => stream.emit('price', price(84050)))
    expect(screen.getByTestId('header-feed-state')).toHaveTextContent('即時')
  })

  it('stops saying 連線中 and shows 資料延遲 if nothing arrives within 15 s of opening (QA CONCERN 21)', () => {
    renderLivePrice()
    act(() => vi.advanceTimersByTime(14_000))
    expect(screen.getByTestId('header-feed-state')).toHaveTextContent('連線中')
    act(() => vi.advanceTimersByTime(1_000))
    expect(screen.getByTestId('header-feed-state')).toHaveTextContent('資料延遲')
  })

  it('shows 已斷線 when the connection to the backend fails, and recovers when it reopens', () => {
    const stream = renderLivePrice()
    act(() => stream.emit('price', price(84045.5)))

    act(() => stream.fail())
    expect(screen.getByTestId('header-feed-state')).toHaveTextContent('已斷線')
    expect(screen.getByRole('alert')).toHaveTextContent('已斷線')

    act(() => stream.open())
    act(() => stream.emit('status', status({ state: 'LIVE' })))
    expect(screen.getByTestId('header-feed-state')).toHaveTextContent('即時')
  })

  it('shows which exchange is currently used (AC6)', () => {
    const stream = renderLivePrice()
    act(() => stream.emit('status', status({ state: 'LIVE', activeSource: 'coinbase' })))
    expect(screen.getByTestId('active-source')).toHaveTextContent('Coinbase')

    act(() => stream.emit('status', status({ state: 'LIVE', activeSource: 'kraken' })))
    expect(screen.getByTestId('active-source')).toHaveTextContent('Kraken')
  })

  // Design direction B: the big number never changes colour; the direction pill next to it does.
  it('marks the direction pill up / down when a trade is above / below the previous one', () => {
    const stream = renderLivePrice()
    act(() => stream.emit('price', price(84000)))
    expect(screen.queryByTestId('price-trend')).not.toBeInTheDocument()   // nothing to compare yet

    act(() => stream.emit('price', price(84010)))
    expect(screen.getByTestId('price-trend')).toHaveAttribute('data-dir', 'up')
    expect(screen.getByTestId('price-trend')).toHaveTextContent('較前一筆上漲')

    act(() => stream.emit('price', price(83990.5)))
    expect(screen.getByTestId('price-trend')).toHaveAttribute('data-dir', 'down')
    expect(screen.getByTestId('price-trend')).toHaveTextContent('較前一筆下跌')
    expect(screen.getByTestId('btc-price').className).toBe('price__value')   // no colour class on the number
  })

  it('shows the change since the page opened, relative to the first price received (task 31)', () => {
    const stream = renderLivePrice()
    act(() => stream.emit('price', price(84000)))
    expect(screen.getByTestId('price-change')).toHaveTextContent('0.00（0.00%）')
    act(() => stream.emit('price', price(84042)))
    expect(screen.getByTestId('price-change')).toHaveTextContent('+42.00（+0.05%）')
    expect(screen.getByTestId('price-change')).toHaveAttribute('data-sign', 'up')
    act(() => stream.emit('price', price(83916)))
    expect(screen.getByTestId('price-change')).toHaveTextContent('-84.00（-0.10%）')
    expect(screen.getByTestId('price-change')).toHaveAttribute('data-sign', 'down')
  })

  it('flashes at most every 2 s (trade time) in the same direction, but at once when the direction changes', () => {
    const stream = renderLivePrice()
    const at = (value: number, second: number) =>
      ({ ...price(value), eventTime: new Date(Date.UTC(2026, 8, 30, 8, 0, second)).toISOString() })
    act(() => stream.emit('price', at(84000, 0)))
    act(() => stream.emit('price', at(84010, 1)))                 // up: flash
    const firstUp = screen.getByTestId('price-trend')
    expect(firstUp).toHaveClass('flash-up')

    act(() => stream.emit('price', at(84020, 2)))                 // up again 1 s later: no new flash
    expect(screen.getByTestId('price-trend')).toBe(firstUp)          // same element = animation not replayed
    expect(screen.getByTestId('btc-price')).toHaveTextContent('84,020.00')

    act(() => stream.emit('price', at(84000, 2)))                 // down: flashes immediately
    const firstDown = screen.getByTestId('price-trend')
    expect(firstDown).not.toBe(firstUp)
    expect(firstDown).toHaveClass('flash-down')

    act(() => stream.emit('price', at(83995, 3)))                 // down 1 s later: no new flash
    expect(screen.getByTestId('price-trend')).toBe(firstDown)
    act(() => stream.emit('price', at(83990, 4)))                 // down 2 s after the last flash: flashes
    expect(screen.getByTestId('price-trend')).not.toBe(firstDown)
  })

  it('never flashes more than once per second, even when the price bounces up and down', () => {
    const stream = renderLivePrice()
    const at = (value: number, ms: number) =>
      ({ ...price(value), eventTime: new Date(Date.UTC(2026, 8, 30, 8, 0, 0, ms)).toISOString() })
    act(() => stream.emit('price', at(84000, 0)))
    act(() => stream.emit('price', at(84010, 100)))              // up: flash
    const first = screen.getByTestId('price-trend')
    act(() => stream.emit('price', at(84000, 400)))              // down 0.3 s later: too soon
    act(() => stream.emit('price', at(84010, 800)))              // up again 0.7 s later: too soon
    expect(screen.getByTestId('price-trend')).toBe(first)
    expect(screen.getByTestId('btc-price')).toHaveTextContent('84,010.00')   // the price itself updates

    act(() => stream.emit('price', at(84000, 1_100)))            // down, 1.0 s after the flash: flashes
    expect(screen.getByTestId('price-trend')).not.toBe(first)
    expect(screen.getByTestId('price-trend')).toHaveClass('flash-down')
  })

  it('dims the price card and explains why whenever the data is not live, and clears both when live again (QA CONCERN 23)', () => {
    const stream = renderLivePrice()
    const card = () => screen.getByTestId('live-price-card')
    act(() => stream.emit('status', status({ state: 'LIVE' })))
    act(() => stream.emit('price', price(84045.5)))
    expect(card()).toHaveClass('live-price--live')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    act(() => stream.emit('status', status({ state: 'STALE' })))
    expect(card()).toHaveClass('live-price--delayed')
    expect(screen.getByRole('alert')).toHaveTextContent('資料延遲：暫時沒有收到新的價格')

    act(() => stream.emit('status', status({ state: 'DISCONNECTED' })))
    expect(card()).toHaveClass('live-price--disconnected')
    expect(screen.getByRole('alert')).toHaveTextContent('已斷線：目前顯示的是最後收到的價格')

    act(() => stream.emit('status', status({ state: 'LIVE' })))
    expect(card()).toHaveClass('live-price--live')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    act(() => vi.advanceTimersByTime(15_000))                     // silence alone is enough
    expect(card()).toHaveClass('live-price--delayed')
    expect(screen.getByRole('alert')).toHaveTextContent('資料延遲')

    act(() => stream.emit('price', price(84050)))
    expect(card()).toHaveClass('live-price--live')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('closes the stream when the page unmounts', () => {
    const { unmount } = render(
      <LiveStreamProvider eventSourceFactory={FakeEventSource.factory}>
        <LivePrice />
      </LiveStreamProvider>,
    )
    unmount()
    expect(FakeEventSource.latest().closed).toBe(true)
  })
})
