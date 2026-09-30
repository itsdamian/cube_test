import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { AlertEvent } from '../api/types'
import { formatDateTime, formatUsd } from '../format/format'
import { ALERT_EVENTS_CHANGED, notifyAlertEventsChanged } from './events'
import { describeAlert } from './format'

/**
 * Alerts that fired while nobody was looking (spec 14a / AC8): loaded when the page opens,
 * each can be marked read, or all at once.
 */
export function UnreadAlerts() {
  const [events, setEvents] = useState<AlertEvent[]>([])

  useEffect(() => {
    let cancelled = false
    const load = () => {
      api.alertEvents(true).then((list) => !cancelled && setEvents(list)).catch(() => undefined)
    }
    load()
    window.addEventListener(ALERT_EVENTS_CHANGED, load)
    return () => {
      cancelled = true
      window.removeEventListener(ALERT_EVENTS_CHANGED, load)
    }
  }, [])

  // Nothing unread: the block is not shown at all (design direction B).
  if (events.length === 0) {
    return null
  }

  const markRead = async (id: number) => {
    await api.markEventRead(id)
    setEvents((current) => current.filter((e) => e.id !== id))
    notifyAlertEventsChanged()
  }

  const markAll = async () => {
    await api.markAllEventsRead()
    setEvents([])
    notifyAlertEventsChanged()
  }

  return (
    <section className="unread" aria-labelledby="unread-title">
      <div className="unread__head">
        <span className="ico" aria-hidden="true">●</span>
        <h3 id="unread-title" className="unread__title">離開期間觸發 {events.length} 則</h3>
        <button className="btn btn--link" type="button" onClick={() => void markAll()}>全部標記已讀</button>
      </div>
      <ul>
        {events.map((e) => (
          <li key={e.id} data-testid={`unread-${e.id}`}>
            <span className="what">
              {describeAlert(e.direction, e.threshold)}
              <small>{formatDateTime(e.triggeredAt)} 觸發 · 當時 {formatUsd(e.price)}</small>
            </span>
            <button className="btn btn--sm" type="button" onClick={() => void markRead(e.id)} aria-label={`標記已讀 ${e.id}`}>已讀</button>
          </li>
        ))}
      </ul>
    </section>
  )
}
