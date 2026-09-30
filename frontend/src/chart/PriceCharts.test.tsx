import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { LiveStreamProvider } from '../live/LiveStreamProvider'
import { fakeChartFactory } from '../test/fakeChart'
import { FakeEventSource } from '../test/fakeEventSource'
import { samples } from '../test/msw/samples'
import { server } from '../test/msw/server'
import { PriceCharts } from './PriceCharts'

function setup() {
  const requests: URL[] = []
  server.use(
    http.get('/api/candles', ({ request }) => {
      requests.push(new URL(request.url))
      return HttpResponse.json(samples.candles)
    }),
    http.get('/api/prices/trend', ({ request }) => {
      requests.push(new URL(request.url))
      return HttpResponse.json(samples.pricesTrend)
    }),
  )
  const { factory, charts } = fakeChartFactory()
  const view = render(
    <LiveStreamProvider eventSourceFactory={FakeEventSource.factory}>
      <PriceCharts chartFactory={factory} />
    </LiveStreamProvider>,
  )
  return { requests, chart: () => charts[0], stream: FakeEventSource.latest(), view }
}

describe('PriceCharts (requirement 11)', () => {
  afterEach(() => {
    FakeEventSource.instances = []
  })

  it('starts with 1-minute candles from /api/candles?interval=1m', async () => {
    const { requests, chart } = setup()
    await waitFor(() => expect(chart().candles).toHaveLength(1))
    expect(requests[0].pathname).toBe('/api/candles')
    expect(requests[0].searchParams.get('interval')).toBe('1m')
    expect(chart().candles[0]).toHaveLength(2)
    expect(screen.getByRole('tab', { name: '1 分 K 線' })).toHaveAttribute('aria-selected', 'true')
  })

  it('switches to 5-minute candles', async () => {
    const { requests, chart } = setup()
    await waitFor(() => expect(chart().candles).toHaveLength(1))

    await userEvent.click(screen.getByRole('tab', { name: '5 分 K 線' }))

    await waitFor(() => expect(chart().candles).toHaveLength(2))
    expect(requests[requests.length - 1].searchParams.get('interval')).toBe('5m')
  })

  it('shows the trend from /api/prices/trend and extends it with live prices', async () => {
    const { requests, chart, stream } = setup()
    await userEvent.click(screen.getByRole('tab', { name: /走勢/ }))

    await waitFor(() => expect(chart().lines).toHaveLength(1))
    const trendRequest = requests.find((r) => r.pathname === '/api/prices/trend')!
    expect(trendRequest.searchParams.get('points')).toBe('300')
    expect(Date.parse(trendRequest.searchParams.get('to')!) - Date.parse(trendRequest.searchParams.get('from')!))
      .toBe(60 * 60_000)
    expect(chart().lines[0]).toHaveLength(2)

    act(() => stream.emit('price', { ...samples.ssePrice, eventTime: '2026-09-29T08:00:09Z', price: 84100 }))
    expect(chart().appended).toEqual([{ time: 1790668809, value: 84100 }])

    act(() => stream.emit('price', { ...samples.ssePrice, eventTime: '2026-09-29T08:00:01Z', price: 1 }))
    expect(chart().appended).toHaveLength(1)   // older than the line's end: ignored
  })

  it('live prices do not touch the candle view', async () => {
    const { chart, stream } = setup()
    await waitFor(() => expect(chart().candles).toHaveLength(1))
    act(() => stream.emit('price', samples.ssePrice))
    expect(chart().appended).toHaveLength(0)
  })

  it('explains an empty chart and disposes the chart on unmount', async () => {
    server.use(http.get('/api/candles', () => HttpResponse.json([])))
    const { factory, charts } = fakeChartFactory()
    const { unmount } = render(
      <LiveStreamProvider eventSourceFactory={FakeEventSource.factory}>
        <PriceCharts chartFactory={factory} />
      </LiveStreamProvider>,
    )
    expect(await screen.findByText(/尚無資料/)).toBeInTheDocument()
    unmount()
    expect(charts[0].disposed).toBe(true)
  })
})
