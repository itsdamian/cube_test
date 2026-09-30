import type {
  Alert,
  AlertEvent,
  Candle,
  CandleInterval,
  ConvertedPrices,
  Currency,
  CurrencyInput,
  Direction,
  HistoryPage,
  LatestPrice,
  ProblemDetail,
  Trend,
} from './types'

/** A non-2xx response; carries the backend's ProblemDetail when there is one. */
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | null

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  /** Validation messages by field name, e.g. { code: "must be 3 uppercase letters..." }. */
  fieldErrors(): Record<string, string> {
    return Object.fromEntries((this.problem?.errors ?? []).map((e) => [e.field, e.message]))
  }
}

/** All requests go to the same origin: nginx (or the Vite dev proxy) forwards /api to the backend. */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}) },
  })
  if (!response.ok) {
    let problem: ProblemDetail | null = null
    try {
      problem = (await response.json()) as ProblemDetail
    } catch {
      // body was not JSON
    }
    throw new ApiError(response.status, problem)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

function query(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

export const api = {
  latestPrice: () => request<LatestPrice>('/api/prices/latest'),
  convertedPrices: () => request<ConvertedPrices>('/api/prices/converted'),
  history: (from?: string, to?: string, limit?: number, cursor?: string) =>
    request<HistoryPage>(`/api/prices/history${query({ from, to, limit, cursor })}`),
  trend: (from?: string, to?: string, points?: number) =>
    request<Trend>(`/api/prices/trend${query({ from, to, points })}`),
  candles: (interval: CandleInterval, from?: string, to?: string) =>
    request<Candle[]>(`/api/candles${query({ interval, from, to })}`),

  currencies: () => request<Currency[]>('/api/currencies'),
  createCurrency: (input: CurrencyInput) =>
    request<Currency>('/api/currencies', { method: 'POST', body: JSON.stringify(input) }),
  updateCurrency: (id: number, input: CurrencyInput) =>
    request<Currency>(`/api/currencies/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  deleteCurrency: (id: number) => request<void>(`/api/currencies/${id}`, { method: 'DELETE' }),

  alerts: () => request<Alert[]>('/api/alerts'),
  createAlert: (direction: Direction, threshold: number) =>
    request<Alert>('/api/alerts', { method: 'POST', body: JSON.stringify({ direction, threshold }) }),
  deleteAlert: (id: number) => request<void>(`/api/alerts/${id}`, { method: 'DELETE' }),
  alertEvents: (unread: boolean) => request<AlertEvent[]>(`/api/alert-events${query({ unread })}`),
  markEventRead: (id: number) => request<void>(`/api/alert-events/${id}/read`, { method: 'POST' }),
  markAllEventsRead: () => request<void>('/api/alert-events/read-all', { method: 'POST' }),
}
