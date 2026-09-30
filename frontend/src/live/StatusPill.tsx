import { sourceName } from '../format/format'
import type { DisplayState } from './liveStream'
import { useLiveStream } from './liveStreamContext'

const LABELS: Record<DisplayState, string> = {
  connecting: '連線中',
  live: '即時',
  delayed: '資料延遲',
  disconnected: '已斷線',
}

/**
 * Compact feed state for the page header: 即時 / 資料延遲 / 已斷線 + the active exchange.
 * Colours come from the page's data-feed attribute (design direction B: live = panel + green
 * text with a pulsing dot; delayed / disconnected = soft warn / down background).
 */
export function StatusPill() {
  const { status, display } = useLiveStream()
  return (
    <span className={`pill status-pill status-pill--${display}`} role="status" aria-live="polite"
          aria-label={`資料狀態：${LABELS[display]}${status ? `，目前來源 ${sourceName(status.activeSource)}` : ''}`}
          data-testid="header-feed-state">
      <span className="dot" aria-hidden="true" />
      {LABELS[display]}
      {status && <span className="pill__src" data-testid="active-source">· {sourceName(status.activeSource)}</span>}
    </span>
  )
}
