import { useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'
import type { ConvertedPrices as Converted } from '../api/types'
import { CURRENCIES_CHANGED } from '../currency/events'
import { formatDateTime, formatMoney } from '../format/format'
import { useLiveStream } from '../live/liveStreamContext'

/** Rates change at most daily; the table re-reads them (and the currency list) every minute. */
export const REFRESH_MS = 60_000

/** USD first (the base currency); everything else keeps the API's order (user decision, task 31). */
function usdFirst<T extends { code: string }>(items: T[]): T[] {
  return [...items.filter((i) => i.code === 'USD'), ...items.filter((i) => i.code !== 'USD')]
}

interface Props {
  refreshMs?: number
  /** Opens the currency management dialog (the button sits in this card's header). */
  onManage?: (trigger: HTMLButtonElement) => void
}

/**
 * BTC price in every currency of the database, with the exchange rate used and when the
 * provider last updated it. The rates come from the backend; the price is multiplied locally
 * with the live BTC-USD price from the stream, so the tiles move with the ticker.
 */
export function ConvertedPrices({ refreshMs = REFRESH_MS, onManage }: Props) {
  const { price } = useLiveStream()
  const [data, setData] = useState<Converted | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const load = () =>
      api.convertedPrices()
        .then((result) => {
          if (!cancelled) {
            setData(result)
            setError(null)
          }
        })
        .catch((e: unknown) => {
          if (!cancelled) {
            setError(e instanceof ApiError && e.status === 404 ? '尚無價格資料' : '無法取得換算資料')
          }
        })
    load()
    const timer = setInterval(load, refreshMs)
    window.addEventListener(CURRENCIES_CHANGED, load)   // a currency was added/renamed/deleted
    return () => {
      cancelled = true
      clearInterval(timer)
      window.removeEventListener(CURRENCIES_CHANGED, load)
    }
  }, [refreshMs])

  const usd = data ? price?.price ?? data.usdPrice : null
  // The provider updates all rates together; if they ever differ, show the oldest (most cautious).
  const rateTimes = (data?.items ?? []).map((i) => i.rateUpdatedAt).filter((t): t is string => t !== null).sort()
  const oldestRate = rateTimes.length ? rateTimes[0] : null
  return (
    <>
      <header className="card__head">
        <div className="card__title">
          <h2 id="converted-title">多幣別換算</h2>
          <span className="card__sub" id="converted-note">
            1 BTC 以各幣別計價{usd !== null && `（依 BTC-USD ${formatMoney(usd, 'USD')} 換算）`}
          </span>
        </div>
        <div className="fx__meta">
          {oldestRate && (
            <span data-testid="rates-updated">匯率更新於 <time dateTime={oldestRate}>{formatDateTime(oldestRate)}</time></span>
          )}
          {onManage && (
            <button className="btn btn--sm" type="button" aria-haspopup="dialog"
                    onClick={(e) => onManage(e.currentTarget)}>管理幣別</button>
          )}
        </div>
      </header>
      <div className="card__body">
        {data && usd !== null ? (
          <ul className="tiles" aria-describedby="converted-note">
            {usdFirst(data.items).map((item) => (
              <li key={item.currencyId} className={`tile${item.rate === null ? ' tile--none' : ''}`} data-testid={`converted-${item.code}`}>
                <div className="tile__top"><span className="tile__code">{item.code}</span><span className="tile__name">{item.name}</span></div>
                {item.rate === null ? (
                  <div className="tile__value">無匯率</div>
                ) : (
                  <>
                    <div className="tile__value sk" data-conv={item.code}>{formatMoney(usd * item.rate, item.code)}</div>
                    <div className="tile__rate">1 USD = <span>{item.rate}</span></div>
                  </>
                )}
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">{error ?? '載入中…'}</p>
        )}
        <p className="attribution">
          {/* Required by the exchange-rate provider's terms of use. */}
          <a href="https://www.exchangerate-api.com" target="_blank" rel="noreferrer">Rates By Exchange Rate API</a>
        </p>
      </div>
    </>
  )
}
