import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { AlertTriggered } from '../api/types'
import { formatUsd } from '../format/format'
import { useLiveStream } from '../live/liveStreamContext'
import { notifyAlertEventsChanged } from './events'
import { describeAlert } from './format'

export const TOAST_MS = 10_000

/**
 * Pop-up notification for every alert pushed over the stream (AC7).
 *
 * "Read" rule (QA S4): a toast shown while the page is VISIBLE counts as seen, so the event is
 * marked read right away. If the tab is in the background, the alert waits - it stays unread
 * until the user comes back, then the toast is shown and the event marked read. Alerts that
 * fire while the page is closed stay unread and appear in the unread list on the next visit.
 */
export function AlertToasts({ toastMs = TOAST_MS }: { toastMs?: number }) {
  const { subscribeAlerts } = useLiveStream()
  const [toasts, setToasts] = useState<AlertTriggered[]>([])
  const pending = useRef<AlertTriggered[]>([])

  const show = useCallback((alert: AlertTriggered) => {
    setToasts((current) => [...current.filter((t) => t.eventId !== alert.eventId), alert])
    api.markEventRead(alert.eventId).then(notifyAlertEventsChanged).catch(() => undefined)
    setTimeout(() => setToasts((current) => current.filter((t) => t.eventId !== alert.eventId)), toastMs)
  }, [toastMs])

  useEffect(() => subscribeAlerts((alert) => {
    if (document.visibilityState === 'visible') {
      show(alert)
    } else {
      pending.current.push(alert)
      notifyAlertEventsChanged()   // the unread list picks it up meanwhile
    }
  }), [subscribeAlerts, show])

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        const waiting = pending.current
        pending.current = []
        waiting.forEach(show)
      }
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [show])

  return (
    <div className="toasts" aria-live="assertive">
      {toasts.map((t) => (
        <div key={t.eventId} className="toast" role="alert" data-testid={`toast-${t.eventId}`}>
          <strong>價格警示</strong>
          <div>{describeAlert(t.direction, t.threshold)}，目前 {formatUsd(t.price)}</div>
          <button type="button" aria-label="關閉通知"
                  onClick={() => setToasts((current) => current.filter((x) => x.eventId !== t.eventId))}>×</button>
        </div>
      ))}
    </div>
  )
}
