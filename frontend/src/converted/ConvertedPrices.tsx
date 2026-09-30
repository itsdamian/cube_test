import { useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'
import type { ConvertedPrices as Converted } from '../api/types'
import { CURRENCIES_CHANGED } from '../currency/events'
import { formatDateTime, formatMoney } from '../format/format'
import { useLiveStream } from '../live/liveStreamContext'

/** Rates change at most daily; the table re-reads them (and the currency list) every minute. */
export const REFRESH_MS = 60_000

/**
 * BTC price in every currency of the database, with the exchange rate used and when the
 * provider last updated it. The rates come from the backend; the price is multiplied locally
 * with the live BTC-USD price from the stream, so the table moves with the ticker.
 */
export function ConvertedPrices({ refreshMs = REFRESH_MS }: { refreshMs?: number }) {
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

  if (!data) {
    return <p>{error ?? '載入中…'}</p>
  }

  const usd = price?.price ?? data.usdPrice
  // The provider updates all rates together; if they ever differ, show the oldest (most cautious).
  const rateTimes = data.items.map((i) => i.rateUpdatedAt).filter((t): t is string => t !== null).sort()
  const oldestRate = rateTimes.length ? rateTimes[0] : null
  return (
    <div>
      <p className="table__note" id="converted-note">
        1 BTC 以各幣別計價（依目前 BTC-USD 價格 {formatMoney(usd, 'USD')} 換算）
      </p>
      <div className="table-scroll">
      <table className="table" aria-describedby="converted-note">
        <thead>
          <tr>
            <th scope="col">代碼</th>
            <th scope="col">名稱</th>
            <th scope="col" className="num">BTC 價格</th>
            <th scope="col" className="num">匯率（1 USD =）</th>
          </tr>
        </thead>
        <tbody>
          {data.items.map((item) => (
            <tr key={item.currencyId} data-testid={`converted-${item.code}`}>
              <td className="nowrap">{item.code}</td>
              <td className="nowrap">{item.name}</td>
              {item.rate === null ? (
                <td className="num muted" colSpan={2}>無匯率</td>
              ) : (
                <>
                  <td className="num">{formatMoney(usd * item.rate, item.code)}</td>
                  <td className="num">{item.rate}</td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      </div>
      {oldestRate && (
        <p className="table__note" data-testid="rates-updated">
          匯率更新於 <time dateTime={oldestRate}>{formatDateTime(oldestRate)}</time>
        </p>
      )}
      <p className="attribution">
        {/* Required by the exchange-rate provider's terms of use. */}
        <a href="https://www.exchangerate-api.com" target="_blank" rel="noreferrer">Rates By Exchange Rate API</a>
      </p>
    </div>
  )
}
