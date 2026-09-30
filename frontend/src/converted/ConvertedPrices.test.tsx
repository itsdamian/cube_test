import { act, render, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { notifyCurrenciesChanged } from '../currency/events'
import { LiveStreamProvider } from '../live/LiveStreamProvider'
import { FakeEventSource } from '../test/fakeEventSource'
import { samples } from '../test/msw/samples'
import { server } from '../test/msw/server'
import { ConvertedPrices } from './ConvertedPrices'

function renderTable() {
  render(
    <LiveStreamProvider eventSourceFactory={FakeEventSource.factory}>
      <ConvertedPrices />
    </LiveStreamProvider>,
  )
  return FakeEventSource.latest()
}

describe('ConvertedPrices (AC3 frontend)', () => {
  afterEach(() => {
    FakeEventSource.instances = []
  })

  it('shows Chinese name, price, rate and the rate update time for each currency', async () => {
    renderTable()
    const twd = await screen.findByTestId('converted-TWD')
    expect(within(twd).getByText('新台幣')).toBeInTheDocument()
    expect(within(twd).getByText('31.84071')).toBeInTheDocument()
    // price = latest USD price (84,045.50 from /api/prices/latest) x rate
    expect(twd).toHaveTextContent('2,676,068.39')
    // One line under the table (all rates share the provider's update time).
    const updated = within(screen.getByTestId('rates-updated')).getByText((_, el) => el?.tagName === 'TIME')
    expect(updated).toHaveAttribute('dateTime', '2026-09-30T00:02:31Z')
    expect(screen.getByTestId('rates-updated')).toHaveTextContent('匯率更新於')

    expect(within(screen.getByTestId('converted-EUR')).getByText('歐元')).toBeInTheDocument()
  })

  it('marks a currency without a known rate as 無匯率', async () => {
    renderTable()
    const xau = await screen.findByTestId('converted-XAU')
    expect(within(xau).getByText('黃金')).toBeInTheDocument()
    expect(within(xau).getByText('無匯率')).toBeInTheDocument()
  })

  it('shows the attribution link required by the rate provider', async () => {
    renderTable()
    const link = await screen.findByRole('link', { name: 'Rates By Exchange Rate API' })
    expect(link).toHaveAttribute('href', 'https://www.exchangerate-api.com')
  })

  it('recomputes the prices from the live BTC-USD price', async () => {
    const stream = renderTable()
    await screen.findByTestId('converted-TWD')

    act(() => stream.emit('price', { ...samples.ssePrice, price: 100000 }))

    expect(screen.getByTestId('converted-TWD')).toHaveTextContent('3,184,071.00')
  })

  it('reloads at once when a currency was added, renamed or deleted', async () => {
    let requests = 0
    server.use(http.get('/api/prices/converted', () => {
      requests++
      return HttpResponse.json(samples.pricesConverted)
    }))
    renderTable()
    await screen.findByTestId('converted-TWD')
    expect(requests).toBe(1)

    act(() => notifyCurrenciesChanged())

    await waitFor(() => expect(requests).toBe(2))
  })

  it('says so when there is no price yet', async () => {
    server.use(
      http.get('/api/prices/converted', () => HttpResponse.json({ status: 404, detail: 'No price' }, { status: 404 })),
      http.get('/api/prices/latest', () => new HttpResponse(null, { status: 404 })),
    )
    renderTable()
    expect(await screen.findByText('尚無價格資料')).toBeInTheDocument()
  })
})
