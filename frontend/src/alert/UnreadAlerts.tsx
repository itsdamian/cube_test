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

  if (events.length === 0) {
    return <p className="muted">沒有未讀警示</p>
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
    <div className="unread" aria-label="未讀警示">
      <div className="unread__header">
        <strong>未讀警示（{events.length}）</strong>
        <button type="button" onClick={() => void markAll()}>全部標記已讀</button>
      </div>
      <ul>
        {events.map((e) => (
          <li key={e.id} data-testid={`unread-${e.id}`}>
            {describeAlert(e.direction, e.threshold)}：於 {formatDateTime(e.triggeredAt)} 觸發，當時價格 {formatUsd(e.price)}
            <button type="button" onClick={() => void markRead(e.id)} aria-label={`標記已讀 ${e.id}`}>標記已讀</button>
          </li>
        ))}
      </ul>
    </div>
  )
}
