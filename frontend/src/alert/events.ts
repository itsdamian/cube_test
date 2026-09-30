/** Fired when the set of unread alert events may have changed (new alert, marked read...). */
export const ALERT_EVENTS_CHANGED = 'alert-events-changed'

export function notifyAlertEventsChanged(): void {
  window.dispatchEvent(new Event(ALERT_EVENTS_CHANGED))
}
