import { http, HttpResponse } from 'msw'
import { samples } from './samples'

/** Default happy-path API: every endpoint answers with its contract sample. Tests override as needed. */
export const handlers = [
  http.get('/api/prices/latest', () => HttpResponse.json(samples.pricesLatest)),
  http.get('/api/prices/converted', () => HttpResponse.json(samples.pricesConverted)),
  http.get('/api/prices/history', () => HttpResponse.json(samples.pricesHistory)),
  http.get('/api/prices/trend', () => HttpResponse.json(samples.pricesTrend)),
  http.get('/api/candles', () => HttpResponse.json(samples.candles)),
  http.get('/api/currencies', () => HttpResponse.json(samples.currencies)),
  http.get('/api/alerts', () => HttpResponse.json(samples.alerts)),
  http.get('/api/alert-events', () => HttpResponse.json(samples.alertEvents)),
]
