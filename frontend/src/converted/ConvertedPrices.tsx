import { useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'
import type { ConvertedPrices as Converted } from '../api/types'
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
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [refreshMs])

  if (!data) {
    return <p>{error ?? '載入中…'}</p>
  }

  const usd = price?.price ?? data.usdPrice
  return (
    <div>
      <table className="table">
        <caption className="table__caption">1 BTC 以各幣別計價（依目前 BTC-USD 價格 {formatMoney(usd, 'USD')} 換算）</caption>
        <thead>
          <tr>
            <th scope="col">代碼</th>
            <th scope="col">名稱</th>
            <th scope="col" className="num">BTC 價格</th>
            <th scope="col" className="num">匯率（1 USD =）</th>
            <th scope="col">匯率更新時間</th>
          </tr>
        </thead>
        <tbody>
          {data.items.map((item) => (
            <tr key={item.currencyId} data-testid={`converted-${item.code}`}>
              <td>{item.code}</td>
              <td>{item.name}</td>
              {item.rate === null ? (
                <td className="num muted" colSpan={3}>無匯率</td>
              ) : (
                <>
                  <td className="num">{formatMoney(usd * item.rate, item.code)}</td>
                  <td className="num">{item.rate}</td>
                  <td>
                    {item.rateUpdatedAt && <time dateTime={item.rateUpdatedAt}>{formatDateTime(item.rateUpdatedAt)}</time>}
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      <p className="attribution">
        {/* Required by the exchange-rate provider's terms of use. */}
        <a href="https://www.exchangerate-api.com" target="_blank" rel="noreferrer">Rates By Exchange Rate API</a>
      </p>
    </div>
  )
}
