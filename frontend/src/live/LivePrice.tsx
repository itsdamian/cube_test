import { formatTime, formatUsd, sourceName } from '../format/format'
import type { DisplayState } from './liveStream'
import { useLiveStream } from './liveStreamContext'

const LABELS: Record<DisplayState, string> = {
  connecting: '連線中',
  live: '即時',
  delayed: '資料延遲',
  disconnected: '已斷線',
}

/** Big BTC-USD price with its source, the feed state and the time of the last price. */
export function LivePrice() {
  const { price, status, display } = useLiveStream()
  const stale = display !== 'live'

  return (
    <div className={`live-price live-price--${display}`}>
      <div className="live-price__status" role="status" aria-live="polite">
        <span className={`dot dot--${display}`} aria-hidden="true" />
        <span data-testid="feed-state">{LABELS[display]}</span>
        {status && (
          <span className="live-price__source">
            目前來源：<strong data-testid="active-source">{sourceName(status.activeSource)}</strong>
          </span>
        )}
      </div>
      {price ? (
        <>
          <div className="live-price__value" data-testid="btc-price" aria-label="BTC-USD 價格">
            {formatUsd(price.price)}
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
