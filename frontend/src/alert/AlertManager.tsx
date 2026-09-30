import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '../api/client'
import type { Alert, Direction } from '../api/types'
import { formatDateTime } from '../format/format'
import { ALERT_EVENTS_CHANGED, notifyAlertEventsChanged } from './events'
import { describeAlert } from './format'

/** Create ("BTC-USD 高於 90,000") and delete price alerts. Alerts are evaluated by the backend. */
export function AlertManager() {
  const [alerts, setAlerts] = useState<Alert[] | null>(null)
  const [direction, setDirection] = useState<Direction>('ABOVE')
  const [threshold, setThreshold] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null)

  const reload = useCallback(async () => {
    try {
      setAlerts(await api.alerts())
    } catch {
      setError('無法載入警示')
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    const load = () => {
      api.alerts().then((list) => !cancelled && setAlerts(list)).catch(() => !cancelled && setError('無法載入警示'))
    }
    load()
    window.addEventListener(ALERT_EVENTS_CHANGED, load)   // "last triggered" changes when an alert fires
    return () => {
      cancelled = true
      window.removeEventListener(ALERT_EVENTS_CHANGED, load)
    }
  }, [])

  const create = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    const value = Number(threshold)
    if (!threshold || !Number.isFinite(value) || value <= 0) {
      setError('請輸入大於 0 的價格')
      return
    }
    try {
      await api.createAlert(direction, value)
      setThreshold('')
      await reload()
    } catch (e) {
      setError(e instanceof ApiError && e.status === 400 ? '門檻格式不正確' : '新增失敗，請稍後再試')
    }
  }

  const remove = async (id: number) => {
    try {
      await api.deleteAlert(id)
      setConfirmDelete(null)
      await reload()
      // The backend deletes the alert's events too (ON DELETE CASCADE): refresh the unread list.
      notifyAlertEventsChanged()
    } catch {
      setError('刪除失敗，請稍後再試')
    }
  }

  return (
    <div>
      <form className="currency-form" onSubmit={create} aria-label="新增警示">
        <label>
          條件
          <select value={direction} onChange={(e) => setDirection(e.target.value as Direction)}>
            <option value="ABOVE">價格高於</option>
            <option value="BELOW">價格低於</option>
          </select>
        </label>
        <label>
          門檻（USD）
          <input type="number" inputMode="decimal" min="0" step="any" value={threshold} placeholder="90000"
                 onChange={(e) => setThreshold(e.target.value)} />
        </label>
        <button type="submit">新增警示</button>
        {error && <span className="field-error" role="alert">{error}</span>}
      </form>
      {alerts && alerts.length === 0 && <p className="muted">尚未設定任何警示</p>}
      {alerts && alerts.length > 0 && (
        <div className="table-scroll">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">條件</th>
              <th scope="col">上次觸發</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            {alerts.map((a) => (
              <tr key={a.id} data-testid={`alert-${a.id}`}>
                <td>{describeAlert(a.direction, a.threshold)}</td>
                <td>{a.lastTriggeredAt ? formatDateTime(a.lastTriggeredAt) : '尚未觸發'}</td>
                <td className="actions">
                  {confirmDelete === a.id ? (
                    <>
                      <button type="button" className="danger" onClick={() => void remove(a.id)}>確定刪除？</button>
                      <button type="button" onClick={() => setConfirmDelete(null)}>取消</button>
                    </>
                  ) : (
                    <button type="button" onClick={() => setConfirmDelete(a.id)} aria-label={`刪除警示 ${a.id}`}>刪除</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      )}
    </div>
  )
}
