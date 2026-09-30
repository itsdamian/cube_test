import { useState } from 'react'
import { formatTime, formatUsd, sourceName, sourceRole } from '../format/format'
import { useLiveStream } from './liveStreamContext'

type Trend = 'up' | 'down' | null

/** A flash in the same direction at most this often. */
export const FLASH_EVERY_MS = 2_000
/** Any two flashes at least this far apart, so a price bouncing up/down cannot strobe. */
export const FLASH_MIN_GAP_MS = 1_000

const signed = new Intl.NumberFormat('zh-TW', { minimumFractionDigits: 2, maximumFractionDigits: 2, signDisplay: 'exceptZero' })
const signedPercent = new Intl.NumberFormat('zh-TW', { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 2, signDisplay: 'exceptZero' })

/** "+42.37（+0.05%）": change since the first price this page received (computed in the browser). */
function describeChange(first: number, current: number): { text: string, sign: 'up' | 'down' | 'flat' } {
  const diff = current - first
  const sign = diff > 0 ? 'up' : diff < 0 ? 'down' : 'flat'
  return { text: `${signed.format(diff)}（${signedPercent.format(first === 0 ? 0 : diff / first)}）`, sign }
}

/** "US$84,045.50" -> ["US$84,045", ".50"] so the decimals can be shown in a quieter colour. */
function splitDecimals(formatted: string): [string, string] {
  const dot = formatted.lastIndexOf('.')
  return dot < 0 ? [formatted, ''] : [formatted.slice(0, dot), formatted.slice(dot)]
}

/**
 * Hero price (design direction B): the big BTC-USD number never changes colour; the small
 * direction pill next to it shows whether the newest trade is above / below the previous one and
 * glows briefly - at most every FLASH_EVERY_MS per direction and never more often than
 * FLASH_MIN_GAP_MS overall. The meta line shows the change since the page opened, the exchange
 * of that trade and its time. When the data is not live the numbers turn muted and a banner
 * explains why (styling keyed off the page's data-feed attribute).
 */
export function LivePrice() {
  const { price, display } = useLiveStream()
  const stale = display !== 'live'
  // Values derived from earlier renders are kept in state and updated while rendering (React's
  // recommended pattern for this) - no effect, no extra render cascade.
  const [previous, setPrevious] = useState<number | null>(null)
  const [first, setFirst] = useState<number | null>(null)
  const [trend, setTrend] = useState<Trend>(null)
  const [flash, setFlash] = useState({ count: 0, direction: null as Trend, at: 0 })
  if (price && price.price !== previous) {
    if (first === null) setFirst(price.price)
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

  const [integer, decimals] = price ? splitDecimals(formatUsd(price.price)) : ['', '']
  const change = price && first !== null ? describeChange(first, price.price) : null
  const role = price ? sourceRole(price.source) : null

  return (
    <div className={`live-price live-price--${display}`} data-testid="live-price-card">
      <div className="hero__label">
        <span className="coin" aria-hidden="true">₿</span>比特幣 BTC ／ 美元 USD
      </div>
      {price ? (
        <>
          <div className="price">
            <span className="price__value" data-testid="btc-price" aria-label="BTC-USD 價格">
              {integer}<span className="dec">{decimals}</span>
            </span>
            {trend && (
              // key = flash count: a new element replays the one-second glow animation.
              <span key={flash.count} data-testid="price-trend" data-dir={trend}
                    className={`dirpill${flash.direction ? ` flash-${flash.direction}` : ''}`}>
                <span className="glyph" aria-hidden="true">{trend === 'up' ? '▲' : '▼'}</span>
                <span aria-hidden="true">較前一筆</span>
                <span className="visually-hidden">{trend === 'up' ? '較前一筆上漲' : '較前一筆下跌'}</span>
              </span>
            )}
          </div>
          <div className="meta">
            {change && (
              <>
                <span>開啟後 <b className="chg num" data-sign={change.sign} data-testid="price-change">{change.text}</b></span>
                <span className="sep" aria-hidden="true" />
              </>
            )}
            <span>成交來源 <b>{sourceName(price.source)}</b>{role && `（${role}）`}</span>
            <span className="sep" aria-hidden="true" />
            <span>更新於 <time dateTime={price.eventTime}>{formatTime(price.eventTime)}</time></span>
          </div>
          {stale && (
            <p className="stale-note" role="alert">
              {display === 'disconnected'
                ? '已斷線：目前顯示的是最後收到的價格，連線恢復後會自動更新。'
                : '資料延遲：暫時沒有收到新的價格，顯示的可能不是最新價格。'}
            </p>
          )}
        </>
      ) : display === 'connecting' ? (
        // Skeleton while the first price is on its way (shimmer via .app[data-feed=connecting]).
        <>
          <div className="price"><span className="price__value sk" aria-hidden="true">US$00,000<span className="dec">.00</span></span></div>
          <div className="meta"><span className="sk" aria-hidden="true">開啟後 +0.00（+0.00%）· 成交來源 Coinbase</span></div>
          <p className="visually-hidden">尚無價格資料</p>
        </>
      ) : (
        <div className="price"><span className="price__value price__value--empty">尚無價格資料</span></div>
      )}
    </div>
  )
}
