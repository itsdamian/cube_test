import { useState } from 'react'
import { formatTime, formatUsd, sourceName } from '../format/format'
import { useLiveStream } from './liveStreamContext'

type Trend = 'up' | 'down' | null

/** A flash in the same direction at most this often. */
export const FLASH_EVERY_MS = 2_000
/** Any two flashes at least this far apart, so a price bouncing up/down cannot strobe. */
export const FLASH_MIN_GAP_MS = 1_000

/**
 * Big BTC-USD price, the exchange of that trade and the time of the last price. The feed state
 * and the active source are shown once, in the header (StatusPill); this card dims and explains
 * itself when the data is delayed or the connection is lost.
 * Green / red when the newest trade is above / below the previous one (only data we actually
 * have - no invented 24 h change), plus a subtle flash at most every FLASH_EVERY_MS per direction
 * and never more often than FLASH_MIN_GAP_MS overall.
 * The flash is a CSS animation, so prefers-reduced-motion switches it off.
 */
export function LivePrice() {
  const { price, display } = useLiveStream()
  const stale = display !== 'live'
  // "Previous value" kept in state and updated while rendering (React's recommended pattern for
  // values derived from the previous render) - no effect, no extra render cascade.
  const [previous, setPrevious] = useState<number | null>(null)
  const [trend, setTrend] = useState<Trend>(null)
  const [flash, setFlash] = useState({ count: 0, direction: null as Trend, at: 0 })
  if (price && price.price !== previous) {
    if (previous !== null) {
      const direction: Trend = price.price > previous ? 'up' : 'down'
      setTrend(direction)
      // Busy markets tick several times per second: throttle the flash so the page stays calm.
      // Measured in trade time (the price's eventTime), which keeps rendering pure and predictable.
      const now = Date.parse(price.eventTime)
      const sinceLast = now - flash.at
      if (sinceLast >= FLASH_MIN_GAP_MS && (direction !== flash.direction || sinceLast >= FLASH_EVERY_MS)) {
        setFlash({ count: flash.count + 1, direction, at: now })
      }
    }
    setPrevious(price.price)
  }

  return (
    <div className={`live-price live-price--${display}`} data-testid="live-price-card">
      {price ? (
        <>
          <div className="live-price__row">
            <div key={flash.count}
                 className={`live-price__value${trend ? ` live-price__value--${trend}` : ''}${flash.direction ? ` live-price__value--flash-${flash.direction}` : ''}`}
                 data-testid="btc-price" aria-label="BTC-USD 價格">
              {formatUsd(price.price)}
            </div>
            {trend && (
              <span className={`live-price__trend live-price__trend--${trend}`} data-testid="price-trend">
                <span aria-hidden="true">{trend === 'up' ? '▲' : '▼'}</span>
                <span className="visually-hidden">{trend === 'up' ? '較前一筆上漲' : '較前一筆下跌'}</span>
              </span>
            )}
          </div>
          <div className="live-price__meta">
            BTC-USD · 成交來源 {sourceName(price.source)} · 最後更新{' '}
            <time dateTime={price.eventTime}>{formatTime(price.eventTime)}</time>
          </div>
          {stale && (
            <p className="live-price__warning" role="alert">
              {display === 'disconnected'
                ? '已斷線：目前顯示的是最後收到的價格，連線恢復後會自動更新。'
                : '資料延遲：暫時沒有收到新的價格，顯示的可能不是最新價格。'}
            </p>
          )}
        </>
      ) : (
        <div className="live-price__value live-price__value--empty">尚無價格資料</div>
      )}
    </div>
  )
}
