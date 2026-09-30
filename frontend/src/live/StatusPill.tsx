import { sourceName } from '../format/format'
import type { DisplayState } from './liveStream'
import { useLiveStream } from './liveStreamContext'

const LABELS: Record<DisplayState, string> = {
  connecting: '連線中',
  live: '即時',
  delayed: '資料延遲',
  disconnected: '已斷線',
}

/** Compact feed state for the page header: 即時 / 資料延遲 / 已斷線 + the active exchange. */
export function StatusPill() {
  const { status, display } = useLiveStream()
  return (
    <span className={`status-pill status-pill--${display}`} aria-label={`資料狀態：${LABELS[display]}`}
          data-testid="header-feed-state">
      <span className={`dot dot--${display}`} aria-hidden="true" />
      {LABELS[display]}
      {status && <span className="status-pill__source">· {sourceName(status.activeSource)}</span>}
    </span>
  )
}
