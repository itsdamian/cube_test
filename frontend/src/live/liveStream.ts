import type { AlertTriggered, FeedState, FeedStatus, PriceEvent } from '../api/types'

/** How the page should present the data. Worst of what the server says and what we observe. */
export type DisplayState = 'connecting' | 'live' | 'delayed' | 'disconnected'

/** If no SSE event of ANY kind arrives for this long, the data is shown as delayed. */
export const STALE_AFTER_MS = 15_000

/**
 * If the server keeps saying LIVE but no PRICE event arrives for this long, the prices are shown
 * as delayed ("價格更新中斷"). Mirrors the backend's price-stale rule (no trade for 60 s = stale),
 * and guards against a stream that delivers status but not prices (task 32).
 */
export const PRICE_STALE_AFTER_MS = 60_000

/** The minimal EventSource surface we use; tests pass a fake with the same shape. */
export interface EventSourceLike {
  readonly readyState: number
  onopen: ((this: EventSource, ev: Event) => unknown) | null
  onerror: ((this: EventSource, ev: Event) => unknown) | null
  addEventListener(type: string, listener: (event: MessageEvent) => void): void
  close(): void
}

export type EventSourceFactory = (url: string) => EventSourceLike

export const browserEventSource: EventSourceFactory = (url) => new EventSource(url) as unknown as EventSourceLike

export interface LiveState {
  /** When the page opened the stream (ms). */
  startedAt: number
  price: PriceEvent | null
  status: FeedStatus | null
  /** Time (ms) of the last SSE event of any kind; null until the first one. */
  lastEventAt: number | null
  /** Time (ms) of the last SSE price event; null until the first one. */
  lastPriceAt: number | null
  /** true while the EventSource is (re)connecting after an error. */
  connectionLost: boolean
  display: DisplayState
  /** The display is delayed because prices stopped while the status still said LIVE. */
  pricesStalled: boolean
}

export type AlertListener = (alert: AlertTriggered) => void

const SEVERITY: Record<DisplayState, number> = { live: 0, connecting: 1, delayed: 2, disconnected: 3 }

function fromServer(state: FeedState): DisplayState {
  return state === 'LIVE' ? 'live' : state === 'STALE' ? 'delayed' : 'disconnected'
}

/** No price event for PRICE_STALE_AFTER_MS (counted from opening the page until the first one). */
export function pricesStalled(state: Omit<LiveState, 'display' | 'pricesStalled'>, now: number): boolean {
  if (state.connectionLost || state.lastEventAt === null) return false
  return now - (state.lastPriceAt ?? state.startedAt) >= PRICE_STALE_AFTER_MS
}

/**
 * Combine the four signals into one display state:
 *  - our own connection to the backend (EventSource error -> disconnected),
 *  - silence: no event of any kind (price, status, alert) for 15 s -> delayed,
 *  - the backend's view of the exchanges (FeedStatus LIVE / STALE / DISCONNECTED),
 *  - no price event for 60 s although other events keep coming -> delayed.
 * Whichever is worst wins, so an old price is never presented as live.
 */
export function computeDisplay(state: Omit<LiveState, 'display' | 'pricesStalled'>, now: number): DisplayState {
  if (state.connectionLost) {
    return 'disconnected'
  }
  if (state.lastEventAt === null) {
    // Nothing received yet: "connecting" for a while, then admit that no data is arriving.
    return now - state.startedAt >= STALE_AFTER_MS ? 'delayed' : 'connecting'
  }
  let display: DisplayState = now - state.lastEventAt >= STALE_AFTER_MS || pricesStalled(state, now) ? 'delayed' : 'live'
  if (state.status) {
    const server = fromServer(state.status.state)
    if (SEVERITY[server] > SEVERITY[display]) {
      display = server
    }
  }
  return display
}
