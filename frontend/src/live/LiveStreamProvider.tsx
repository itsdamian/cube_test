import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import type { AlertTriggered, FeedStatus, PriceEvent } from '../api/types'
import { browserEventSource, computeDisplay, type AlertListener, type EventSourceFactory } from './liveStream'
import { LiveStreamContext, type LiveStreamContextValue } from './liveStreamContext'

interface Props {
  children: ReactNode
  /** Injected in tests; the browser's EventSource otherwise. */
  eventSourceFactory?: EventSourceFactory
  /** How often the silence timer is re-evaluated. */
  tickMs?: number
}

/**
 * Owns the page's single connection to GET /api/stream (Server-Sent Events). The browser
 * reconnects an EventSource by itself after network errors; we only track whether it is
 * currently connected and when we last heard anything.
 */
export function LiveStreamProvider({ children, eventSourceFactory = browserEventSource, tickMs = 1_000 }: Props) {
  const [price, setPrice] = useState<PriceEvent | null>(null)
  const [status, setStatus] = useState<FeedStatus | null>(null)
  const [lastEventAt, setLastEventAt] = useState<number | null>(null)
  const [connectionLost, setConnectionLost] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  const alertListeners = useRef(new Set<AlertListener>())
  const priceListeners = useRef(new Set<(price: PriceEvent) => void>())

  useEffect(() => {
    // Show the last known price right away, before the first SSE event arrives.
    api.latestPrice()
      .then((latest) => {
        setPrice((current) => current ?? { pair: latest.pair, price: latest.price, source: latest.source, eventTime: latest.eventTime })
        if (latest.status) setStatus((current) => current ?? latest.status)
      })
      .catch(() => {
        // no price yet (404) or backend unreachable: the stream state will say so
      })

    const source = eventSourceFactory('/api/stream')
    const heard = () => {
      setLastEventAt(Date.now())
      setConnectionLost(false)
    }
    source.onopen = () => setConnectionLost(false)
    source.onerror = () => setConnectionLost(true)
    source.addEventListener('price', (event) => {
      const data = JSON.parse(event.data) as PriceEvent
      heard()
      setPrice(data)
      priceListeners.current.forEach((listener) => listener(data))
    })
    source.addEventListener('status', (event) => {
      heard()
      setStatus(JSON.parse(event.data) as FeedStatus)
    })
    source.addEventListener('alert', (event) => {
      heard()
      const alert = JSON.parse(event.data) as AlertTriggered
      alertListeners.current.forEach((listener) => listener(alert))
    })
    const timer = setInterval(() => setNow(Date.now()), tickMs)
    return () => {
      clearInterval(timer)
      source.close()
    }
  }, [eventSourceFactory, tickMs])

  const subscribeAlerts = useCallback((listener: AlertListener) => {
    alertListeners.current.add(listener)
    return () => {
      alertListeners.current.delete(listener)
    }
  }, [])

  const subscribePrices = useCallback((listener: (price: PriceEvent) => void) => {
    priceListeners.current.add(listener)
    return () => {
      priceListeners.current.delete(listener)
    }
  }, [])

  const value = useMemo<LiveStreamContextValue>(() => {
    const base = { price, status, lastEventAt, connectionLost }
    return { ...base, display: computeDisplay(base, now), subscribeAlerts, subscribePrices }
  }, [price, status, lastEventAt, connectionLost, now, subscribeAlerts, subscribePrices])

  return <LiveStreamContext.Provider value={value}>{children}</LiveStreamContext.Provider>
}
