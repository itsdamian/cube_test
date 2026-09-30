// The backend's contract samples (generated from the real controllers and verified by the
// backend's ContractSamplesTest). Mocks use them verbatim, so frontend and backend cannot drift.
import alertEvents from '../../../../contracts/api-samples/alert-events.json'
import alerts from '../../../../contracts/api-samples/alerts.json'
import candles from '../../../../contracts/api-samples/candles.json'
import currencies from '../../../../contracts/api-samples/currencies.json'
import currencyValidationError from '../../../../contracts/api-samples/currency-validation-error.json'
import pricesConverted from '../../../../contracts/api-samples/prices-converted.json'
import pricesHistory from '../../../../contracts/api-samples/prices-history.json'
import pricesLatest from '../../../../contracts/api-samples/prices-latest.json'
import pricesTrend from '../../../../contracts/api-samples/prices-trend.json'
import sseAlert from '../../../../contracts/api-samples/sse-alert.json'
import ssePrice from '../../../../contracts/api-samples/sse-price.json'
import sseStatus from '../../../../contracts/api-samples/sse-status.json'
import type {
  Alert,
  AlertEvent,
  AlertTriggered,
  Candle,
  ConvertedPrices,
  Currency,
  FeedStatus,
  HistoryPage,
  LatestPrice,
  PriceEvent,
  ProblemDetail,
  Trend,
} from '../../api/types'

// Note: `as` is only a cast, not a check (JSON imports widen "LIVE" to string, so `satisfies` cannot be
// used). What keeps the two sides in sync is the backend's ContractSamplesTest (samples == real output)
// plus the frontend tests that assert on the fields they use.
export const samples = {
  alertEvents: alertEvents as AlertEvent[],
  alerts: alerts as Alert[],
  candles: candles as Candle[],
  currencies: currencies as Currency[],
  currencyValidationError: currencyValidationError as ProblemDetail,
  pricesConverted: pricesConverted as ConvertedPrices,
  pricesHistory: pricesHistory as HistoryPage,
  pricesLatest: pricesLatest as LatestPrice,
  pricesTrend: pricesTrend as Trend,
  sseAlert: sseAlert as AlertTriggered,
  ssePrice: ssePrice as PriceEvent,
  sseStatus: sseStatus as FeedStatus,
}
