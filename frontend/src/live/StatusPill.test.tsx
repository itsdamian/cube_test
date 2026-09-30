import { act, render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { FakeEventSource } from '../test/fakeEventSource'
import { samples } from '../test/msw/samples'
import { server } from '../test/msw/server'
import { LiveStreamProvider } from './LiveStreamProvider'
import { StatusPill } from './StatusPill'

describe('StatusPill (header)', () => {
  beforeEach(() => {
    server.use(http.get('/api/prices/latest', () => new HttpResponse(null, { status: 404 })))
  })
  afterEach(() => {
    FakeEventSource.instances = []
  })

  it('shows the feed state and the active exchange, with a matching colour class', () => {
    render(
      <LiveStreamProvider eventSourceFactory={FakeEventSource.factory}>
        <StatusPill />
      </LiveStreamProvider>,
    )
    const stream = FakeEventSource.latest()
    const pill = screen.getByTestId('header-feed-state')
    expect(pill).toHaveTextContent('連線中')

    act(() => stream.emit('status', { ...samples.sseStatus, state: 'LIVE', activeSource: 'kraken' }))
    expect(pill).toHaveTextContent('即時')
    expect(pill).toHaveTextContent('Kraken')
    expect(pill).toHaveClass('status-pill--live')
    expect(pill).toHaveAccessibleName('資料狀態：即時')

    act(() => stream.emit('status', { ...samples.sseStatus, state: 'STALE' }))
    expect(pill).toHaveTextContent('資料延遲')
    expect(pill).toHaveClass('status-pill--delayed')

    act(() => stream.emit('status', { ...samples.sseStatus, state: 'DISCONNECTED' }))
    expect(pill).toHaveClass('status-pill--disconnected')
  })
})
