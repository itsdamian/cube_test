// TypeScript mirrors of the backend's JSON (see contracts/api-samples/*.json, which are
// generated from the real controllers and checked by the backend's ContractSamplesTest).
// Money values arrive as JSON numbers; ISO-8601 instants arrive as strings.

export type FeedState = 'LIVE' | 'STALE' | 'DISCONNECTED'

export interface FeedStatus {
  activeSource: string
  state: FeedState
  lastTickAt: string | null
  reportedAt: string
}

export interface LatestPrice {
  pair: string
  price: number
  source: string
  eventTime: string
  status: FeedStatus | null
}

export interface PricePoint {
  eventTime: string
  price: number
  source: string
  receivedAt: string
  eventId: string
}

export interface HistoryPage {
  items: PricePoint[]
  nextCursor: string | null
}

export interface TrendPoint {
  bucketStart: string
  eventTime: string
  price: number
}

export interface Trend {
  from: string
  to: string
  bucketSeconds: number
  points: TrendPoint[]
}

export type CandleInterval = '1m' | '5m'

export interface Candle {
  pair: string
  interval: CandleInterval
  openTime: string
  closeTime: string
  open: number
  high: number
  low: number
  close: number
  tickCount: number
}

export interface ConvertedItem {
  currencyId: number
  code: string
  name: string
  price: number | null
  rate: number | null
  rateUpdatedAt: string | null
}

export interface ConvertedPrices {
  pair: string
  usdPrice: number
  priceSource: string
  priceEventTime: string
  rateSource: string
  items: ConvertedItem[]
}

export interface Currency {
  id: number
  code: string
  name: string
  createdAt: string
  updatedAt: string
}

export interface CurrencyInput {
  code: string
  name: string
}

export type Direction = 'ABOVE' | 'BELOW'

export interface Alert {
  id: number
  pair: string
  direction: Direction
  threshold: number
  createdAt: string
  lastTriggeredAt: string | null
}

export interface AlertEvent {
  id: number
  alertId: number
  direction: Direction
  threshold: number
  price: number
  triggeredAt: string
  readAt: string | null
}

/** SSE event "price". */
export interface PriceEvent {
  pair: string
  price: number
  source: string
  eventTime: string
}

/** SSE event "alert". */
export interface AlertTriggered {
  eventId: number
  alertId: number
  pair: string
  direction: Direction
  threshold: number
  price: number
  triggeredAt: string
}

/** RFC 9457 problem details returned by the backend on errors. */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  errors?: { field: string; message: string }[]
}
