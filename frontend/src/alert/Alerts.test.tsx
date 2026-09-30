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
    expect(screen.getByText('未讀警示（1）')).toBeInTheDocument()
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
    expect(await screen.findByText('沒有未讀警示')).toBeInTheDocument()
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
    expect(toast).toHaveTextContent('BTC-USD 高於')
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

    await user.selectOptions(screen.getByLabelText('條件'), 'BELOW')
    await user.type(screen.getByLabelText('門檻（USD）'), '80000')
    await user.click(screen.getByRole('button', { name: '新增警示' }))

    expect(await screen.findByTestId('alert-50')).toHaveTextContent('BTC-USD 低於')
    expect(posted).toEqual([{ direction: 'BELOW', threshold: 80000 }])
  })

  it('rejects an empty or non-positive threshold without calling the API', async () => {
    const { posted } = fakeAlertsBackend()
    const user = userEvent.setup()
    render(<AlertManager />)
    await screen.findByTestId('alert-3')
    await user.click(screen.getByRole('button', { name: '新增警示' }))
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
