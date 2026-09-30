import { act, render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { FeedStatus, PriceEvent } from '../api/types'
import { FakeEventSource } from '../test/fakeEventSource'
import { samples } from '../test/msw/samples'
import { server } from '../test/msw/server'
import { LivePrice } from './LivePrice'
import { LiveStreamProvider } from './LiveStreamProvider'

function renderLivePrice() {
  render(
    <LiveStreamProvider eventSourceFactory={FakeEventSource.factory} tickMs={1_000}>
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
    expect(screen.getByTestId('feed-state')).toHaveTextContent('連線中')

    act(() => stream.emit('status', status({ state: 'LIVE', activeSource: 'coinbase' })))
    act(() => stream.emit('price', price(84045.5)))
    expect(screen.getByTestId('btc-price')).toHaveTextContent('84,045.50')
    expect(screen.getByTestId('feed-state')).toHaveTextContent('即時')

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
    expect(screen.getByTestId('feed-state')).toHaveTextContent('資料延遲')
    expect(screen.getByRole('alert')).toHaveTextContent('資料延遲')

    act(() => stream.emit('status', status({ state: 'DISCONNECTED' })))
    expect(screen.getByTestId('feed-state')).toHaveTextContent('已斷線')
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
    expect(screen.getByTestId('feed-state')).toHaveTextContent('即時')

    // Nothing at all for 14 s: still live; at 15 s: delayed.
    act(() => vi.advanceTimersByTime(14_000))
    expect(screen.getByTestId('feed-state')).toHaveTextContent('即時')
    act(() => vi.advanceTimersByTime(1_000))
    expect(screen.getByTestId('feed-state')).toHaveTextContent('資料延遲')

    // Anything arriving again makes it live again.
    act(() => stream.emit('price', price(84050)))
    expect(screen.getByTestId('feed-state')).toHaveTextContent('即時')
  })

  it('stops saying 連線中 and shows 資料延遲 if nothing arrives within 15 s of opening (QA CONCERN 21)', () => {
    renderLivePrice()
    act(() => vi.advanceTimersByTime(14_000))
    expect(screen.getByTestId('feed-state')).toHaveTextContent('連線中')
    act(() => vi.advanceTimersByTime(1_000))
    expect(screen.getByTestId('feed-state')).toHaveTextContent('資料延遲')
  })

  it('shows 已斷線 when the connection to the backend fails, and recovers when it reopens', () => {
    const stream = renderLivePrice()
    act(() => stream.emit('price', price(84045.5)))

    act(() => stream.fail())
    expect(screen.getByTestId('feed-state')).toHaveTextContent('已斷線')
    expect(screen.getByRole('alert')).toHaveTextContent('已斷線')

    act(() => stream.open())
    act(() => stream.emit('status', status({ state: 'LIVE' })))
    expect(screen.getByTestId('feed-state')).toHaveTextContent('即時')
  })

  it('shows which exchange is currently used (AC6)', () => {
    const stream = renderLivePrice()
    act(() => stream.emit('status', status({ state: 'LIVE', activeSource: 'coinbase' })))
    expect(screen.getByTestId('active-source')).toHaveTextContent('Coinbase')

    act(() => stream.emit('status', status({ state: 'LIVE', activeSource: 'kraken' })))
    expect(screen.getByTestId('active-source')).toHaveTextContent('Kraken')
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
