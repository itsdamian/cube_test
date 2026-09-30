import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { Alert, AlertTriggered } from '../api/types'
import { LiveStreamProvider } from '../live/LiveStreamProvider'
import { FakeEventSource } from '../test/fakeEventSource'
import { samples } from '../test/msw/samples'
import { server } from '../test/msw/server'
import { AlertManager } from './AlertManager'
import { AlertToasts } from './AlertToasts'
import { UnreadAlerts } from './UnreadAlerts'

function setVisibility(state: 'visible' | 'hidden') {
  Object.defineProperty(document, 'visibilityState', { value: state, configurable: true })
  document.dispatchEvent(new Event('visibilitychange'))
}

/** A small stateful backend for alert events: records reads, and read events leave the unread list. */
function recordReads() {
  const reads: string[] = []
  let unread = structuredClone(samples.alertEvents)
  server.use(
    http.get('/api/alert-events', () => HttpResponse.json(unread)),
    http.post('/api/alert-events/:id/read', ({ params }) => {
      reads.push(String(params.id))
      unread = unread.filter((e) => e.id !== Number(params.id))
      return new HttpResponse(null, { status: 204 })
    }),
    http.post('/api/alert-events/read-all', () => {
      reads.push('all')
      unread = []
      return new HttpResponse(null, { status: 204 })
    }),
  )
  return {
    reads,
    reset: () => {
      unread = structuredClone(samples.alertEvents)
    },
  }
}

describe('UnreadAlerts (AC8)', () => {
  it('shows alerts that fired while the page was closed', async () => {
    render(<UnreadAlerts />)
    const item = await screen.findByTestId('unread-17')
    expect(item).toHaveTextContent('BTC-USD 高於')
    expect(item).toHaveTextContent('90,000.00')
    expect(item).toHaveTextContent('90,012.34')
    expect(screen.getByRole('heading', { name: '離開期間觸發 1 則' })).toBeInTheDocument()
  })

  it('asks the backend for unread events only', async () => {
    let unread: string | null = null
    server.use(http.get('/api/alert-events', ({ request }) => {
      unread = new URL(request.url).searchParams.get('unread')
      return HttpResponse.json(samples.alertEvents)
    }))
    render(<UnreadAlerts />)
    await screen.findByTestId('unread-17')
    expect(unread).toBe('true')
  })

  it('marks one event read, or all of them', async () => {
    const { reads, reset } = recordReads()
    const user = userEvent.setup()
    const { unmount } = render(<UnreadAlerts />)
    await user.click(await screen.findByRole('button', { name: '標記已讀 17' }))
    await waitFor(() => expect(screen.queryByTestId('unread-17')).not.toBeInTheDocument())
    expect(reads).toEqual(['17'])
    unmount()

    reset()   // event 17 unread again, to try "read all"
    render(<UnreadAlerts />)
    await user.click(await screen.findByRole('button', { name: '全部標記已讀' }))
    await waitFor(() => expect(reads).toContain('all'))
    // Nothing unread: the whole block disappears (design direction B; was the text 沒有未讀警示).
    await waitFor(() => expect(screen.queryByRole('heading', { name: /離開期間觸發/ })).not.toBeInTheDocument())
    expect(screen.queryByTestId('unread-17')).not.toBeInTheDocument()
  })
})

