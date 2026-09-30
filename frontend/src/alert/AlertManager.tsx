import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { api, ApiError } from '../api/client'
import type { Alert, Direction } from '../api/types'
import { formatDateTime, formatUsd } from '../format/format'
import { useOptionalLiveStream } from '../live/liveStreamContext'
import { ALERT_EVENTS_CHANGED, notifyAlertEventsChanged } from './events'
import { DIRECTION_LABEL } from './format'

const signed = new Intl.NumberFormat('zh-TW', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** "還差 +5,954.50" = threshold − current price (user decision, task 31). */
function describeDistance(threshold: number, price: number): string {
  const d = threshold - price
  return `還差 ${d >= 0 ? '+' : '−'}${signed.format(Math.abs(d))}`
}

/**
 * The distance of one alert to the live price. Its own component (reading the stream itself), so
 * only these few spans re-render on every price, not the whole alert card.
 */
function Distance({ threshold }: { threshold: number }) {
  const price = useOptionalLiveStream()?.price
  if (!price) return null
  return <><span className="num" data-testid="alert-distance">{describeDistance(threshold, price.price)}</span> · </>
}

/** "目前 US$84,045.50 · 填入目前價格" under the form (rounded to whole dollars when filled in). */
function CurrentPriceHint({ onFill }: { onFill: (value: string) => void }) {
  const price = useOptionalLiveStream()?.price
  if (!price) return null
  return (
    <p className="hint">
      目前 <span className="num">{formatUsd(price.price)}</span> ·{' '}
      <button className="btn btn--link" type="button" onClick={() => onFill(String(Math.round(price.price)))}>填入目前價格</button>
    </p>
  )
}

interface Props {
  /** Shown between the form and the list (the unread alerts, in the page). */
  children?: ReactNode
}

/**
 * Create ("BTC-USD 高於 90,000") and delete price alerts. Alerts are evaluated by the backend.
 * The form is collapsed behind the header's 新增警示 button and stays open after adding, so
 * several alerts can be entered in a row.
 */
export function AlertManager({ children }: Props) {
  const [alerts, setAlerts] = useState<Alert[] | null>(null)
  const [formOpen, setFormOpen] = useState(false)
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
    <>
      <header className="card__head">
        <h2 id="alert-title" className="card__title">價格警示</h2>
        <button className="btn btn--sm" type="button" aria-expanded={formOpen} aria-controls="alert-form"
                onClick={() => setFormOpen((open) => !open)}>
          <span aria-hidden="true">＋</span>新增警示
        </button>
      </header>
      <div className="card__body">
        <form className="add-form" id="alert-form" onSubmit={create} aria-label="新增警示" noValidate hidden={!formOpen}>
          <div className="seg seg--dir" role="radiogroup" aria-label="條件">
            {(['ABOVE', 'BELOW'] as const).map((d) => (
              <span key={d}>
                <input type="radio" name="direction" id={`alert-dir-${d}`} value={d}
                       checked={direction === d} onChange={() => setDirection(d)} />
                <label htmlFor={`alert-dir-${d}`}>
                  <span aria-hidden="true">{d === 'ABOVE' ? '▲' : '▼'}</span>價格{DIRECTION_LABEL[d]}
                </label>
              </span>
            ))}
          </div>
          <div className="row">
            <label className="field">
              門檻價格
              <span className="control">
                <input type="number" inputMode="decimal" min="0" step="any" value={threshold} placeholder="例如 85000"
                       aria-invalid={error ? true : undefined} onChange={(e) => setThreshold(e.target.value)} />
                <span className="suffix" aria-hidden="true">USD</span>
              </span>
            </label>
            <button className="btn btn--primary" type="submit">建立</button>
          </div>
          {error && <p className="field-error" role="alert">{error}</p>}
          <CurrentPriceHint onFill={setThreshold} />
        </form>

        {children}

        {alerts && alerts.length === 0 && (
          <div className="empty">
            <span className="ico" aria-hidden="true">🔔</span>
            <strong>尚未設定任何警示</strong>
            設定一個門檻，價格越過時會在這裡通知你。
          </div>
        )}
        {alerts && alerts.length > 0 && (
          <ul className="alist" aria-label="已設定的警示">
            {alerts.map((a) => {
              const up = a.direction === 'ABOVE'
              const confirming = confirmDelete === a.id
              return (
                <li key={a.id} data-testid={`alert-${a.id}`} className={confirming ? 'is-confirming' : undefined}>
                  <span className={`dir dir--${up ? 'up' : 'down'}`} aria-hidden="true">{up ? '▲' : '▼'}</span>
                  <span className="info">
                    <span className="cond">
                      <span className="visually-hidden">BTC-USD </span>{DIRECTION_LABEL[a.direction]} <span className="num">{formatUsd(a.threshold)}</span>
                    </span>
                    <span className="sub">
                      <Distance threshold={a.threshold} />
                      {a.lastTriggeredAt ? <>上次觸發 {formatDateTime(a.lastTriggeredAt)}</> : '尚未觸發'}
                    </span>
                  </span>
                  <span className="acts">
                    {confirming ? (
                      <>
                        <button type="button" className="btn btn--danger btn--sm" onClick={() => void remove(a.id)}>確定刪除？</button>
                        <button type="button" className="btn btn--sm" onClick={() => setConfirmDelete(null)}>取消</button>
                      </>
                    ) : (
                      <button type="button" className="btn btn--quiet btn--sm" onClick={() => setConfirmDelete(a.id)}
                              aria-label={`刪除警示 ${a.id}`}>刪除</button>
                    )}
                  </span>
                </li>
              )
            })}
          </ul>
        )}
        <p className="side-note">警示由後端判斷：頁面關閉時觸發的警示，下次打開會列為「離開期間觸發」。同一警示觸發後冷卻 5 分鐘。</p>
      </div>
    </>
  )
}