describe('AlertToasts (AC7, QA S4)', () => {
  const alert: AlertTriggered = samples.sseAlert

  beforeEach(() => setVisibility('visible'))
  afterEach(() => {
    FakeEventSource.instances = []
    setVisibility('visible')
  })

  function renderToasts() {
    render(
      <LiveStreamProvider eventSourceFactory={FakeEventSource.factory}>
        <AlertToasts />
      </LiveStreamProvider>,
    )
    return FakeEventSource.latest()
  }

  it('pops up a notification and marks it read when the page is visible', async () => {
    const { reads } = recordReads()
    const stream = renderToasts()

    act(() => stream.emit('alert', alert))

    const toast = await screen.findByTestId('toast-17')
    expect(toast).toHaveTextContent('價格警示')
    expect(toast).toHaveTextContent('BTC-USD 已高於 US$90,000.00')
    expect(toast).toHaveTextContent('90,012.34')
    await waitFor(() => expect(reads).toEqual(['17']))
  })

  it('in a background tab it waits: not marked read until the page is visible again', async () => {
    const { reads } = recordReads()
    const stream = renderToasts()
    setVisibility('hidden')

    act(() => stream.emit('alert', alert))
    await new Promise((r) => setTimeout(r, 50))
    expect(reads).toEqual([])
    expect(screen.queryByTestId('toast-17')).not.toBeInTheDocument()

    act(() => setVisibility('visible'))

    expect(await screen.findByTestId('toast-17')).toBeInTheDocument()
    await waitFor(() => expect(reads).toEqual(['17']))
  })

  it('can be closed', async () => {
    recordReads()
    const user = userEvent.setup()
    const stream = renderToasts()
    act(() => stream.emit('alert', alert))
    await user.click(within(await screen.findByTestId('toast-17')).getByRole('button', { name: '關閉通知' }))
    expect(screen.queryByTestId('toast-17')).not.toBeInTheDocument()
  })
})

describe('AlertManager', () => {
  function fakeAlertsBackend() {
    let store: Alert[] = structuredClone(samples.alerts)
    const posted: unknown[] = []
    const deleted: string[] = []
    server.use(
      http.get('/api/alerts', () => HttpResponse.json(store)),
      http.post('/api/alerts', async ({ request }) => {
        const body = (await request.json()) as { direction: 'ABOVE' | 'BELOW'; threshold: number }
        posted.push(body)
        const created: Alert = { id: 50, pair: 'BTC-USD', ...body, createdAt: '2026-09-30T00:00:00Z', lastTriggeredAt: null }
        store = [...store, created]
        return HttpResponse.json(created, { status: 201 })
      }),
      http.delete('/api/alerts/:id', ({ params }) => {
        deleted.push(String(params.id))
        store = store.filter((a) => a.id !== Number(params.id))
        return new HttpResponse(null, { status: 204 })
      }),
    )
    return { posted, deleted }
  }

  it('lists alerts with their condition and last trigger', async () => {
    fakeAlertsBackend()
    render(<AlertManager />)
    expect(await screen.findByTestId('alert-3')).toHaveTextContent('BTC-USD 高於')
    expect(screen.getByTestId('alert-4')).toHaveTextContent('BTC-USD 低於')
    expect(screen.getByTestId('alert-4')).toHaveTextContent('尚未觸發')
  })

  it('creates a "below" alert', async () => {
    const { posted } = fakeAlertsBackend()
    const user = userEvent.setup()
    render(<AlertManager />)
    await screen.findByTestId('alert-3')

    // The form is collapsed behind the header button (aria-expanded) and stays open after adding.
    const toggle = screen.getByRole('button', { name: '新增警示' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('form', { name: '新增警示' })).not.toBeInTheDocument()
    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await user.click(within(screen.getByRole('radiogroup', { name: '條件' })).getByRole('radio', { name: '價格低於' }))
    await user.type(screen.getByRole('spinbutton', { name: '門檻價格' }), '80000')
    await user.click(screen.getByRole('button', { name: '建立' }))

    expect(await screen.findByTestId('alert-50')).toHaveTextContent('BTC-USD 低於')
    expect(posted).toEqual([{ direction: 'BELOW', threshold: 80000 }])
    expect(screen.getByRole('form', { name: '新增警示' })).toBeVisible()
    expect(screen.getByRole('spinbutton', { name: '門檻價格' })).toHaveValue(null)
  })

  it('rejects an empty or non-positive threshold without calling the API', async () => {
    const { posted } = fakeAlertsBackend()
    const user = userEvent.setup()
    render(<AlertManager />)
    await screen.findByTestId('alert-3')
    await user.click(screen.getByRole('button', { name: '新增警示' }))
    await user.click(screen.getByRole('button', { name: '建立' }))
    expect(screen.getByRole('alert')).toHaveTextContent('請輸入大於 0 的價格')
    await user.type(screen.getByRole('spinbutton', { name: '門檻價格' }), '-5')
    await user.click(screen.getByRole('button', { name: '建立' }))
    expect(screen.getByRole('alert')).toHaveTextContent('請輸入大於 0 的價格')
    expect(posted).toEqual([])
  })

  it('deletes an alert after confirmation', async () => {
    const { deleted } = fakeAlertsBackend()
    const user = userEvent.setup()
    render(<AlertManager />)
    await user.click(await screen.findByRole('button', { name: '刪除警示 3' }))
    await user.click(screen.getByRole('button', { name: '確定刪除？' }))
    await waitFor(() => expect(screen.queryByTestId('alert-3')).not.toBeInTheDocument())
    expect(deleted).toEqual(['3'])
  })
})

describe('AlertManager with the live price (task 31)', () => {
  afterEach(() => {
    FakeEventSource.instances = []
  })

  it('shows how far each alert is from the price, or that its condition already holds, and fills in the rounded price', async () => {
    server.use(http.get('/api/alerts', () => HttpResponse.json(samples.alerts)))
    const user = userEvent.setup()
    render(
      <LiveStreamProvider eventSourceFactory={FakeEventSource.factory}>
        <AlertManager />
      </LiveStreamProvider>,
    )
    const stream = FakeEventSource.latest()
    await screen.findByTestId('alert-3')
    act(() => stream.emit('price', { ...samples.ssePrice, price: 84045.5 }))

    const distance = (id: number) => within(screen.getByTestId(`alert-${id}`)).getByTestId('alert-distance')
    // Not met: alert 3 above 90,000 / alert 4 below 80,000.50, price 84,045.50 -> 還差 (no sign).
    expect(distance(3)).toHaveTextContent(/^還差 5,954.50$/)
    expect(distance(4)).toHaveTextContent(/^還差 4,045.00$/)

    // Met (same strict rule as the backend): 已高於 / 已低於 by how much.
    act(() => stream.emit('price', { ...samples.ssePrice, price: 90135.57 }))
    expect(distance(3)).toHaveTextContent(/^已高於 135.57$/)
    act(() => stream.emit('price', { ...samples.ssePrice, price: 80000 }))
    expect(distance(4)).toHaveTextContent(/^已低於 0.50$/)
    // Exactly at the threshold is not "above" yet (backend: price > threshold).
    act(() => stream.emit('price', { ...samples.ssePrice, price: 90000 }))
    expect(distance(3)).toHaveTextContent(/^還差 0.00$/)

    await user.click(screen.getByRole('button', { name: '新增警示' }))
    act(() => stream.emit('price', { ...samples.ssePrice, price: 84045.5 }))
    await user.click(screen.getByRole('button', { name: '填入目前價格' }))
    expect(screen.getByRole('spinbutton', { name: '門檻價格' })).toHaveValue(84046)   // rounded
  })
})

describe('AlertManager + UnreadAlerts together', () => {
  it('deleting an alert that has unread events removes them from the unread list (team lead bug)', async () => {
    // Stateful backend: deleting alert 3 cascades to its events, like the database does.
    let alerts = structuredClone(samples.alerts)
    let events = structuredClone(samples.alertEvents)   // event 17 belongs to alert 3
    server.use(
      http.get('/api/alerts', () => HttpResponse.json(alerts)),
      http.get('/api/alert-events', () => HttpResponse.json(events)),
      http.delete('/api/alerts/:id', ({ params }) => {
        alerts = alerts.filter((a) => a.id !== Number(params.id))
        events = events.filter((e) => e.alertId !== Number(params.id))
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const user = userEvent.setup()
    render(<><UnreadAlerts /><AlertManager /></>)
    expect(await screen.findByTestId('unread-17')).toBeInTheDocument()

    await user.click(await screen.findByRole('button', { name: '刪除警示 3' }))
    await user.click(screen.getByRole('button', { name: '確定刪除？' }))

    await waitFor(() => expect(screen.queryByTestId('unread-17')).not.toBeInTheDocument())
    expect(screen.queryByRole('heading', { name: /離開期間觸發/ })).not.toBeInTheDocument()
  })
})
